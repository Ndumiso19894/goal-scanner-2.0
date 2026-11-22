"""Run the goal scanner on GitHub Actions and publish public static JSON feeds."""
from __future__ import annotations

import json
import os
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from goal_scanner import api
from goal_scanner.providers import LiveScoreProvider, ProviderError

# Football-Data.co.uk division codes and the corresponding LiveScore competition names.
# Only competitions with a matching historical results feed are included.
LEAGUES = [
    ("E0", "Premier League"),
    ("E1", "Championship"),
    ("E2", "League One"),
    ("E3", "League Two"),
    ("SC0", "Premiership"),
    ("D1", "Bundesliga"),
    ("I1", "Serie A"),
    ("SP1", "LaLiga"),
    ("F1", "Ligue 1"),
    ("N1", "Eredivisie"),
    ("B1", "Pro League"),
    ("P1", "Liga Portugal"),
    ("T1", "Süper Lig"),
    ("G1", "Super League"),
]
MARKETS = sorted(api.SELECTABLE_MARKETS)
OUTPUT = Path("docs/data")
OUTPUT.mkdir(parents=True, exist_ok=True)


def current_season(today: date) -> str:
    start_year = today.year if today.month >= 7 else today.year - 1
    return f"{start_year % 100:02d}{(start_year + 1) % 100:02d}"


def write_json(name: str, payload: dict) -> None:
    path = OUTPUT / name
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    today = datetime.now(timezone.utc).astimezone(api.LOCAL_TZ).date()
    season = current_season(today)
    league_status = []
    for division, competition in LEAGUES:
        try:
            request = api.ScannerRunInput(
                match_date=today,
                season=season,
                division=division,
                hours_ahead=72,
                minimum_probability=0.60,
                markets=MARKETS,
                mode="full",
                competition_query=competition,
            )
            result = api.scanner_run(request)
            league_status.append({
                "division": division,
                "competition": competition,
                "status": result.get("status", "unknown"),
                "fixtures": result.get("fixture_count", 0),
                "recommendations": result.get("recommendation_count", 0),
                "sources": result.get("sources", {}),
            })
        except Exception as exc:  # A single unavailable league must not stop the others.
            league_status.append({
                "division": division,
                "competition": competition,
                "status": "error",
                "error": f"{type(exc).__name__}: {exc}",
            })

    # Settle prior predictions only when the scoreboard marks a match as full-time.
    try:
        settlement = api.refresh_results()
    except Exception as exc:
        settlement = {"status": "error", "error": f"{type(exc).__name__}: {exc}"}

    now = datetime.now(timezone.utc)
    latest_by_market: dict[tuple[str, str, str], dict] = {}
    for row in api.STORE.list_predictions(limit=500, status="PENDING"):
        candidate = row.get("payload") or {}
        try:
            kickoff = datetime.fromisoformat(str(candidate.get("kickoff", "")).replace("Z", "+00:00"))
            if kickoff < now or kickoff > now + timedelta(days=7):
                continue
        except (TypeError, ValueError):
            continue
        candidate = {**candidate, "prediction_id": row["id"], "status": "PENDING"}
        key = (str(candidate.get("fixture_id", "")), str(candidate.get("market", "")),
               str(candidate.get("selection", "")))
        # list_predictions is newest-first, so the first record is the latest snapshot.
        latest_by_market.setdefault(key, candidate)

    recommendations = sorted(
        latest_by_market.values(),
        key=lambda item: (item.get("rank_score", 0), item.get("estimated_probability", 0)),
        reverse=True,
    )[:100]

    fixture_rows = []
    fixture_errors = []
    provider = LiveScoreProvider()
    for offset in range(3):
        day = today + timedelta(days=offset)
        try:
            for item in provider.fetch_day(day, utc_offset_hours=2):
                fixture_rows.append({
                    "id": item.source_id, "kickoff": item.kickoff, "home": item.home,
                    "away": item.away, "competition": item.competition, "country": item.country,
                    "status": item.status, "home_score": item.home_score,
                    "away_score": item.away_score, "source": item.source,
                })
        except ProviderError as exc:
            fixture_errors.append(str(exc))
    fixture_rows.sort(key=lambda item: item.get("kickoff") or "")

    generated_at = datetime.now(timezone.utc).isoformat()
    sources = {
        "livescore_and_odds": "Collected during each league scan; bookmaker odds are comparison-only.",
        "historical_results": "Football-Data.co.uk completed results, filtered to matches before each fixture.",
        "league_runs": league_status,
    }
    write_json("latest.json", {
        "schema_version": 1,
        "updated_at": generated_at,
        "season": season,
        "status": "ok" if any(x.get("status") in {"ok", "no_qualifying_recommendations"} for x in league_status) else "partial",
        "recommendation_count": len(recommendations),
        "fixture_count": len(fixture_rows),
        "recommendations": recommendations,
        "sources": sources,
        "notes": [
            "Probabilities are uncalibrated historical-rate estimates, not guaranteed outcomes.",
            "Bookmaker odds are attached after model probabilities and are not model inputs.",
            "Only fixtures with sufficient prior home/away history are eligible.",
        ],
    })
    write_json("fixtures.json", {
        "schema_version": 1, "updated_at": generated_at,
        "count": len(fixture_rows), "fixtures": fixture_rows, "errors": fixture_errors,
    })
    write_json("results.json", {
        "schema_version": 1, "updated_at": generated_at,
        "summary": api.STORE.results_summary(),
        "recent": api.STORE.list_predictions(limit=100),
        "settlement_refresh": settlement,
    })

    print(f"Published {len(recommendations)} upcoming selections from {len(league_status)} league scans.")
    for item in league_status:
        print(f"{item['division']} {item['competition']}: {item['status']} "
              f"({item.get('fixtures', 0)} fixtures, {item.get('recommendations', 0)} picks)")
    print(f"Fixtures feed: {len(fixture_rows)} rows; settlement: {settlement.get('status', 'unknown')}")


if __name__ == "__main__":
    main()
