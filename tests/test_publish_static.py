from datetime import date

from scripts.publish_static import _head_to_head, _league_table, _team_pre_match_stats


def test_league_table_includes_home_away_splits_and_points():
    rows = [
        {"date": "2026-11-01", "home": "Alpha", "away": "Beta", "home_goals": 2, "away_goals": 0},
        {"date": "2026-11-08", "home": "Gamma", "away": "Alpha", "home_goals": 1, "away_goals": 1},
        {"date": "2026-12-01", "home": "Beta", "away": "Alpha", "home_goals": 3, "away_goals": 0},
    ]
    table = _league_table(rows, date(2026, 11, 30))
    alpha = next(row for row in table if row["team"] == "Alpha")
    assert alpha["played"] == 2
    assert alpha["points"] == 4
    assert alpha["gd"] == 2
    assert alpha["home"]["played"] == 1
    assert alpha["away"]["played"] == 1
    assert alpha["form"] == "WD"


def test_venue_form_exposes_recent_match_list_and_non_goal_stats():
    rows = [
        {"date": "2026-11-01", "kickoff": "2026-11-01T15:00:00+00:00", "home": "Alpha", "away": "Beta", "home_goals": 2, "away_goals": 1, "statistics": {"HS": 12, "HST": 5, "HxG": 1.7, "HC": 6}},
        {"date": "2026-11-08", "kickoff": "2026-11-08T15:00:00+00:00", "home": "Alpha", "away": "Gamma", "home_goals": 1, "away_goals": 0, "statistics": {"HS": 10, "HST": 4, "HxG": 1.2, "HC": 4}},
    ]
    stats = _team_pre_match_stats(rows, "Alpha", "2026-11-20T15:00:00+00:00", "home")
    assert stats["form"] == "WW"
    assert len(stats["recent_matches"]) == 2
    assert stats["recent_matches"][0]["opponent"] == "Gamma"
    assert stats["shots_avg"] == 11
    assert stats["shots_on_target_avg"] == 4.5


def test_head_to_head_exposes_recent_meetings_in_home_team_order():
    rows = [{"date": "2026-10-10", "kickoff": "2026-10-10T15:00:00+00:00", "home": "Beta", "away": "Alpha", "home_goals": 0, "away_goals": 2}]
    h2h = _head_to_head(rows, "Alpha", "Beta", "2026-11-20T15:00:00+00:00")
    assert h2h["played"] == 1
    assert h2h["recent_meetings"][0]["score"] == "2-0"