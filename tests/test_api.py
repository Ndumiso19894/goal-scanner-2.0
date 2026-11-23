from datetime import date, datetime, timezone

from goal_scanner.providers import Fixture
from goal_scanner.storage import Store


def _history():
    rows = []
    start = date(2026, 10, 1)
    for index, opponent in enumerate(("North", "South", "East", "West")):
        day = date(2026, 10, 1 + index * 2).isoformat()
        rows.append({"date": day, "home": "Home FC", "away": opponent,
                     "home_goals": 2, "away_goals": 1})
        rows.append({"date": day, "home": opponent, "away": "Away FC",
                     "home_goals": 1, "away_goals": 2})
    for index in range(4):
        day = date(2026, 10, 2 + index * 2).isoformat()
        rows.append({"date": day, "home": f"Club {index}", "away": f"Other {index}",
                     "home_goals": 1, "away_goals": 1})
    return rows


def test_scanner_api_saves_a_real_fixture_prediction(monkeypatch, tmp_path):
    from goal_scanner import api

    kickoff = "2026-11-23T18:00:00+02:00"
    fixture = Fixture(
        source="livescore", source_id="ls-fixture-1", kickoff=kickoff,
        home="Home FC", away="Away FC", competition="Test League",
        status="NS", retrieved_at="2026-11-22T12:00:00+00:00",
    )

    class Live:
        def fetch_day(self, *args, **kwargs):
            return [fixture]

    class Football:
        def fetch_season(self, *args, **kwargs):
            return _history()

    class Sporty:
        def __init__(self, *args, **kwargs):
            pass
        def fetch_upcoming(self, **kwargs):
            return [{"source_id":"sportybet-1","kickoff":kickoff,"home":"Home FC","away":"Away FC",
                     "odds":{"match_total_goals":{"2.5":{"over":1.9,"under":1.9}}}}]

    monkeypatch.setattr(api, "LiveScoreProvider", Live)
    monkeypatch.setattr(api, "FootballDataProvider", Football)
    monkeypatch.setattr(api, "SportyBetProvider", Sporty)
    monkeypatch.setattr(api, "STORE", Store(str(tmp_path / "api.sqlite3")))
    start = datetime(2026, 11, 23, 15, 30, tzinfo=timezone.utc)
    end = datetime(2026, 11, 23, 17, 0, tzinfo=timezone.utc)
    monkeypatch.setattr(api, "_window", lambda data: (start, end, [date(2026, 11, 23)]))

    request = api.ScannerRunInput(
        match_date=date(2026, 11, 23), season="2627", division="E0",
        hours_ahead=24, minimum_probability=0.50,
        markets=["match_over_2_5"], mode="recommended",
    )
    response = api.scanner_run(request)
    assert response["recommendation_count"] == 1
    assert response["recommendations"][0]["fixture_id"] == "ls-fixture-1"
    assert api.STORE.results_summary()["pending"] == 1
