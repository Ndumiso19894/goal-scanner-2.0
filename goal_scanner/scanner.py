"""Fixture-level estimates for the nine approved Goal Scanner markets."""
from __future__ import annotations
from datetime import date, datetime
from typing import Any
from .models import goal_market_probabilities, count_market_probability
from .providers import team_similarity

MODEL_VERSION="venue-poisson-goals-corners-cards-v2"
MIN_VENUE_MATCHES=4
MAX_TEAM_MATCHES=12
DEFAULT_LINES={"team_corners_over":4.5,"match_corners_over":8.5,"match_bookings_over":3.5,"team_bookings_over":1.5}

def _valid_rows(rows,before):
    valid=[]
    for row in rows:
        try:
            played=date.fromisoformat(str(row["date"])); hg,ag=row.get("home_goals"),row.get("away_goals")
            if played>=before or hg is None or ag is None: continue
            hg,ag=int(hg),int(ag)
            if hg<0 or ag<0: continue
            valid.append({**row,"home_goals":hg,"away_goals":ag,"_date":played})
        except (KeyError,TypeError,ValueError): continue
    return sorted(valid,key=lambda r:r["_date"])

def _team_rows(rows,team,venue):
    key="home" if venue=="home" else "away"
    return [r for r in rows if team_similarity(str(r.get(key,"")),team)>=.88]

def _shrunk_average(values,prior,prior_matches=3.0):
    return (sum(values)+prior*prior_matches)/(len(values)+prior_matches)

def _stat(row,key):
    try:
        v=float((row.get("statistics") or {}).get(key))
        return v if v>=0 else None
    except (TypeError,ValueError,AttributeError): return None

def _count_expectation(rows,home_rows,away_rows,hkey,akey):
    league=[(_stat(r,hkey),_stat(r,akey)) for r in rows]
    league=[(h,a) for h,a in league if h is not None and a is not None]
    hv=[_stat(r,hkey) for r in home_rows]; av=[_stat(r,akey) for r in away_rows]
    hv=[x for x in hv if x is not None]; av=[x for x in av if x is not None]
    if len(league)<12 or len(hv)<MIN_VENUE_MATCHES or len(av)<MIN_VENUE_MATCHES: return None
    lh=sum(x[0] for x in league)/len(league); la=sum(x[1] for x in league)/len(league)
    return {"home_lambda":max(.05,_shrunk_average(hv,lh)),
            "away_lambda":max(.05,_shrunk_average(av,la)),
            "league_matches":len(league),"home_sample":len(hv),"away_sample":len(av)}

def estimate_fixture_lambdas(historical_rows,home,away,kickoff,min_venue_matches=MIN_VENUE_MATCHES):
    try: day=datetime.fromisoformat(kickoff.replace("Z","+00:00")).date()
    except (TypeError,ValueError): return {"status":"insufficient_data","reason":"Fixture kickoff is invalid"}
    rows=_valid_rows(historical_rows,day)
    if len(rows)<12: return {"status":"insufficient_data","reason":"Fewer than 12 prior completed league matches"}
    lh=sum(r["home_goals"] for r in rows)/len(rows); la=sum(r["away_goals"] for r in rows)/len(rows)
    if lh<=0 or la<=0: return {"status":"insufficient_data","reason":"League goal baseline is not usable"}
    hr=_team_rows(rows,home,"home")[-MAX_TEAM_MATCHES:]; ar=_team_rows(rows,away,"away")[-MAX_TEAM_MATCHES:]
    if len(hr)<min_venue_matches or len(ar)<min_venue_matches:
        return {"status":"insufficient_data","reason":f"At least {min_venue_matches} prior home and away matches are required",
                "home_home_matches":len(hr),"away_away_matches":len(ar)}
    hsc=[r["home_goals"] for r in hr]; hcon=[r["away_goals"] for r in hr]
    asc=[r["away_goals"] for r in ar]; acon=[r["home_goals"] for r in ar]
    hl=min(6.,max(.05,(_shrunk_average(hsc,lh)/lh)*(_shrunk_average(acon,lh)/lh)*lh))
    al=min(6.,max(.05,(_shrunk_average(asc,la)/la)*(_shrunk_average(hcon,la)/la)*la))
    # Bookings count yellow plus red cards; missing card fields remain missing, never zero.
    booking_rows=[]
    for r in rows:
        hy,ay,redh,reda=(_stat(r,k) for k in ("HY","AY","HR","AR"))
        if None not in (hy,ay,redh,reda):
            booking_rows.append({**r,"statistics":{**(r.get("statistics") or {}),"BH":hy+redh,"BA":ay+reda}})
    bhr=_team_rows(booking_rows,home,"home")[-MAX_TEAM_MATCHES:]
    bar=_team_rows(booking_rows,away,"away")[-MAX_TEAM_MATCHES:]
    counts={}
    corners=_count_expectation(rows,hr,ar,"HC","AC")
    bookings=_count_expectation(booking_rows,bhr,bar,"BH","BA")
    if corners: counts["corners"]=corners
    if bookings: counts["bookings"]=bookings
    return {"status":"ok","home_lambda":round(hl,4),"away_lambda":round(al,4),
            "league_home_goals":round(lh,4),"league_away_goals":round(la,4),
            "home_home_matches":len(hr),"away_away_matches":len(ar),"historical_matches":len(rows),
            "count_expectations":counts,
            "data_quality":round(.75*min(1.,min(len(hr),len(ar))/8.)+.25*min(1.,len(rows)/80.),3),
            "history_cutoff":day.isoformat(),"model_version":MODEL_VERSION,"calibration":"uncalibrated_baseline"}

