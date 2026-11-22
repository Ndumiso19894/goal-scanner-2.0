"""Evidence-based predicted starting XIs for competitions with adequate player data."""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from datetime import date
from math import exp
from typing import Any, Iterable


@dataclass(frozen=True)
class Appearance:
    match_date: date
    started: bool
    minutes: int = 0
    position: str | None = None


@dataclass
class PlayerProfile:
    player_id: str
    name: str
    position: str
    appearances: list[Appearance] = field(default_factory=list)
    availability: str = "unknown"


FORMATIONS = {
    "4-3-3": {"GK": 1, "DEF": 4, "MID": 3, "FWD": 3},
    "4-2-3-1": {"GK": 1, "DEF": 4, "MID": 5, "FWD": 1},
    "4-4-2": {"GK": 1, "DEF": 4, "MID": 4, "FWD": 2},
    "3-5-2": {"GK": 1, "DEF": 3, "MID": 5, "FWD": 2},
    "3-4-3": {"GK": 1, "DEF": 3, "MID": 4, "FWD": 3},
    "5-3-2": {"GK": 1, "DEF": 5, "MID": 3, "FWD": 2},
}
VALID_AVAILABILITY = {"unknown", "available", "questionable", "missing", "suspended"}


def _role(value: str | None) -> str:
    text = (value or "").strip().lower()
    if any(x in text for x in ("goalkeeper", "keeper", "gk")):
        return "GK"
    if any(x in text for x in ("forward", "attacker", "striker", "winger", "fwd")):
        return "FWD"
    if any(x in text for x in ("midfield", "midfielder", "mid")):
        return "MID"
    if any(x in text for x in ("defender", "defence", "defense", "back", "def")):
        return "DEF"
    return "UNK"


def _as_date(value: date | str | None) -> date | None:
    if value is None:
        return None
    if isinstance(value, date):
        return value
    try:
        return date.fromisoformat(str(value)[:10])
    except ValueError:
        return None


def _player_score(player: PlayerProfile, today: date) -> tuple[float, list[str]]:
    # Critical: only appearances on or before the prediction cutoff may influence a score.
    appearances = sorted(
        (item for item in player.appearances if item.match_date <= today),
        key=lambda item: item.match_date,
        reverse=True,
    )
    if not appearances:
        return 0.0, ["No eligible appearance history before prediction cutoff"]

    recent5, recent10 = appearances[:5], appearances[:10]
    starts5 = sum(item.started for item in recent5) / len(recent5)
    starts10 = sum(item.started for item in recent10) / len(recent10)
    minutes10 = sum(max(0, min(120, item.minutes)) for item in recent10) / (90 * len(recent10))
    score = 42 * starts5 + 20 * starts10 + 18 * min(1.0, minutes10)
    reasons = [
        f"{sum(item.started for item in recent5)}/{len(recent5)} recent starts",
        f"{round(minutes10 * 90)} average minutes across recent appearances",
    ]

    days_rest = (today - appearances[0].match_date).days
    if days_rest <= 2:
        score -= 8
        reasons.append("Short rest after latest eligible appearance")
    elif days_rest >= 4:
        score += 2
        reasons.append("Adequate recovery interval")

    availability = player.availability.strip().lower()
    if availability in {"missing", "suspended"}:
        return -1000.0, ["Reported unavailable" if availability == "missing" else "Suspended"]
    if availability == "questionable":
        score -= 22
        reasons.append("Questionable availability; penalised, not assumed absent")
    elif availability == "available":
        score += 4
        reasons.append("Listed available")
    return score, reasons


def _softmax_probability(score: float, alternatives: list[float]) -> float:
    if not alternatives:
        return 0.0
    top = max(alternatives)
    weights = [exp(max(-30.0, min(30.0, (value - top) / 12.0))) for value in alternatives]
    numerator = exp(max(-30.0, min(30.0, (score - top) / 12.0)))
    return round(100.0 * numerator / sum(weights), 1)


