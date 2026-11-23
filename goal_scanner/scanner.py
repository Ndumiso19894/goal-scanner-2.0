"""Fixture-level goal estimates and market candidate generation.

This is a transparent historical-rate baseline, not a trained or calibrated model.
Only completed Football-Data matches strictly before the fixture date are eligible.
Bookmaker prices are attached after probabilities have been calculated.
"""
from __future__ import annotations

from datetime import date, datetime, timezone
from typing import Any

from .markets import Market
from .models import goal_market_probabilities
from .providers import team_similarity

MODEL_VERSION = "venue-poisson-baseline-1"
MIN_VENUE_MATCHES = 4
MAX_TEAM_MATCHES = 12


def _valid_rows(rows: list[dict[str, Any]], before: date) -> list[dict[str, Any]]:
    valid = []
    for row in rows:
        try:
            played = date.fromisoformat(str(row["date"]))
            home_goals, away_goals = row.get("home_goals"), row.get("away_goals")
            if played >= before or home_goals is None or away_goals is None:
                continue
            hg, ag = int(home_goals), int(away_goals)
            if hg < 0 or ag < 0:
                continue
            valid.append({**row, "home_goals": hg, "away_goals": ag, "_date": played})
        except (KeyError, TypeError, ValueError):
            continue
    return sorted(valid, key=lambda row: row["_date"])


def _team_rows(rows: list[dict[str, Any]], team: str, venue: str) -> list[dict[str, Any]]:
    key = "home" if venue == "home" else "away"
    return [row for row in rows if team_similarity(str(row.get(key, "")), team) >= 0.88]


def _shrunk_average(values: list[int], prior: float, prior_matches: float = 3.0) -> float:
    return (sum(values) + prior * prior_matches) / (len(values) + prior_matches)


def estimate_fixture_lambdas(
    historical_rows: list[dict[str, Any]], home: str, away: str, kickoff: str,
    min_venue_matches: int = MIN_VENUE_MATCHES,
) -> dict[str, Any]:
    """Estimate home/away expected goals from prior home/away results only."""
    try:
        fixture_day = datetime.fromisoformat(kickoff.replace("Z", "+00:00")).date()
    except (TypeError, ValueError):
        return {"status": "insufficient_data", "reason": "Fixture kickoff is invalid"}
    rows = _valid_rows(historical_rows, fixture_day)
    if len(rows) < 12:
        return {"status": "insufficient_data", "reason": "Fewer than 12 prior completed league matches"}

    league_home = sum(row["home_goals"] for row in rows) / len(rows)
    league_away = sum(row["away_goals"] for row in rows) / len(rows)
    if league_home <= 0 or league_away <= 0:
        return {"status": "insufficient_data", "reason": "League goal baseline is not usable"}

    home_rows = _team_rows(rows, home, "home")[-MAX_TEAM_MATCHES:]
    away_rows = _team_rows(rows, away, "away")[-MAX_TEAM_MATCHES:]
    if len(home_rows) < min_venue_matches:
        return {"status": "insufficient_data", "reason": f"{home} has only {len(home_rows)} prior home matches; {min_venue_matches} required",
                "home_home_matches": len(home_rows), "away_away_matches": len(away_rows)}
    if len(away_rows) < min_venue_matches:
        return {"status": "insufficient_data", "reason": f"{away} has only {len(away_rows)} prior away matches; {min_venue_matches} required",
                "home_home_matches": len(home_rows), "away_away_matches": len(away_rows)}

    home_scored = [row["home_goals"] for row in home_rows]
    home_conceded = [row["away_goals"] for row in home_rows]
    away_scored = [row["away_goals"] for row in away_rows]
    away_conceded = [row["home_goals"] for row in away_rows]

    h_attack = _shrunk_average(home_scored, league_home)
    h_concede = _shrunk_average(home_conceded, league_away)
    a_attack = _shrunk_average(away_scored, league_away)
    a_concede = _shrunk_average(away_conceded, league_home)

    home_lambda = (h_attack / league_home) * (a_concede / league_home) * league_home
    away_lambda = (a_attack / league_away) * (h_concede / league_away) * league_away
    home_lambda = min(6.0, max(0.05, home_lambda))
    away_lambda = min(6.0, max(0.05, away_lambda))
    sample_quality = min(1.0, min(len(home_rows), len(away_rows)) / 8.0)
    league_quality = min(1.0, len(rows) / 80.0)
    quality = round(0.75 * sample_quality + 0.25 * league_quality, 3)

    return {
        "status": "ok",
        "home_lambda": round(home_lambda, 4),
        "away_lambda": round(away_lambda, 4),
        "league_home_goals": round(league_home, 4),
        "league_away_goals": round(league_away, 4),
        "home_home_matches": len(home_rows),
        "away_away_matches": len(away_rows),
        "historical_matches": len(rows),
        "data_quality": quality,
        "history_cutoff": fixture_day.isoformat(),
        "model_version": MODEL_VERSION,
        "calibration": "uncalibrated_baseline",
    }


