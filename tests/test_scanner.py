from datetime import date, timedelta

from goal_scanner.scanner import estimate_fixture_lambdas, build_fixture_candidates


def history_rows():
    rows = []
    start = date(2026, 10, 1)
    for index, opponent in enumerate(("North", "South", "East", "West")):
        day = (start + timedelta(days=index * 2)).isoformat()
        rows.append({"date": day, "home": "Home FC", "away": opponent,
                     "home_goals": 2, "away_goals": 1})
        rows.append({"date": day, "home": opponent, "away": "Away FC",
                     "home_goals": 1, "away_goals": 2})
    for index in range(4):
        day = (start + timedelta(days=1 + index * 2)).isoformat()
        rows.append({"date": day, "home": f"Club {index}", "away": f"Other {index}",
                     "home_goals": 1, "away_goals": 1})
    return rows


def test_fixture_lambdas_use_only_prior_venue_matches():
    rows = history_rows()
    rows.append({"date": "2026-11-22", "home": "Home FC", "away": "Away FC",
                 "home_goals": 6, "away_goals": 0})
    result = estimate_fixture_lambdas(
        rows, "Home FC", "Away FC", "2026-11-23T18:00:00+02:00"
    )
    assert result["status"] == "ok"
    assert result["home_home_matches"] == 4
    assert result["away_away_matches"] == 4
    assert result["historical_matches"] == 12
    assert result["home_lambda"] > 0 and result["away_lambda"] > 0
    assert result["history_cutoff"] == "2026-11-23"


def test_fixture_model_blocks_small_venue_samples():
    result = estimate_fixture_lambdas(
        history_rows()[:5], "Home FC", "Away FC", "2026-11-23T18:00:00+02:00"
    )
    assert result["status"] == "insufficient_data"


def test_candidates_keep_model_probability_separate_from_odds():
    fixture = {
        "id": "fx-1", "source_id": "fx-1", "kickoff": "2026-11-23T18:00:00+02:00",
        "home": "Home FC", "away": "Away FC", "competition": "Test League",
    }
    result = build_fixture_candidates(
        fixture, history_rows(), None, {"home_over_0_5", "match_over_1_5"}, 0.5
    )
    assert result["status"] == "ok"
    assert result["candidates"]
    assert all(item["odds"] is None for item in result["candidates"])
    assert all(item["calibration"] == "uncalibrated_baseline" for item in result["candidates"])
