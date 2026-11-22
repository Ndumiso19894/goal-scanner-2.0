"""Optional API: install with pip install -e '.[api]'."""
try:
    from datetime import date
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel, Field, StrictBool, StrictInt
except ImportError as exc:
    raise RuntimeError("Install optional dependencies: pip install -e '.[api]'") from exc

from .models import goal_market_probabilities
from .ingestion import collect_sources
from .providers import provider_manifest
from .lineups import Appearance, PlayerProfile, predict_starting_xi

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


class AppearanceInput(BaseModel):
    match_date: date
    started: StrictBool
    minutes: StrictInt = 0
    position: str | None = None


class PlayerInput(BaseModel):
    player_id: str = Field(min_length=1)
    name: str = Field(min_length=1)
    position: str = Field(min_length=1)
    appearances: list[AppearanceInput] = Field(default_factory=list)
    availability: str = "unknown"


class LineupPredictionInput(BaseModel):
    as_of: date
    players: list[PlayerInput]
    recent_formations: list[str] = Field(default_factory=list)
    minimum_players: int = Field(default=14, ge=11, le=40)
    minimum_appearance_samples: int = Field(default=3, ge=1, le=20)


@app.get("/health")
def health():
    return {"status": "ok", "version": "2.0.0"}


@app.post("/probabilities")
def probabilities(data: MatchInput):
    try:
        return {
            key.value: value
            for key, value in goal_market_probabilities(data.home_lambda, data.away_lambda).items()
        }
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/sources/manifest")
def sources_manifest():
    return provider_manifest()


@app.post("/sources/collect")
def sources_collect(data: SourceCollectionInput):
    """Collect the configured feeds; provider failures are reported per source."""
    try:
        return collect_sources(
            data.match_date, data.season, data.division, data.hours_ahead, data.include_squads
        )
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/lineups/predict")
def lineups_predict(data: LineupPredictionInput):
    """Predict from normalized recent appearances; official team sheets are not required."""
    try:
        players = [
            PlayerProfile(
                player_id=item.player_id,
                name=item.name,
                position=item.position,
                appearances=[
                    Appearance(
                        match_date=row.match_date,
                        started=row.started,
                        minutes=row.minutes,
                        position=row.position or item.position,
                    )
                    for row in item.appearances
                ],
                availability=item.availability.lower(),
            )
            for item in data.players
        ]
        return predict_starting_xi(
            players,
            as_of=data.as_of,
            recent_formations=data.recent_formations,
            minimum_players=data.minimum_players,
            minimum_appearance_samples=data.minimum_appearance_samples,
        )
    except (TypeError, ValueError) as exc:
        raise HTTPException(status_code=422, detail=f"Invalid player appearance data: {exc}") from exc
