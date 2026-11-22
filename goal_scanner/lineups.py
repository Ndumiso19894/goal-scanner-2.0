"""Evidence-based predicted starting XIs for any competition with adequate player data."""
from __future__ import annotations
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

def _role(value: str | None) -> str:
    text = (value or "").strip().lower()
    if any(x in text for x in ("goalkeeper", "keeper", "gk")): return "GK"
    if any(x in text for x in ("forward", "attacker", "striker", "winger", "fwd")): return "FWD"
    if any(x in text for x in ("midfield", "midfielder", "mid")): return "MID"
    if any(x in text for x in ("defender", "defence", "defense", "back", "def")): return "DEF"
    return "UNK"

def _as_date(value: date | str | None) -> date | None:
    if value is None: return None
    if isinstance(value, date): return value
    try: return date.fromisoformat(str(value)[:10])
    except ValueError: return None

def _player_score(player: PlayerProfile, today: date) -> tuple[float, list[str]]:
    appearances = sorted(player.appearances, key=lambda x: x.match_date, reverse=True)
    if not appearances: return 0.0, ["No appearance history"]
    recent5, recent10 = appearances[:5], appearances[:10]
    starts5 = sum(x.started for x in recent5) / len(recent5)
    starts10 = sum(x.started for x in recent10) / len(recent10)
    minutes10 = sum(max(0, min(120, x.minutes)) for x in recent10) / (90 * len(recent10))
    score = 42 * starts5 + 20 * starts10 + 18 * min(1.0, minutes10)
    reasons = [f"{sum(x.started for x in recent5)}/{len(recent5)} recent starts",
               f"{round(minutes10 * 90)} average minutes across recent appearances"]
    days_rest = max(0, (today - appearances[0].match_date).days)
    if days_rest <= 2:
        score -= 8
        reasons.append("Short rest after latest appearance")
    elif days_rest >= 4:
        score += 2
        reasons.append("Adequate recovery interval")
    if player.availability == "missing": return -1000.0, ["Reported unavailable"]
    if player.availability == "suspended": return -1000.0, ["Suspended"]
    if player.availability == "questionable":
        score -= 22
        reasons.append("Questionable availability; penalised, not assumed absent")
    elif player.availability == "available":
        score += 4
        reasons.append("Listed available")
    return score, reasons

def _softmax_probability(score: float, alternatives: list[float]) -> float:
    top = max(alternatives)
    weights = [exp(max(-30.0, min(30.0, (x - top) / 12.0))) for x in alternatives]
    return round(100.0 * exp(max(-30.0, min(30.0, (score - top) / 12.0))) / sum(weights), 1)

def predict_starting_xi(players: Iterable[PlayerProfile], *, as_of: date | str,
                        recent_formations: Iterable[str] = (), minimum_players: int = 14,
                        minimum_appearance_samples: int = 3) -> dict[str, Any]:
    """Predict a likely XI; probabilities are relative scores until calibrated by backtesting."""
    match_day = _as_date(as_of)
    if match_day is None: raise ValueError("as_of must be a date or ISO date string")
    roster = list(players)
    eligible = [p for p in roster if _role(p.position) != "UNK"]
    evidence_players = [p for p in eligible if len(p.appearances) >= minimum_appearance_samples]
    if len(roster) < minimum_players or len(evidence_players) < 11:
        return {"status":"insufficient_data",
                "reason":"Not enough player profiles or recent appearance history to make a defensible XI.",
                "roster_size":len(roster),"players_with_sufficient_history":len(evidence_players),
                "predicted_xi":[],"data_quality":"insufficient"}
    scores = {p.player_id:_player_score(p, match_day) for p in eligible}
    recent = [str(x).strip() for x in recent_formations if str(x).strip() in FORMATIONS]
    formation = max(set(recent), key=recent.count) if recent else "4-3-3"
    selected, chosen_formation = [], formation
    for candidate in [formation] + [f for f in FORMATIONS if f != formation]:
        chosen = []
        used = set()
        possible = True
        for role, count in FORMATIONS[candidate].items():
            pool = [p for p in evidence_players if _role(p.position) == role and
                    p.availability not in ("missing", "suspended")]
            pool.sort(key=lambda p:scores[p.player_id][0], reverse=True)
            if len(pool) < count:
                possible = False
                break
            chosen.extend(pool[:count])
            used.update(p.player_id for p in pool[:count])
        if possible and len(used) == 11:
            selected, chosen_formation = chosen, candidate
            break
    if len(selected) != 11:
        return {"status":"insufficient_data",
                "reason":"Available player records cannot fill all 11 positions without guessing.",
                "roster_size":len(roster),"players_with_sufficient_history":len(evidence_players),
                "predicted_xi":[],"data_quality":"insufficient"}
    output = []
    for role in ("GK","DEF","MID","FWD"):
        group = [p for p in selected if _role(p.position) == role]
        pool = [p for p in evidence_players if _role(p.position) == role and
                p.availability not in ("missing","suspended")]
        pool_scores = [scores[p.player_id][0] for p in pool]
        for player in group:
            score, reasons = scores[player.player_id]
            probability = _softmax_probability(score, pool_scores) if pool_scores else 0.0
            confidence = "high" if probability >= 65 else "medium" if probability >= 35 else "low"
            output.append({"player_id":player.player_id,"name":player.name,"position":player.position,
                "selection_score":round(score,2),"selection_probability":probability,
                "confidence":confidence,"reasons":reasons})
    return {"status":"predicted","formation":chosen_formation,"as_of":match_day.isoformat(),
            "predicted_xi":output,"data_quality":"usable","roster_size":len(roster),
            "players_with_sufficient_history":len(evidence_players),
            "warnings":["Model-generated XI, not an official team sheet.",
                "Selection probabilities are relative scores and require backtesting calibration.",
                "Private training, illness and manager decisions may not appear in public data."]}
