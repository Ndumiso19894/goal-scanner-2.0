"""Optional API: install with pip install -e '.[api]'."""
try:
    from datetime import date
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel, Field
except ImportError as exc:
    raise RuntimeError("Install optional dependencies: pip install -e '.[api]'") from exc

from .models import goal_market_probabilities
from .ingestion import collect_sources
from .providers import provider_manifest

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
