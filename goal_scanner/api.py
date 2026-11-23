"""FastAPI service for goal markets, fixture scanning and prediction settlement."""
from __future__ import annotations

import hmac
import os
from datetime import date, datetime, time, timedelta, timezone
from typing import Literal
from zoneinfo import ZoneInfo

try:
    from fastapi import FastAPI, HTTPException, Request
    from fastapi.responses import JSONResponse
    from pydantic import BaseModel, Field
except ImportError as exc:
    raise RuntimeError("Install optional dependencies: pip install -e '.[api]'") from exc

from .ingestion import collect_sources
from .markets import Market
from .models import goal_market_probabilities
from .providers import (
    FootballDataProvider, LiveScoreProvider, ProviderError, SportyBetProvider,
    match_fixtures_to_odds, provider_manifest,
)
from .scanner import build_fixture_candidates, is_finished_status
from .storage import Store

app = FastAPI(title="Goal Scanner Pro API", version="2.1.0")

@app.middleware("http")
async def api_token_guard(request: Request, call_next):
    """Protect mobile/API routes when a deployment token is configured."""
    expected = os.getenv("GOAL_SCANNER_API_TOKEN", "").strip()
    if expected and request.url.path != "/health":
        supplied = request.headers.get("Authorization", "")
        if not hmac.compare_digest(supplied, f"Bearer {expected}"):
            return JSONResponse(status_code=401, content={"detail": "A valid Bearer token is required"})
    return await call_next(request)

STORE = Store(os.getenv("GOAL_SCANNER_DB", "data/goal_scanner.sqlite3"))
LOCAL_TZ = ZoneInfo("Africa/Johannesburg")
SELECTABLE_MARKETS = {
    "team_corners_over", "match_corners_over", "match_bookings_over",
    "team_bookings_over", "match_over_2_5", "match_winner_home", "match_winner_draw", "match_winner_away", "btts_yes",
}


class MatchInput(BaseModel):
    home_lambda: float = Field(gt=0, le=6)
    away_lambda: float = Field(gt=0, le=6)


class SourceCollectionInput(BaseModel):
    match_date: date = Field(default_factory=date.today)
    season: str = Field(pattern=r"^\d{4}$", examples=["2627"])
    division: str = Field(min_length=2, max_length=4, examples=["E0"])
    hours_ahead: float = Field(default=48, gt=0, le=168)
    include_squads: bool = True


class ScannerRunInput(BaseModel):
    match_date: date = Field(default_factory=lambda: datetime.now(LOCAL_TZ).date())
    season: str = Field(pattern=r"^\d{4}$", examples=["2627"])
    division: str = Field(min_length=2, max_length=4, examples=["E0"])
    hours_ahead: float = Field(default=48, gt=0, le=168)
    minimum_probability: float = Field(default=0.60, ge=0.50, le=0.95)
    markets: list[str] = Field(default_factory=lambda: sorted(SELECTABLE_MARKETS))
    mode: Literal["recommended", "full"] = "recommended"
    min_odds: float | None = Field(default=None, gt=1)
    max_odds: float | None = Field(default=None, gt=1)
    competition_query: str | None = Field(default=None, max_length=80)
    team_query: str | None = Field(default=None, max_length=80)


def _window(data: ScannerRunInput) -> tuple[datetime, datetime, list[date]]:
    now = datetime.now(timezone.utc)
    local_today = now.astimezone(LOCAL_TZ).date()
    start = now if data.match_date <= local_today else datetime.combine(
        data.match_date, time.min, tzinfo=LOCAL_TZ
    ).astimezone(timezone.utc)
    end = start + timedelta(hours=data.hours_ahead)
    days = []
    cursor = start.astimezone(LOCAL_TZ).date()
    last = end.astimezone(LOCAL_TZ).date()
    while cursor <= last:
        days.append(cursor)
        cursor += timedelta(days=1)
    return start, end, days


def _fixture_dict(item) -> dict:
    return {
        "source": item.source, "source_id": item.source_id, "id": item.source_id,
        "kickoff": item.kickoff, "home": item.home, "away": item.away,
        "competition": item.competition, "country": item.country, "status": item.status,
        "home_score": item.home_score, "away_score": item.away_score,
        "retrieved_at": item.retrieved_at,
    }