def _at(odds: dict[str, Any], group: str, line: float, side: str = "over") -> float | None:
    try:
        value = odds.get(group, {}).get(line, {}).get(side)
        return float(value) if value is not None and float(value) > 1 else None
    except (AttributeError, TypeError, ValueError):
        return None


def market_selections(probabilities: dict[str, float], odds: dict[str, Any]) -> list[dict[str, Any]]:
    """Flatten selected market families into individual selectable outcomes."""
    specs = [
        ("home_over_0_5", "over", "home_over_0_5", _at(odds, "home_team_goals", 0.5)),
        ("away_over_0_5", "over", "away_over_0_5", _at(odds, "away_team_goals", 0.5)),
        ("home_over_1_5", "over", "home_over_1_5", _at(odds, "home_team_goals", 1.5)),
        ("away_over_1_5", "over", "away_over_1_5", _at(odds, "away_team_goals", 1.5)),
        ("match_over_1_5", "over", "match_over_1_5", _at(odds, "match_total_goals", 1.5)),
        ("match_over_2_5", "over", "match_over_2_5", _at(odds, "match_total_goals", 2.5)),
        ("match_winner", "home", "home_win", (odds.get("1X2") or {}).get("home")),
        ("match_winner", "draw", "draw", (odds.get("1X2") or {}).get("draw")),
        ("match_winner", "away", "away_win", (odds.get("1X2") or {}).get("away")),
        ("double_chance", "1X", "double_chance_1x", (odds.get("double_chance") or {}).get("1X")),
        ("double_chance", "X2", "double_chance_x2", (odds.get("double_chance") or {}).get("X2")),
        ("double_chance", "12", "double_chance_12", (odds.get("double_chance") or {}).get("12")),
        ("btts_yes", "yes", "btts_yes", (odds.get("btts") or {}).get("yes")),
    ]
    output = []
    for market, selection, probability_key, price in specs:
        if probability_key not in probabilities:
            continue
        try:
            price = float(price) if price is not None and float(price) > 1 else None
        except (TypeError, ValueError):
            price = None
        probability = probabilities[probability_key]
        ev = probability * price - 1 if price is not None else None
        output.append({
            "market": market, "selection": selection,
            "estimated_probability": round(probability, 6),
            "odds": round(price, 4) if price is not None else None,
            "expected_value": round(ev, 6) if ev is not None else None,
            "odds_status": "available" if price is not None else "unavailable",
        })
    return output


def build_fixture_candidates(
    fixture: dict[str, Any], historical_rows: list[dict[str, Any]],
    odds_event: dict[str, Any] | None, selected_markets: set[str],
    min_probability: float = 0.60, min_odds: float | None = None,
    max_odds: float | None = None,
) -> dict[str, Any]:
    home, away = str(fixture.get("home") or ""), str(fixture.get("away") or "")
    kickoff = str(fixture.get("kickoff") or "")
    estimate = estimate_fixture_lambdas(historical_rows, home, away, kickoff)
    if estimate["status"] != "ok":
        return {"status": "insufficient_data", "fixture": fixture, "reason": estimate["reason"]}
    probabilities = {key.value: value for key, value in goal_market_probabilities(
        estimate["home_lambda"], estimate["away_lambda"]).items()}
    prices = (odds_event or {}).get("odds") or {}
    candidates = []
    for item in market_selections(probabilities, prices):
        if item["market"] not in selected_markets or item["estimated_probability"] < min_probability:
            continue
        price = item["odds"]
        if min_odds is not None and (price is None or price < min_odds):
            continue
        if max_odds is not None and (price is None or price > max_odds):
            continue
        ev = item["expected_value"]
        value_score = min(1.0, max(0.0, (ev + 0.10) / 0.30)) if ev is not None else 0.5
        rank_score = 0.55 * item["estimated_probability"] + 0.30 * estimate["data_quality"] + 0.15 * value_score
        candidates.append({
            **item,
            "rank_score": round(rank_score, 6),
            "fixture_id": str(fixture.get("id") or fixture.get("source_id") or ""),
            "kickoff": kickoff, "home": home, "away": away,
            "competition": fixture.get("competition") or "",
            "home_lambda": estimate["home_lambda"], "away_lambda": estimate["away_lambda"],
            "market_probabilities": probabilities,
            "market_odds": prices,
            "data_quality": estimate["data_quality"],
            "sample": {
                "home_home_matches": estimate["home_home_matches"],
                "away_away_matches": estimate["away_away_matches"],
                "historical_matches": estimate["historical_matches"],
            },
            "history_cutoff": estimate["history_cutoff"],
            "model_version": MODEL_VERSION,
            "calibration": "uncalibrated_baseline",
            "reasons": [
                f"Historical home/away sample: {estimate['home_home_matches']}/{estimate['away_away_matches']} matches",
                "Bookmaker odds are used only for value comparison, not to calculate probability"
                if price is not None else "No matched bookmaker price; probability estimate shown without value claim",
            ],
        })
    return {"status": "ok", "fixture": fixture, "estimates": estimate,
            "probabilities": probabilities, "candidates": candidates}


def is_finished_status(status: str) -> bool:
    return str(status or "").strip().upper() in {"FT", "AET", "PEN", "FINISHED", "FULL TIME"}
