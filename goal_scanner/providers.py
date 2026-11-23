"""Read-only data adapters for the four public providers used by Mr2Odds.

Modelling boundaries are intentional: Football-Data results can build goal history;
LiveScore supplies fixtures/live events/statistics; SportyBet prices are for market
comparison only; Transfermarkt squad metrics are context only.
"""
from __future__ import annotations
import csv, io, json, os, re, shutil, subprocess, unicodedata, urllib.error, urllib.parse, urllib.request
from dataclasses import dataclass
from datetime import date, datetime, timezone, timedelta
from difflib import SequenceMatcher
from typing import Any
from zoneinfo import ZoneInfo

USER_AGENT = "Mozilla/5.0 (compatible; GoalScanner/2.0)"
TIMEOUT_SECONDS = 20

class ProviderError(RuntimeError):
    """A provider failed or returned an unusable response."""

def _http_bytes(url: str, timeout: int = TIMEOUT_SECONDS, headers: dict[str, str] | None = None) -> bytes:
    h = {"User-Agent": USER_AGENT, "Accept": "*/*"}
    h.update(headers or {})
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout) as response:
            if response.status != 200:
                raise ProviderError(f"HTTP {response.status} from {url}")
            return response.read()
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise ProviderError(f"Request failed for {url}: {exc}") from exc