@app.get("/health")
def health():
    return {"status": "ok", "version": app.version, "database": "sqlite", "auth_required": bool(os.getenv("GOAL_SCANNER_API_TOKEN", "").strip())}


@app.post("/probabilities")
def probabilities(data: MatchInput):
    try:
        return {key.value: value for key, value in goal_market_probabilities(
            data.home_lambda, data.away_lambda).items()}
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/sources/manifest")
def sources_manifest():
    return provider_manifest()


@app.post("/sources/collect")
def sources_collect(data: SourceCollectionInput):
    """Collect configured feeds; failures remain visible per provider."""
    try:
        return collect_sources(data.match_date, data.season, data.division,
                               data.hours_ahead, data.include_squads)
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/scanner/run")
def scanner_run(data: ScannerRunInput):
    """Collect fixtures/history/odds, estimate markets, and save immutable picks."""
    unknown = set(data.markets) - SELECTABLE_MARKETS
    if unknown:
        raise HTTPException(status_code=422, detail=f"Unsupported scanner markets: {', '.join(sorted(unknown))}")
    if data.min_odds is not None and data.max_odds is not None and data.min_odds > data.max_odds:
        raise HTTPException(status_code=422, detail="Minimum odds cannot exceed maximum odds")

    start, end, days = _window(data)
    source_status: dict[str, dict] = {}
    fixtures = []
    livescore_errors = []
    provider = LiveScoreProvider()
    for day in days:
        try:
            fixtures.extend(_fixture_dict(item) for item in provider.fetch_day(day, utc_offset_hours=2))
        except ProviderError as exc:
            livescore_errors.append(str(exc))
    if livescore_errors:
        source_status["livescore"] = {
            "status": "partial" if fixtures else "error",
            "records": len(fixtures), "errors": livescore_errors,
        }
    else:
        source_status["livescore"] = {"status": "ok", "records": len(fixtures)}

    unique = {}
    for fixture in fixtures:
        if fixture["id"]:
            unique[fixture["id"]] = fixture
    upcoming = []
    for fixture in unique.values():
        try:
            kickoff = datetime.fromisoformat(fixture["kickoff"].replace("Z", "+00:00"))
            if start <= kickoff <= end and kickoff >= datetime.now(timezone.utc):
                upcoming.append(fixture)
        except (TypeError, ValueError):
            continue
    if data.competition_query:
        needle = data.competition_query.casefold().strip()
        upcoming = [row for row in upcoming if needle in str(row.get("competition") or "").casefold()]
    if data.team_query:
        needle = data.team_query.casefold().strip()
        upcoming = [row for row in upcoming if needle in
                    (str(row.get("home") or "") + " " + str(row.get("away") or "")).casefold()]
    upcoming.sort(key=lambda row: row["kickoff"])

    try:
        history = FootballDataProvider().fetch_season(data.season, data.division)
        completed = [row for row in history if row.get("home_goals") is not None and row.get("away_goals") is not None]
        source_status["football_data"] = {"status": "ok", "records": len(history), "completed_matches": len(completed)}
    except (ProviderError, ValueError) as exc:
        history = []
        source_status["football_data"] = {"status": "error", "error": str(exc)}

    try:
        odds_events = SportyBetProvider("za").fetch_upcoming(hours_ahead=data.hours_ahead)
        source_status["sportybet"] = {"status": "ok", "events": len(odds_events), "role": "odds_only"}
        odds_matches = match_fixtures_to_odds(upcoming, odds_events)
        captured_at = datetime.now(timezone.utc).isoformat()
        for fixture in upcoming:
            event = odds_matches.get(str(fixture.get("id") or ""))
            if event and isinstance(event.get("odds"), dict):
                STORE.save_odds_snapshot(str(fixture.get("id") or ""), event["odds"], captured_at)
    except ProviderError as exc:
        odds_events, odds_matches = [], {}
        source_status["sportybet"] = {"status": "error", "error": str(exc), "role": "odds_only"}

    if not history:
        return {
            "status": "insufficient_data", "mode": data.mode, "retrieved_at": datetime.now(timezone.utc).isoformat(),
            "window": {"start": start.isoformat(), "end": end.isoformat()},
            "sources": source_status, "fixture_count": len(upcoming), "recommendation_count": 0,
            "recommendations": [], "skipped": [{"reason": "No historical results available; fixture probabilities were not generated."}],
        }

    raw_candidates = []
    skipped = []
    for fixture in upcoming:
        STORE.save_fixture(fixture)
        result = build_fixture_candidates(
            fixture, history, odds_matches.get(fixture["id"]), set(data.markets),
            data.minimum_probability, data.min_odds, data.max_odds,
        )
        if result["status"] != "ok":
            skipped.append({
                "fixture_id": fixture["id"], "home": fixture["home"], "away": fixture["away"],
                "reason": result["reason"],
            })
            continue
        for candidate in result["candidates"]:
            raw_candidates.append(candidate)

    raw_candidates.sort(key=lambda item: (item["rank_score"], item["estimated_probability"]), reverse=True)
    selected_candidates = raw_candidates[:10] if data.mode == "recommended" else raw_candidates
    recommendations = []
    for candidate in selected_candidates:
        prediction_id = STORE.save_prediction(candidate)
        recommendations.append({**candidate, "prediction_id": prediction_id, "status": "PENDING"})
    return {
        "status": "ok" if recommendations else "no_qualifying_recommendations",
        "mode": data.mode, "strategy": "balanced" if data.mode == "recommended" else "full_statistical_scan",
        "retrieved_at": datetime.now(timezone.utc).isoformat(),
        "window": {"start": start.isoformat(), "end": end.isoformat()},
        "sources": source_status, "fixture_count": len(upcoming),
        "recommendation_count": len(recommendations), "saved_prediction_count": len(recommendations),
        "recommendations": recommendations, "skipped": skipped,
        "notes": [
            "Probabilities are uncalibrated historical-rate baseline estimates, not guaranteed outcomes.",
            "Bookmaker odds are attached after model probabilities and never used as model inputs.",
            "Repeated scans create separate timestamped prediction records; results are settled against recorded snapshots.",
        ],
    }


