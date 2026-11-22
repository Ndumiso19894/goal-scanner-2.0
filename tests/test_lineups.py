from datetime import date, timedelta

from goal_scanner.lineups import Appearance, PlayerProfile, predict_starting_xi


def make_player(pid, name, position, *, starts=5, availability="available"):
    appearances = [
        Appearance(date(2026, 11, 20) - timedelta(days=i * 7), i < starts, 90 if i < starts else 20, position)
        for i in range(5)
    ]
    return PlayerProfile(pid, name, position, appearances, availability)


def test_predicts_eleven_when_roster_and_history_are_sufficient():
    players = []
    for role, count in (("Goalkeeper", 2), ("Defender", 5), ("Midfielder", 5), ("Forward", 4)):
        players.extend(make_player(f"{role}-{i}", f"{role} {i}", role) for i in range(count))
    result = predict_starting_xi(players, as_of="2026-11-22", recent_formations=["4-3-3"])
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
    players = []
    for role, count in (("Goalkeeper", 2), ("Defender", 5), ("Midfielder", 5), ("Forward", 4)):
        players.extend(make_player(f"{role}-{i}", f"{role} {i}", role) for i in range(count))
    players[0].availability = "suspended"
    players[2].availability = "missing"
    result = predict_starting_xi(players, as_of="2026-11-22")
    assert result["status"] == "predicted"
    selected = {p["player_id"] for p in result["predicted_xi"]}
    assert "Goalkeeper-0" not in selected
    assert "Defender-0" not in selected
