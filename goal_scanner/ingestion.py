"""Orchestrate the Mr2Odds-compatible providers without hiding source failures."""
from __future__ import annotations
from dataclasses import asdict
from datetime import date, datetime, timezone
from typing import Any
from .providers import (
    FootballDataProvider, LiveScoreProvider, ProviderError, SportyBetProvider,
    TransfermarktProvider, match_fixtures_to_odds, provider_manifest,
)

def collect_sources(match_date: date, season: str, division: str, hours_ahead: float = 48,
                    include_squads: bool = True) -> dict[str, Any]:
    """Fetch all configured feeds independently and report per-source success/failure.

    This function deliberately does not derive probabilities or mix bookmaker prices and
    squad metrics into the goal model. It returns provenance-ready raw/normalised records.
    """
    retrieved_at = datetime.now(timezone.utc).isoformat()
    output: dict[str, Any] = {
        "retrieved_at": retrieved_at,
        "sources": {},
        "fixtures": [],
        "historical_results": [],
        "sportybet_events": [],
        "matched_odds": {},
        "squad_context": [],
    }

    live_provider = LiveScoreProvider()
    try:
        fixtures = live_provider.fetch_day(match_date, utc_offset_hours=2)
        output["fixtures"] = [asdict(item) for item in fixtures]
        output["sources"]["livescore"] = {"status": "ok", "records": len(fixtures)}
    except ProviderError as exc:
        fixtures = []
        output["sources"]["livescore"] = {"status": "error", "error": str(exc)}

    football_provider = FootballDataProvider()
    try:
        history = football_provider.fetch_season(season, division)
        output["historical_results"] = history
        completed = sum(1 for row in history if row.get("home_goals") is not None and row.get("away_goals") is not None)
        output["sources"]["football_data"] = {
            "status": "ok", "records": len(history), "completed_matches": completed,
            "missing_full_time_scores": len(history) - completed,
        }
    except (ProviderError, ValueError) as exc:
        history = []
        output["sources"]["football_data"] = {"status": "error", "error": str(exc)}

    sporty_provider = SportyBetProvider("za")
    try:
        events = sporty_provider.fetch_upcoming(hours_ahead=hours_ahead)
        output["sportybet_events"] = events
        fixture_rows = [asdict(item) for item in fixtures]
        output["matched_odds"] = match_fixtures_to_odds(fixture_rows, events)
        output["sources"]["sportybet"] = {
            "status": "ok", "events": len(events), "matched_fixtures": len(output["matched_odds"]),
            "country": "za", "role": "odds_only",
        }
    except ProviderError as exc:
        output["sources"]["sportybet"] = {"status": "error", "error": str(exc), "role": "odds_only"}

    if include_squads:
        try:
            clubs = TransfermarktProvider().fetch_division_squads(division)
            output["squad_context"] = clubs
            output["sources"]["transfermarkt"] = {
                "status": "ok", "clubs": len(clubs), "role": "context_only",
            }
        except ProviderError as exc:
            output["sources"]["transfermarkt"] = {"status": "error", "error": str(exc), "role": "context_only"}
    else:
        output["sources"]["transfermarkt"] = {"status": "skipped", "role": "context_only"}

    output["history_match_records"] = len(FootballDataProvider.to_match_records(output["historical_results"]))
    output["provider_manifest"] = provider_manifest()
    output["overall_status"] = (
        "ok" if all(v["status"] == "ok" for v in output["sources"].values())
        else "partial" if any(v["status"] == "ok" for v in output["sources"].values())
        else "failed"
    )
    return output
