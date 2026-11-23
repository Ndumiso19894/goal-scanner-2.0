from goal_scanner.storage import Store


def test_fixture_and_snapshot_persistence(tmp_path):
    from goal_scanner.snapshots import make_snapshot, Stage
    store = Store(str(tmp_path / "data.sqlite3"))
    store.save_fixture({"id": "f1", "kickoff": "2026-12-01T18:00:00Z", "home": "A", "away": "B"})
    snap = make_snapshot("f1", Stage.T_MINUS_24H, {"p": .6})
    store.save_snapshot(snap)
    assert store.list_fixtures()[0]["id"] == "f1"


def test_predictions_are_saved_and_settled_from_verified_scores(tmp_path):
    store = Store(str(tmp_path / "predictions.sqlite3"))
    candidate = {
        "fixture_id": "ls-123", "kickoff": "2026-12-01T18:00:00+02:00",
        "home": "A", "away": "B", "competition": "League",
        "market": "match_over_2_5", "selection": "over",
        "estimated_probability": 0.72, "odds": 1.8, "expected_value": 0.296,
        "rank_score": 0.71, "data_quality": 0.8,
        "model_version": "venue-poisson-baseline-1", "calibration": "uncalibrated_baseline",
    }
    prediction_id = store.save_prediction(candidate)
    assert store.list_predictions()[0]["id"] == prediction_id
    assert store.settle_fixture("ls-123", 2, 1, "FT") == 1
    assert store.settle_fixture("ls-123", 2, 1, "FT") == 0
    result = store.results_summary()
    assert result["settled"] == 1
    assert result["wins"] == 1
    assert result["accuracy"] == 1.0
    assert result["profit_units"] == 0.8


def test_non_final_scores_do_not_settle_predictions(tmp_path):
    store = Store(str(tmp_path / "pending.sqlite3"))
    candidate = {
        "fixture_id": "ls-456", "kickoff": "2026-12-01T18:00:00+02:00",
        "home": "A", "away": "B", "competition": "",
        "market": "btts_yes", "selection": "yes",
        "estimated_probability": 0.61, "odds": None, "expected_value": None,
        "rank_score": 0.6, "data_quality": 0.7,
        "model_version": "venue-poisson-baseline-1", "calibration": "uncalibrated_baseline",
    }
    store.save_prediction(candidate)
    assert store.settle_fixture("ls-456", 1, 1, "HT") == 0
    assert store.results_summary()["pending"] == 1


def test_dropping_odds_requires_two_snapshots_and_measures_price_fall(tmp_path):
    store = Store(str(tmp_path / "odds.sqlite3"))
    store.save_fixture({
        "id": "fx-odds", "kickoff": "2026-12-01T18:00:00Z",
        "home": "Home FC", "away": "Away FC", "competition": "Test League",
    })
    store.save_odds_snapshot("fx-odds", {"match_total_goals": {"2.5": {"over": 1.90}}},
                              "2026-11-23T08:00:00+00:00")
    assert store.list_dropping_odds() == []
    store.save_odds_snapshot("fx-odds", {"match_total_goals": {"2.5": {"over": 1.80}}},
                              "2026-11-23T08:15:00+00:00")
    movements = store.list_dropping_odds()
    assert len(movements) == 1
    assert movements[0]["opening_odds"] == 1.9
    assert movements[0]["current_odds"] == 1.8
    assert movements[0]["drop_pct"] == 5.26
    assert movements[0]["samples"] == 2