@app.get("/predictions")
def predictions(limit: int = 100, status: str | None = None):
    if status and status.upper() not in {"PENDING", "WON", "LOST"}:
        raise HTTPException(status_code=422, detail="Status must be PENDING, WON or LOST")
    rows = STORE.list_predictions(limit, status)
    return {"count": len(rows), "predictions": rows}


@app.get("/results")
def results():
    return {"summary": STORE.results_summary(), "recent": STORE.list_predictions(50)}


@app.post("/results/refresh")
def refresh_results():
    """Fetch recent scoreboards and settle pending picks only on full-time scores."""
    today = datetime.now(LOCAL_TZ).date()
    provider = LiveScoreProvider()
    fixtures = []
    errors = []
    for day in (today - timedelta(days=1), today):
        try:
            fixtures.extend(_fixture_dict(item) for item in provider.fetch_day(day, utc_offset_hours=2))
        except ProviderError as exc:
            errors.append(str(exc))
    finished = []
    for item in fixtures:
        if not (is_finished_status(item.get("status", ""))
                and item.get("home_score") is not None and item.get("away_score") is not None):
            continue
        completed = dict(item)
        try:
            completed["statistics"] = provider.fetch_statistics(str(item.get("id") or ""))
        except ProviderError as exc:
            completed["statistics"] = {}
            errors.append(f"Statistics unavailable for {item.get('id')}: {exc}")
        finished.append(completed)
    settled = STORE.settle_many(finished)
    return {
        "status": "partial" if errors else "ok", "fixtures_checked": len(fixtures),
        "newly_settled_predictions": settled, "errors": errors,
        "summary": STORE.results_summary(),
    }


@app.get("/odds/dropping")
def dropping_odds(minimum_drop_pct: float = 3.0, lookback_hours: int = 24, limit: int = 200):
    """Expose recorded shortening prices; this is market movement, not a probability forecast."""
    if not 0 < minimum_drop_pct <= 50:
        raise HTTPException(status_code=422, detail="minimum_drop_pct must be between 0 and 50")
    if not 1 <= lookback_hours <= 168:
        raise HTTPException(status_code=422, detail="lookback_hours must be between 1 and 168")
    return {
        "status": "ok",
        "minimum_drop_pct": minimum_drop_pct,
        "lookback_hours": lookback_hours,
        "count": len(STORE.list_dropping_odds(minimum_drop_pct, lookback_hours, limit)),
        "movements": STORE.list_dropping_odds(minimum_drop_pct, lookback_hours, limit),
    }
