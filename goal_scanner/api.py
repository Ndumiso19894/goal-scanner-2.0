"""Optional API: install with pip install -e '.[api]'."""
try:
    from datetime import date
    from typing import Any
    from fastapi import FastAPI, HTTPException, Query
    from pydantic import BaseModel, Field
except ImportError as exc:
    raise RuntimeError("Install optional dependencies: pip install -e '.[api]'") from exc

from .models import goal_market_probabilities
from .ingestion import collect_sources
from .providers import provider_manifest, ProviderError
from .lineups import Appearance, PlayerProfile, predict_starting_xi
from .api_football import ApiFootballProvider

app = FastAPI(title="Goal Scanner 2.0", version="2.0.0")

class MatchInput(BaseModel):
    home_lambda: float = Field(gt=0, le=6)
    away_lambda: float = Field(gt=0, le=6)

class SourceCollectionInput(BaseModel):
    match_date: date = Field(default_factory=date.today)
    season: str = Field(pattern=r"^\d{4}$", examples=["2627"])
    division: str = Field(min_length=2, max_length=4, examples=["E0"])
    hours_ahead: float = Field(default=48, gt=0, le=168)
    include_squads: bool = True

class LineupPredictionInput(BaseModel):
    as_of: date
    players: list[dict[str, Any]]
    recent_formations: list[str] = Field(default_factory=list)
    minimum_players: int = Field(default=14, ge=11, le=40)
    minimum_appearance_samples: int = Field(default=3, ge=1, le=20)

@app.get("/health")
def health():
    return {"status": "ok", "version": "2.0.0"}

@app.post("/probabilities")
def probabilities(data: MatchInput):
    try:
        return {key.value: value for key, value in goal_market_probabilities(data.home_lambda, data.away_lambda).items()}
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

@app.get("/sources/manifest")
def sources_manifest():
    return provider_manifest()

@app.post("/sources/collect")
def sources_collect(data: SourceCollectionInput):
    """Collect the four configured feeds; provider failures are reported per source."""
    try:
        return collect_sources(data.match_date, data.season, data.division, data.hours_ahead, data.include_squads)
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

@app.get("/lineups/coverage")
def lineups_coverage(season: int = Query(..., ge=2000, le=2100)):
    """List competitions and season-specific player/lineup/injury coverage."""
    try:
        return {"season":season,"competitions":ApiFootballProvider().fetch_league_coverage(season)}
    except ProviderError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc

@app.get("/lineups/predict/team")
def lineups_predict_team(team_id: int = Query(..., gt=0),
                         league_id: int = Query(..., gt=0),
                         season: int = Query(..., ge=2000, le=2100),
                         as_of: date | None = Query(None),
                         fixture_id: int | None = Query(None, gt=0),
                         last_matches: int = Query(10, ge=3, le=20)):
    """Build a pre-match XI from recent appearances; optionally apply fixture injury news."""
    try:
        provider = ApiFootballProvider()
        history = provider.fetch_team_recent_appearances(
            team_id=team_id, league_id=league_id, season=season, last_matches=last_matches)
        injury_status = {}
        if fixture_id is not None:
            for item in provider.fetch_fixture_injuries(fixture_id):
                player = item.get("player") or {}
                team = item.get("team") or {}
                if team.get("id") != team_id or player.get("id") is None:
                    continue
                kind = str(player.get("type") or "").lower()
                reason = str(player.get("reason") or "").lower()
                if "question" in kind or "doubt" in reason:
                    injury_status[str(player["id"])] = "questionable"
                elif "suspend" in kind or "suspend" in reason or "missing fixture" in kind:
                    injury_status[str(player["id"])] = "missing"
                else:
                    injury_status[str(player["id"])] = "questionable"
        players = []
        for item in history["players"]:
            appearances = [Appearance(
                match_date=date.fromisoformat(row["match_date"]),
                started=bool(row["started"]), minutes=int(row["minutes"]),
                position=row.get("position") or item.get("position"))
                for row in item["appearances"]]
            players.append(PlayerProfile(
                player_id=item["player_id"], name=item["name"], position=item["position"],
                appearances=appearances,
                availability=injury_status.get(item["player_id"], "unknown")))
        prediction = predict_starting_xi(players, as_of=as_of or date.today())
        return {"team_id":team_id,"league_id":league_id,"season":season,
                "fixture_id":fixture_id,"source":"api-football","appearance_fixtures_checked":history["fixtures_checked"],
                "injury_data_requested":fixture_id is not None,"prediction":prediction}
    except ProviderError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except (KeyError, TypeError, ValueError) as exc:
        raise HTTPException(status_code=422, detail=f"Unable to build lineup from provider data: {exc}") from exc

@app.post("/lineups/predict")
def lineups_predict(data: LineupPredictionInput):
    """Predict from normalized recent player appearances; no official XI is required."""
    try:
        players = []
        for item in data.players:
            appearances = [
                Appearance(
                    match_date=date.fromisoformat(str(row["match_date"])[:10]),
                    started=bool(row.get("started", False)),
                    minutes=int(row.get("minutes") or 0),
                    position=row.get("position") or item.get("position"),
                )
                for row in item.get("appearances", [])
            ]
            players.append(PlayerProfile(
                player_id=str(item["player_id"]),
                name=str(item["name"]),
                position=str(item["position"]),
                appearances=appearances,
                availability=str(item.get("availability") or "unknown").lower(),
            ))
        return predict_starting_xi(players, as_of=data.as_of,
            recent_formations=data.recent_formations,
            minimum_players=data.minimum_players,
            minimum_appearance_samples=data.minimum_appearance_samples)
    except (KeyError, TypeError, ValueError) as exc:
        raise HTTPException(status_code=422, detail=f"Invalid player appearance data: {exc}") from exc