def _line(odds,group,default):
    try:
        keys=[float(k) for k in (odds.get(group) or {})]
        return min(keys,key=lambda x:abs(x-default)) if keys else default
    except (TypeError,ValueError): return default

def _price(odds,group,line):
    try:
        entry=(odds.get(group) or {}).get(line) or (odds.get(group) or {}).get(str(line)) or {}
        value=float(entry.get("over"))
        return value if value>1 else None
    except (TypeError,ValueError,AttributeError): return None

def market_selections(probabilities,odds,count_expectations=None):
    counts=count_expectations or {}; out=[]
    def add(market,selection,p,line=None,price=None):
        try: price=float(price); price=price if price>1 else None
        except (TypeError,ValueError): price=None
        ev=p*price-1 if price else None
        out.append({"market":market,"selection":selection,"estimated_probability":round(p,6),"line":line,
                    "odds":round(price,4) if price else None,"expected_value":round(ev,6) if ev is not None else None,
                    "odds_status":"available" if price else "unavailable"})
    for side in ("home","draw","away"):
        add("match_winner",side,probabilities[{"home":"home_win","draw":"draw","away":"away_win"}[side]],
            price=(odds.get("1X2") or {}).get(side))
    add("match_over_2_5","over",probabilities["match_over_2_5"],2.5,_price(odds,"match_total_goals",2.5))
    add("btts_yes","yes",probabilities["btts_yes"],price=(odds.get("btts") or {}).get("yes"))
    specs=[("corners","team_corners_over","home_corners","away_corners"),
           ("corners","match_corners_over","match_corners",None),
           ("bookings","team_bookings_over","home_cards","away_cards"),
           ("bookings","match_bookings_over","match_cards",None)]
    for stat,market,group,away_group in specs:
        e=counts.get(stat)
        if not e: continue
        line=_line(odds,group,DEFAULT_LINES[market])
        if away_group:
            for side,odds_group in (("home",group),("away",away_group)):
                add(market,side,count_market_probability(e[side+"_lambda"],line),line,_price(odds,odds_group,line))
        else:
            lam=e["home_lambda"]+e["away_lambda"]
            add(market,"over",count_market_probability(lam,line),line,_price(odds,group,line))
    return out

def build_fixture_candidates(fixture,historical_rows,odds_event,selected_markets,min_probability=.60,min_odds=None,max_odds=None):
    home,away=str(fixture.get("home") or ""),str(fixture.get("away") or "")
    kickoff=str(fixture.get("kickoff") or "")
    estimate=estimate_fixture_lambdas(historical_rows,home,away,kickoff)
    if estimate["status"]!="ok": return {"status":"insufficient_data","fixture":fixture,"reason":estimate["reason"]}
    probabilities={k.value:v for k,v in goal_market_probabilities(estimate["home_lambda"],estimate["away_lambda"]).items()}
    prices=(odds_event or {}).get("odds") or {}; candidates=[]
    for item in market_selections(probabilities,prices,estimate.get("count_expectations")):
        if item["market"] not in selected_markets or item["estimated_probability"]<min_probability: continue
        price=item["odds"]
        if min_odds is not None and (price is None or price<min_odds): continue
        if max_odds is not None and (price is None or price>max_odds): continue
        ev=item["expected_value"]; value=min(1.,max(0.,(ev+.10)/.30)) if ev is not None else .5
        rank=.55*item["estimated_probability"]+.30*estimate["data_quality"]+.15*value
        candidates.append({**item,"rank_score":round(rank,6),
            "fixture_id":str(fixture.get("id") or fixture.get("source_id") or ""),"kickoff":kickoff,
            "home":home,"away":away,"competition":fixture.get("competition") or "",
            "home_lambda":estimate["home_lambda"],"away_lambda":estimate["away_lambda"],
            "market_probabilities":probabilities,"market_odds":prices,
            "count_expectations":estimate.get("count_expectations",{}),"data_quality":estimate["data_quality"],
            "sample":{"home_home_matches":estimate["home_home_matches"],"away_away_matches":estimate["away_away_matches"],
                      "historical_matches":estimate["historical_matches"]},
            "history_cutoff":estimate["history_cutoff"],"model_version":MODEL_VERSION,
            "calibration":"uncalibrated_baseline",
            "reasons":[f"Historical home/away sample: {estimate['home_home_matches']}/{estimate['away_away_matches']} matches",
                       "Count-market estimates are shown only when historical corner/card samples are available.",
                       "Bookmaker prices are comparisons only and do not determine model probabilities."]})
    return {"status":"ok","fixture":fixture,"estimates":estimate,"probabilities":probabilities,"candidates":candidates}

def is_finished_status(status):
    return str(status or "").strip().upper() in {"FT","AET","PEN","FINISHED","FULL TIME"}
