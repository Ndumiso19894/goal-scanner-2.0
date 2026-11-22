import json

from research.statsbomb.inventory import inventory


def test_inventory_counts_local_synthetic_structure(tmp_path):
    root = tmp_path / "data"
    (root / "matches" / "2").mkdir(parents=True)
    (root / "lineups").mkdir()
    (root / "competitions.json").write_text(
        json.dumps([{"competition_id": 2, "season_id": 27}]), encoding="utf-8"
    )
    (root / "matches" / "2" / "27.json").write_text(
        json.dumps([{"match_id": 100}, {"match_id": 101}]), encoding="utf-8"
    )
    (root / "lineups" / "100.json").write_text(
        json.dumps([
            {"team_id": 1, "lineup": [{"player_id": 10}, {"player_id": 11}]},
            {"team_id": 2, "lineup": [{"player_id": 20}]},
        ]),
        encoding="utf-8",
    )

    before = (root / "lineups" / "100.json").read_bytes()
    result = inventory(root)
    after = (root / "lineups" / "100.json").read_bytes()

    assert result == {
        "competition_season_entries": 1,
        "matches_listed": 2,
        "matches_with_lineup_files": 1,
        "lineup_player_rows": 3,
    }
    assert before == after
    assert not list(root.rglob("*.csv"))


def test_inventory_requires_official_competitions_file(tmp_path):
    import pytest

    with pytest.raises(FileNotFoundError, match="competitions.json"):
        inventory(tmp_path)