def predict_starting_xi(
    players: Iterable[PlayerProfile],
    *,
    as_of: date | str,
    recent_formations: Iterable[str] = (),
    minimum_players: int = 14,
    minimum_appearance_samples: int = 3,
) -> dict[str, Any]:
    """Predict a likely XI using only records available by as_of.

    Selection probabilities are relative within-position score shares, not calibrated
    real-world probabilities. They must not be presented as proven probabilities.
    """
    match_day = _as_date(as_of)
    if match_day is None:
        raise ValueError("as_of must be a date or ISO date string")
    if not 11 <= minimum_players <= 40:
        raise ValueError("minimum_players must be between 11 and 40")
    if not 1 <= minimum_appearance_samples <= 20:
        raise ValueError("minimum_appearance_samples must be between 1 and 20")

    roster = list(players)
    ids = [player.player_id for player in roster]
    if any(not str(player_id).strip() for player_id in ids):
        raise ValueError("Every player must have a non-empty player_id")
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate player_id values are not allowed")

    for player in roster:
        if not player.name.strip() or not player.position.strip():
            raise ValueError("Every player must have a name and position")
        if player.availability.strip().lower() not in VALID_AVAILABILITY:
            raise ValueError(f"Unsupported availability value for {player.player_id}")

    eligible = [player for player in roster if _role(player.position) != "UNK"]
    evidence_players = [
        player for player in eligible
        if sum(item.match_date <= match_day for item in player.appearances)
        >= minimum_appearance_samples
    ]
    if len(roster) < minimum_players or len(evidence_players) < 11:
        return {
            "status": "insufficient_data",
            "reason": "Not enough player profiles or pre-cutoff appearance history to make a defensible XI.",
            "roster_size": len(roster),
            "players_with_sufficient_history": len(evidence_players),
            "predicted_xi": [],
            "data_quality": "insufficient",
        }

    scores = {player.player_id: _player_score(player, match_day) for player in eligible}
    recent = [
        str(value).strip()
        for value in recent_formations
        if str(value).strip() in FORMATIONS
    ]
    formation = Counter(recent).most_common(1)[0][0] if recent else "4-3-3"
    selected: list[PlayerProfile] = []
    chosen_formation = formation

    candidates = [formation] + [name for name in FORMATIONS if name != formation]
    for candidate in candidates:
        chosen: list[PlayerProfile] = []
        used: set[str] = set()
        possible = True
        for role, count in FORMATIONS[candidate].items():
            pool = [
                player for player in evidence_players
                if _role(player.position) == role
                and player.availability.strip().lower() not in {"missing", "suspended"}
            ]
            pool.sort(key=lambda player: scores[player.player_id][0], reverse=True)
            if len(pool) < count:
                possible = False
                break
            chosen.extend(pool[:count])
            used.update(player.player_id for player in pool[:count])
        if possible and len(used) == 11:
            selected, chosen_formation = chosen, candidate
            break

    if len(selected) != 11:
        return {
            "status": "insufficient_data",
            "reason": "Available player records cannot fill all 11 positions without guessing.",
            "roster_size": len(roster),
            "players_with_sufficient_history": len(evidence_players),
            "predicted_xi": [],
            "data_quality": "insufficient",
        }

    output = []
    for role in ("GK", "DEF", "MID", "FWD"):
        group = [player for player in selected if _role(player.position) == role]
        pool = [
            player for player in evidence_players
            if _role(player.position) == role
            and player.availability.strip().lower() not in {"missing", "suspended"}
        ]
        pool_scores = [scores[player.player_id][0] for player in pool]
        for player in group:
            score, reasons = scores[player.player_id]
            probability = _softmax_probability(score, pool_scores)
            confidence = "high" if probability >= 65 else "medium" if probability >= 35 else "low"
            output.append({
                "player_id": player.player_id,
                "name": player.name,
                "position": player.position,
                "selection_score": round(score, 2),
                "selection_probability": probability,
                "confidence": confidence,
                "reasons": reasons,
            })

    return {
        "status": "predicted",
        "formation": chosen_formation,
        "as_of": match_day.isoformat(),
        "predicted_xi": output,
        "data_quality": "usable",
        "roster_size": len(roster),
        "players_with_sufficient_history": len(evidence_players),
        "warnings": [
            "Model-generated XI, not an official team sheet.",
            "Selection probabilities are relative within-position score shares and are not calibrated.",
            "Only appearances dated on or before the prediction cutoff were considered.",
            "Private training, illness and manager decisions may not appear in public data.",
        ],
    }