def _http_json(url: str, timeout: int = TIMEOUT_SECONDS, headers: dict[str, str] | None = None) -> dict[str, Any]:
    try:
        value = json.loads(_http_bytes(url, timeout, {"Accept": "application/json", **(headers or {})}).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProviderError(f"Invalid JSON from {url}") from exc
    if not isinstance(value, dict):
        raise ProviderError(f"Unexpected JSON shape from {url}")
    return value

def _int(value: Any) -> int | None:
    try: return int(value) if value not in (None, "") else None
    except (TypeError, ValueError): return None

def _float(value: Any) -> float | None:
    try: return float(value) if value not in (None, "") else None
    except (TypeError, ValueError): return None

def team_similarity(left: str, right: str) -> float:
    def norm(value: str) -> str:
        value = unicodedata.normalize("NFKD", str(value or "")).encode("ascii", "ignore").decode().lower()
        value = re.sub(r"\b(fc|afc|cf|sc|club|the)\b", " ", value)
        return " ".join(re.findall(r"[a-z0-9]+", value))
    a, b = norm(left), norm(right)
    if not a or not b: return 0.0
    if a == b: return 1.0
    return SequenceMatcher(None, a, b).ratio()

@dataclass(frozen=True)
class Fixture:
    source: str
    source_id: str
    kickoff: str
    home: str
    away: str
    competition: str = ""
    country: str = ""
    status: str = ""
    home_score: int | None = None
    away_score: int | None = None
    half_home_score: int | None = None
    half_away_score: int | None = None
    retrieved_at: str = ""
    home_team_id: str = ""
    away_team_id: str = ""
    home_badge_path: str = ""
    away_badge_path: str = ""

class LiveScoreProvider:
    """LiveScore public app JSON feed; unofficial and keyless."""
    BASE = "https://prod-public-api.livescore.com/v1/api/app"
    INCIDENT_TYPES = {36:"goal",37:"own_goal",39:"penalty",40:"missed_penalty",43:"yellow_card",44:"second_yellow",45:"red_card"}
    STAT_KEYS = {"corners":"Cos","yellow_cards":"Ycs","red_cards":"Rcs","shots_on_target":"Shon",
                 "shots_off_target":"Shof","blocked_shots":"Shbl","possession":"Pss","fouls":"Fls"}
    def __init__(self, timeout: int = TIMEOUT_SECONDS): self.timeout = timeout
    def _get(self, path: str) -> dict[str, Any]:
        return _http_json(f"{self.BASE}/{path.lstrip('/')}", self.timeout)
    @staticmethod
    def _event(event: dict[str, Any], stage: dict[str, Any], offset: int) -> Fixture | None:
        try:
            home, away = event["T1"][0], event["T2"][0]
            kickoff = datetime.strptime(str(event["Esd"]), "%Y%m%d%H%M%S").replace(tzinfo=timezone(timedelta(hours=offset)))
            if not home.get("Nm") or not away.get("Nm"): return None
        except (KeyError, IndexError, TypeError, ValueError): return None
        return Fixture("livescore", str(event.get("Eid") or ""), kickoff.isoformat(),
            str(home["Nm"]), str(away["Nm"]), str(stage.get("Snm") or ""), str(stage.get("Cnm") or ""),
            str(event.get("Eps") or ""), _int(event.get("Tr1")), _int(event.get("Tr2")),
            _int(event.get("Trh1")), _int(event.get("Trh2")), datetime.now(timezone.utc).isoformat(),
            str(home.get("ID") or ""), str(away.get("ID") or ""),
            str(home.get("Img") or ""), str(away.get("Img") or ""))
    def fetch_day(self, day: date, utc_offset_hours: int = 2) -> list[Fixture]:
        payload = self._get(f"date/soccer/{day:%Y%m%d}/{int(utc_offset_hours)}?MD=1")
        return [fx for stage in payload.get("Stages") or [] for event in stage.get("Events") or []
                if (fx := self._event(event, stage, utc_offset_hours))]
    def fetch_stage(self, country_code: str, stage_code: str, utc_offset_hours: int = 2) -> list[Fixture]:
        path = f"stage/soccer/{urllib.parse.quote(country_code)}/{urllib.parse.quote(stage_code)}/{int(utc_offset_hours)}?MD=1"
        payload = self._get(path)
        return [fx for stage in payload.get("Stages") or [] for event in stage.get("Events") or []
                if (fx := self._event(event, stage, utc_offset_hours))]
    def fetch_incidents(self, event_id: str) -> list[dict[str, Any]]:
        payload = self._get(f"incidents/soccer/{urllib.parse.quote(str(event_id))}")
        result = []
        for items in (payload.get("Incs") or {}).values():
            for item in items or []:
                for incident in [item, *(item.get("Incs") or [])]:
                    kind = self.INCIDENT_TYPES.get(_int(incident.get("IT")))
                    if kind:
                        result.append({"minute":_int(incident.get("Min")),"team":"home" if _int(incident.get("Nm"))==1 else "away",
                            "type":kind,"player":incident.get("Pn") or " ".join(x for x in (incident.get("Fn"),incident.get("Ln")) if x),
                            "score":incident.get("Sc")})
        return sorted(result, key=lambda x: x["minute"] or 0)
    def fetch_statistics(self, event_id: str) -> dict[str, dict[str, int | None]]:
        payload = self._get(f"statistics/soccer/{urllib.parse.quote(str(event_id))}")
        teams = {_int(t.get("Tnb")):t for t in (payload.get("Stat") or []) if isinstance(t,dict)}
        if 1 not in teams or 2 not in teams: return {}
        result = {}
        for side, team in (("home",teams[1]),("away",teams[2])):
            stats = {name:_int(team.get(key)) for name,key in self.STAT_KEYS.items()}
            parts = [stats.get(k) for k in ("shots_on_target","shots_off_target","blocked_shots")]
            stats["shots"] = sum(x or 0 for x in parts) if any(x is not None for x in parts) else None
            result[side] = stats
        return result

class FootballDataProvider:
    """Football-Data.co.uk historical results and current fixture CSV feeds."""
    BASE = "https://www.football-data.co.uk"
    def __init__(self, timeout: int = TIMEOUT_SECONDS): self.timeout = timeout
    EXTRA_LEAGUE_CODES = {"ARG","AUT","BRA","CHN","DNK","FIN","IRL","JPN","MEX","NOR","POL","ROU","RUS","SWE","SWZ","USA"}

    def fetch_season(self, season: str, division: str) -> list[dict[str, Any]]:
        if not re.fullmatch(r"\\d{4}",str(season)) or not re.fullmatch(r"[A-Z0-9]{2,4}",str(division)):
            raise ValueError("Use season code such as 2627 and division code such as E0")
        # Sixteen additional countries are published as one all-seasons CSV per
        # country. Load that archive so teams outside the main European feeds
        # can still build a historical sample before kick-off.
        url = (f"{self.BASE}/new/{division}.csv" if division in self.EXTRA_LEAGUE_CODES
               else f"{self.BASE}/mmz4281/{season}/{division}.csv")
        raw = _http_bytes(url, self.timeout)
        try: text = raw.decode("utf-8-sig")
        except UnicodeDecodeError: text = raw.decode("cp1252")
        rows = []
        for row in csv.DictReader(io.StringIO(text)):
            row_season = str(row.get("Season") or ("all" if division in self.EXTRA_LEAGUE_CODES else season)).strip()
            item = self._normalise_row(row, row_season, division)
            if item:
                rows.append(item)
        return rows

    @staticmethod
    def _normalise_row(row: dict[str,str], season: str, division: str) -> dict[str,Any] | None:
        raw_date = (row.get("Date") or "").strip()
        day = None
        for fmt in ("%d/%m/%Y","%d/%m/%y","%Y-%m-%d"):
            try: day = datetime.strptime(raw_date,fmt).date(); break
            except ValueError: pass
        home, away = (row.get("HomeTeam") or "").strip(), (row.get("AwayTeam") or "").strip()
        if not day or not home or not away: return None
        try: kickoff = datetime.strptime(f"{day.isoformat()} {(row.get('Time') or '12:00').strip()}","%Y-%m-%d %H:%M").replace(tzinfo=ZoneInfo("Europe/London"))
        except ValueError: kickoff = datetime.combine(day,datetime.min.time(),tzinfo=ZoneInfo("Europe/London"))
        fields = ("FTHG","FTAG","HTHG","HTAG","HS","AS","HST","AST","HC","AC","HY","AY","HR","AR","HxG","AxG")
        return {"source":"football-data.co.uk","season":season,"division":division,"date":day.isoformat(),
            "kickoff":kickoff.isoformat(),"home":home,"away":away,"home_goals":_int(row.get("FTHG")),
            "away_goals":_int(row.get("FTAG")),"half_home_goals":_int(row.get("HTHG")),
            "half_away_goals":_int(row.get("HTAG")),"statistics":{k:_float(row.get(k)) for k in fields},
            "referee":(row.get("Referee") or "").strip() or None,"retrieved_at":datetime.now(timezone.utc).isoformat()}
    def fetch_current_fixtures(self) -> list[dict[str,Any]]:
        raw = _http_bytes(f"{self.BASE}/fixtures.csv",self.timeout)
        rows = []
        for row in csv.DictReader(io.StringIO(raw.decode("utf-8-sig",errors="replace"))):
            home,away = (row.get("HomeTeam") or "").strip(),(row.get("AwayTeam") or "").strip()
            if not home or not away: continue
            day = None
            for fmt in ("%d/%m/%Y","%d/%m/%y"):
                try: day=datetime.strptime((row.get("Date") or "").strip(),fmt).date(); break
                except ValueError: pass
            if day:
                rows.append({"source":"football-data.co.uk","date":day.isoformat(),"home":home,"away":away,
                    "division":row.get("Div"),"kickoff_time":row.get("Time") or None,
                    "retrieved_at":datetime.now(timezone.utc).isoformat()})
        return rows
    @staticmethod
    def to_match_records(rows: list[dict[str,Any]]) -> list[Any]:
        from .history import MatchRecord
        records=[]
        for row in rows:
            hg,ag=row.get("home_goals"),row.get("away_goals")
            if hg is not None and ag is not None:
                records.append(MatchRecord(date.fromisoformat(row["date"]).toordinal(),row["home"],row["away"],hg,ag))
        return sorted(records,key=lambda x:x.date_index)

class SportyBetProvider:
    """SportyBet ZA public web-app odds feed. Odds are pricing data, not model inputs."""
    LIST_MARKETS="1,10,18,29,19,20,166,900300,900301,139,900304,900305"
    def __init__(self,country_code: str | None=None,timeout: int=TIMEOUT_SECONDS):
        self.country_code=(country_code or os.getenv("SPORTY_CC","za")).lower()
        if not re.fullmatch(r"[a-z]{2}",self.country_code): raise ValueError("Country code must be two letters")
        self.base=f"https://www.sportybet.com/api/{self.country_code}/factsCenter"; self.timeout=timeout
    def _get(self,url: str) -> dict[str,Any]:
        try: payload=_http_json(url,self.timeout,{"Accept":"application/json","Accept-Language":"en"})
        except ProviderError as first:
            curl=shutil.which("curl")
            if not curl: raise first
            try:
                run=subprocess.run([curl,"-sS","--http2","-m",str(self.timeout),"-A",USER_AGENT,
                    "-H","Accept: application/json","-H","Accept-Language: en","-w","\n%{http_code}",url],
                    capture_output=True,text=True,timeout=self.timeout+5,check=False)
                body,_,status=run.stdout.rpartition("\n")
                if run.returncode or status.strip()!="200": raise ProviderError(f"SportyBet HTTP {status.strip() or run.returncode}")
                payload=json.loads(body)
            except (OSError,subprocess.SubprocessError,json.JSONDecodeError) as exc: raise ProviderError(f"SportyBet fallback failed: {exc}") from exc
        if payload.get("bizCode") not in (None,10000): raise ProviderError(f"SportyBet business error: {payload.get('bizCode')}")
        data=payload.get("data") or {}
        if not isinstance(data,dict): raise ProviderError("Unexpected SportyBet response shape")
        return data
    @staticmethod
    def parse_markets(markets: list[dict[str,Any]] | None) -> dict[str,Any]:
        parsed={}
        names={"18":"match_total_goals","19":"home_team_goals","20":"away_team_goals","166":"match_corners",
            "900300":"home_corners","900301":"away_corners","177":"first_half_corners","139":"match_cards",
            "900304":"home_cards","900305":"away_cards"}
        for market in markets or []:
            mid=str(market.get("id") or "")
            if market.get("status",0) not in (0,None): continue
            outcomes={str(o.get("desc") or ""):_float(o.get("odds")) for o in market.get("outcomes") or []
                if o.get("isActive",1)==1 and _float(o.get("odds")) is not None and _float(o.get("odds"))>1}
            if not outcomes: continue
            found=re.search(r"total=([\d.]+)",str(market.get("specifier") or "")); line=float(found.group(1)) if found else None
            if mid=="1": parsed["1X2"]={"home":outcomes.get("Home"),"draw":outcomes.get("Draw"),"away":outcomes.get("Away")}
            elif mid=="10": parsed["double_chance"]={"1X":outcomes.get("Home or Draw"),"12":outcomes.get("Home or Away"),"X2":outcomes.get("Draw or Away")}
            elif mid=="29": parsed["btts"]={"yes":outcomes.get("Yes"),"no":outcomes.get("No")}
            elif mid in names and line is not None:
                over=next((v for k,v in outcomes.items() if k.startswith("Over")),None)
                under=next((v for k,v in outcomes.items() if k.startswith("Under")),None)
                if over is not None or under is not None: parsed.setdefault(names[mid],{})[line]={"over":over,"under":under}
        return parsed
    def fetch_upcoming(self,hours_ahead: float=48,max_pages: int=40) -> list[dict[str,Any]]:
        if hours_ahead<=0 or max_pages<1: raise ValueError("hours_ahead and max_pages must be positive")
        horizon=datetime.now(timezone.utc).timestamp()+hours_ahead*3600; events=[]
        for page in range(1,max_pages+1):
            query=urllib.parse.urlencode({"sportId":"sr:sport:1","marketId":self.LIST_MARKETS,"pageSize":100,"pageNum":page,"option":1})
            data=self._get(f"{self.base}/pcUpcomingEvents?{query}"); tournaments=data.get("tournaments") or []; count=0
            for tour in tournaments:
                for event in tour.get("events") or []:
                    count+=1; start=_int(event.get("estimateStartTime"))
                    if not start or start/1000>horizon: continue
                    events.append({"source":"sportybet","source_id":str(event.get("eventId") or ""),
                        "kickoff":datetime.fromtimestamp(start/1000,timezone.utc).isoformat(),
                        "country":tour.get("categoryName") or "","competition":tour.get("name") or "",
                        "home":event.get("homeTeamName") or "","away":event.get("awayTeamName") or "",
                        "odds":self.parse_markets(event.get("markets")),"retrieved_at":datetime.now(timezone.utc).isoformat()})
            if count==0: break
        return events
    def fetch_event_markets(self,event_id: str) -> dict[str,Any]:
        query=urllib.parse.urlencode({"eventId":str(event_id),"productId":3})
        return self.parse_markets(self._get(f"{self.base}/event?{query}").get("markets"))

class TransfermarktProvider:
    """Transfermarkt public squad API; context-only data."""
    BASE="https://tmapi-alpha.transfermarkt.technology"
    DIVISION_CODES={"E0":"GB1","E1":"GB2","E2":"GB3","E3":"GB4","EC":"CNAT","SC0":"SC1","SC1":"SC2","SC2":"SC3","SC3":"SC4",
        "D1":"L1","D2":"L2","I1":"IT1","I2":"IT2","SP1":"ES1","SP2":"ES2","F1":"FR1","F2":"FR2","N1":"NL1","B1":"BE1",
        "P1":"PO1","T1":"TR1","G1":"GR1","ARG":"ARG1","AUT":"A1","BRA":"BRA1","CHN":"CSL","DNK":"DK1","FIN":"FI1",
        "IRL":"IR1","JPN":"JAP1","MEX":"MEX1","NOR":"NO1","POL":"PL1","ROU":"RO1","RUS":"RU1","SWE":"SE1","SWZ":"C1","USA":"MLS1"}
    def __init__(self,timeout: int=TIMEOUT_SECONDS): self.timeout=timeout
    def _data(self,url: str) -> Any:
        payload=_http_json(url,self.timeout,{"Accept":"application/json"})
        if not payload.get("success"): raise ProviderError(f"Transfermarkt API did not return success: {url}")
        return payload.get("data")
    @classmethod
    def competition_code(cls,division: str) -> str | None:
        if not division or division.startswith("LS:"): return None
        return cls.DIVISION_CODES.get(division) or cls.DIVISION_CODES.get(division.split(":",1)[0])
    def fetch_competition(self,code: str) -> list[dict[str,Any]]:
        table=self._data(f"{self.BASE}/competition/{urllib.parse.quote(code)}/table")
        if not isinstance(table,dict): return []
        ids=[str(c["clubId"]) for t in table.get("tables") or [] for c in t.get("clubs") or [] if c.get("clubId")]
        clubs=[]
        for start in range(0,len(ids),25):
            query=urllib.parse.urlencode([("ids[]",x) for x in ids[start:start+25]])
            for club in self._data(f"{self.BASE}/clubs?{query}") or []:
                squad=club.get("squadDetails") or {}; value=(squad.get("currentMarketValue") or {}).get("value")
                clubs.append({"id":str(club.get("id") or ""),"name":club.get("name") or "",
                    "short_name":(club.get("baseDetails") or {}).get("shortName") or "",
                    "market_value_eur":_float(value),"average_age":_float(squad.get("averageAge")),
                    "squad_size":_int(squad.get("squadSize")),"crest_url":club.get("crestUrl")})
        return clubs
    def fetch_division_squads(self,division: str) -> list[dict[str,Any]]:
        code=self.competition_code(division)
        return self.fetch_competition(code) if code else []
    @staticmethod
    def match_team(name: str,clubs: list[dict[str,Any]],minimum_similarity: float=.78) -> dict[str,Any] | None:
        scored=[(max(team_similarity(name,c.get("name","")),team_similarity(name,c.get("short_name",""))),c) for c in clubs]
        if not scored: return None
        score,club=max(scored,key=lambda x:x[0])
        return {**club,"match_confidence":round(score,3)} if score>=minimum_similarity else None

def match_fixtures_to_odds(fixtures: list[dict[str,Any]],odds_events: list[dict[str,Any]],
    minimum_similarity: float=.78,kickoff_tolerance_minutes: int=20) -> dict[str,dict[str,Any]]:
    """Match both teams plus kickoff; weak/ambiguous matches stay unmatched."""
    matches={}; tolerance=kickoff_tolerance_minutes*60
    for fixture in fixtures:
        try: target=datetime.fromisoformat(fixture["kickoff"]).timestamp()
        except (KeyError,TypeError,ValueError): continue
        best=None; best_score=0.0
        for event in odds_events:
            try: delta=abs(datetime.fromisoformat(event["kickoff"]).timestamp()-target)
            except (KeyError,TypeError,ValueError): continue
            if delta>tolerance: continue
            score=min(team_similarity(fixture.get("home",""),event.get("home","")),
                      team_similarity(fixture.get("away",""),event.get("away","")))
            if score>best_score: best,best_score=event,score
        if best is not None and best_score>=minimum_similarity:
            key=str(fixture.get("id") or fixture.get("source_id") or fixture.get("home","")+"|"+fixture.get("away",""))
            matches[key]={**best,"match_confidence":round(best_score,3)}
    return matches

def provider_manifest() -> dict[str,dict[str,str]]:
    return {
      "livescore":{"role":"fixtures, live scores/status, incidents and available match statistics","base_url":LiveScoreProvider.BASE,
        "model_use":"fixture/live context; historical stats only after coverage validation","authentication":"public unofficial app JSON; no key"},
      "football_data":{"role":"historical results and available xG, shots, corners, cards, half-time and referee fields",
        "base_url":FootballDataProvider.BASE,"model_use":"historical goal results/form; optional stats only when present",
        "authentication":"public CSV; no key"},
      "sportybet":{"role":"current bookmaker odds and market lines","base_url":"https://www.sportybet.com/api/{country}/factsCenter",
        "model_use":"odds/value comparison only; never probability model inputs","authentication":"public web-app JSON; unofficial/WAF-sensitive"},
      "transfermarkt":{"role":"club squad size, average age and estimated market value","base_url":TransfermarktProvider.BASE,
        "model_use":"context only; excluded from probabilities pending validation","authentication":"public unofficial API; no key"},
    }
