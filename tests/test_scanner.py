from datetime import date, timedelta
from goal_scanner.scanner import estimate_fixture_lambdas, build_fixture_candidates

def history_rows(with_stats=False):
    rows=[]; start=date(2026,10,1)
    for i,opponent in enumerate(("North","South","East","West")):
        day=(start+timedelta(days=i*2)).isoformat()
        stats={"HC":6,"AC":3,"HY":2,"AY":1,"HR":0,"AR":0} if with_stats else {}
        rows.append({"date":day,"home":"Home FC","away":opponent,"home_goals":2,"away_goals":1,"statistics":stats})
        stats2={"HC":4,"AC":7,"HY":1,"AY":2,"HR":0,"AR":0} if with_stats else {}
        rows.append({"date":day,"home":opponent,"away":"Away FC","home_goals":1,"away_goals":2,"statistics":stats2})
    for i in range(4):
        day=(start+timedelta(days=1+i*2)).isoformat()
        stats={"HC":5,"AC":4,"HY":1,"AY":1,"HR":0,"AR":0} if with_stats else {}
        rows.append({"date":day,"home":f"Club {i}","away":f"Other {i}","home_goals":1,"away_goals":1,"statistics":stats})
    return rows

def test_fixture_lambdas_use_only_prior_venue_matches():
    rows=history_rows()
    rows.append({"date":"2026-11-23","home":"Home FC","away":"Away FC","home_goals":6,"away_goals":0})
    result=estimate_fixture_lambdas(rows,"Home FC","Away FC","2026-11-23T18:00:00+02:00")
    assert result["status"]=="ok"
    assert result["home_home_matches"]==4 and result["away_away_matches"]==4
    assert result["historical_matches"]==12 and result["history_cutoff"]=="2026-11-23"

def test_fixture_model_blocks_small_venue_samples():
    result=estimate_fixture_lambdas(history_rows()[:5],"Home FC","Away FC","2026-11-23T18:00:00+02:00")
    assert result["status"]=="insufficient_data"

def test_corner_and_booking_estimates_require_real_historical_stats():
    result=estimate_fixture_lambdas(history_rows(True),"Home FC","Away FC","2026-11-23T18:00:00+02:00")
    assert result["status"]=="ok"
    assert "corners" in result["count_expectations"]
    assert "bookings" in result["count_expectations"]
    missing=estimate_fixture_lambdas(history_rows(False),"Home FC","Away FC","2026-11-23T18:00:00+02:00")
    assert missing["count_expectations"]=={}

def test_candidates_are_hidden_when_sportybet_has_no_price():
    fixture={"id":"fx-1","source_id":"fx-1","kickoff":"2026-11-23T18:00:00+02:00","home":"Home FC","away":"Away FC","competition":"Test League"}
    result=build_fixture_candidates(fixture,history_rows(True),None,
        {"team_corners_over","match_corners_over","match_bookings_over","team_bookings_over",
         "match_over_2_5","btts_yes","match_winner_home","match_winner_draw","match_winner_away"},.0)
    assert result["status"]=="ok"
    assert result["candidates"]==[]


def test_candidates_require_sportybet_price_for_exact_market_and_line():
    fixture={"id":"fx-1","source_id":"fx-1","kickoff":"2026-11-23T18:00:00+02:00","home":"Home FC","away":"Away FC","competition":"Test League"}
    odds={"1X2":{"home":1.9,"draw":3.2,"away":4.1},
          "match_total_goals":{"2.5":{"over":1.8}},
          "btts":{"yes":1.9},
          "home_corners":{"4.5":{"over":1.9}},
          "away_corners":{"4.5":{"over":1.9}},
          "match_corners":{"8.5":{"over":1.9}},
          "home_cards":{"1.5":{"over":1.9}},
          "away_cards":{"1.5":{"over":1.9}},
          "match_cards":{"3.5":{"over":1.9}}}
    event={"odds":odds}
    result=build_fixture_candidates(fixture,history_rows(True),event,
        {"team_corners_over","match_corners_over","match_bookings_over","team_bookings_over",
         "match_over_2_5","btts_yes","match_winner_home","match_winner_draw","match_winner_away"},.0)
    assert result["status"]=="ok" and result["candidates"]
    assert all(item["odds_status"]=="available" and item["odds"]>1 for item in result["candidates"])
    assert all(item["calibration"]=="uncalibrated_baseline" for item in result["candidates"])
    assert {x["market"] for x in result["candidates"]} <= {
        "team_corners_over","match_corners_over","match_bookings_over","team_bookings_over",
        "match_over_2_5","btts_yes","match_winner"}
