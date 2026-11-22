from datetime import date
from goal_scanner.ingestion import collect_sources

def test_collect_sources_reports_independent_provider_failures(monkeypatch):
    from goal_scanner import ingestion
    class Broken:
        def fetch_day(self, *args, **kwargs): raise ingestion.ProviderError("offline")
    class Football:
        def fetch_season(self, *args, **kwargs): return []
        @staticmethod
        def to_match_records(rows): return []
    class Sporty:
        def __init__(self, *args, **kwargs): pass
        def fetch_upcoming(self, **kwargs): return []
    class Transfermarkt:
        def fetch_division_squads(self, *args, **kwargs): return []
    monkeypatch.setattr(ingestion, "LiveScoreProvider", Broken)
    monkeypatch.setattr(ingestion, "FootballDataProvider", Football)
    monkeypatch.setattr(ingestion, "SportyBetProvider", Sporty)
    monkeypatch.setattr(ingestion, "TransfermarktProvider", Transfermarkt)
    result=collect_sources(date(2026,11,22),"2627","E0")
    assert result["sources"]["livescore"]["status"]=="error"
    assert result["sources"]["football_data"]["status"]=="ok"
    assert result["sources"]["sportybet"]["role"]=="odds_only"
    assert result["sources"]["transfermarkt"]["role"]=="context_only"
    assert result["overall_status"]=="partial"
