from datetime import date, timedelta

import pytest

from goal_scanner.lineups import Appearance, PlayerProfile, predict_starting_xi


def make_player(pid, name, position, *, starts=5, availability="available"):
    appearances = [
        Appearance(date(2026, 11, 20) - timedelta(days=i * 7), i < starts, 90 if i < starts else 20, position)
        for i in range(5)
    ]
    return PlayerProfile(pid, name, position, appearances, availability)


def make_squad():
    players = []
    for role, count in (("Goalkeeper", 2), ("Defender", 5), ("Midfielder", 5), ("Forward", 4)):
        players.extend(make_player(f"{role}-{i}", f"{role} {i}", role) for i in range(count))
    return players


def test_predicts_eleven_when_roster_and_history_are_sufficient():
    result = predict_starting_xi(make_squad(), as_of="2026-11-22", recent_formations=["4-3-3"])
    assert result["status"] == "predicted"
    assert result["formation"] == "4-3-3"
    assert len(result["predicted_xi"]) == 11
    assert {p["position"] for p in result["predicted_xi"]} == {
        "Goalkeeper", "Defender", "Midfielder", "Forward"
    }


def test_refuses_to_invent_lineup_when_player_history_is_missing():
    players = [make_player(str(i), f"Player {i}", "Defender") for i in range(10)]
    result = predict_starting_xi(players, as_of=date(2026, 11, 22))
    assert result["status"] == "insufficient_data"
    assert result["predicted_xi"] == []


def test_missing_and_suspended_players_are_not_selected():
    players = make_squad()
    players[0].availability = "suspended"
    players[2].availability = "missing"
    result = predict_starting_xi(players, as_of="2026-11-22")
    assert result["status"] == "predicted"
    selected = {p["player_id"] for p in result["predicted_xi"]}
    assert "Goalkeeper-0" not in selected
    assert "Defender-0" not in selected


def test_future_appearances_cannot_be_used_for_earlier_prediction():
    players = make_squad()
    for player in players:
        player.appearances = [
            Appearance(date(2026, 11, 23) + timedelta(days=i), True, 90, player.position)
            for i in range(5)
        ]
    result = predict_starting_xi(players, as_of="2026-11-22")
    assert result["status"] == "insufficient_data"
    assert result["players_with_sufficient_history"] == 0
    assert result["predicted_xi"] == []


def test_duplicate_player_ids_are_rejected():
    players = make_squad()
    players[1].player_id = players[0].player_id
    with pytest.raises(ValueError, match="Duplicate player_id"):
        predict_starting_xi(players, as_of="2026-11-22")


def test_unsupported_availability_is_rejected():
    players = make_squad()
    players[0].availability = "maybe"
    with pytest.raises(ValueError, match="Unsupported availability"):
        predict_starting_xi(players, as_of="2026-11-22")
