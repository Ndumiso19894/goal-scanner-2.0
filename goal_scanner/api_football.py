"""Optional API-Football v3 adapter for league coverage, player history and team news.

Set API_FOOTBALL_KEY in the environment. Check each league/season coverage before
requesting player data; provider coverage does not guarantee data for every fixture.
"""
from __future__ import annotations
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

from .providers import ProviderError

class ApiFootballProvider:
    BASE = "https://v3.football.api-sports.io"

    def __init__(self, api_key: str | None = None, timeout: int = 20, cache_seconds: int = 900):
        self.api_key = api_key or os.getenv("API_FOOTBALL_KEY")
        self.timeout = timeout
        self.cache_seconds = cache_seconds
        self._cache: dict[str, tuple[float, dict[str, Any]]] = {}
        if not self.api_key:
            raise ProviderError("API_FOOTBALL_KEY is not configured")

    def _get(self, endpoint: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        query = urllib.parse.urlencode({k:v for k,v in (params or {}).items() if v is not None})
        url = f"{self.BASE}/{endpoint.lstrip('/')}?{query}" if query else f"{self.BASE}/{endpoint.lstrip('/')}"
        cached = self._cache.get(url)
        if cached and time.time() - cached[0] < self.cache_seconds:
            return cached[1]
        request = urllib.request.Request(url, headers={"x-apisports-key":self.api_key,
            "Accept":"application/json","User-Agent":"GoalScanner/2.0"})
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ProviderError(f"API-Football request failed: {exc}") from exc
        if not isinstance(payload, dict):
            raise ProviderError("API-Football returned an unexpected response")
        errors = payload.get("errors")
        if errors:
            raise ProviderError(f"API-Football error: {errors}")
        self._cache[url] = (time.time(), payload)
        return payload

    def fetch_league_coverage(self, season: int) -> list[dict[str, Any]]:
        """Return every listed competition and its season-specific data coverage flags."""
        response = self._get("leagues", {"season":season})
        rows = []
        for item in response.get("response", []):
            league = item.get("league") or {}
            country = item.get("country") or {}
            seasons = item.get("seasons") or []
            season_info = next((s for s in seasons if s.get("year") == season), None)
            if not league or season_info is None:
                continue
            coverage = season_info.get("coverage") or {}
            fixtures = coverage.get("fixtures") or {}
            rows.append({
                "league_id":league.get("id"), "league":league.get("name"),
                "country":country.get("name"), "season":season,
                "coverage":{
                    "fixtures":bool(fixtures.get("events") or fixtures.get("lineups")),
                    "events":bool(fixtures.get("events")),
                    "lineups":bool(fixtures.get("lineups")),
                    "statistics":bool(fixtures.get("statistics")),
                    "players":bool(coverage.get("players")),
                    "injuries":bool(coverage.get("injuries")),
                    "predictions":bool(coverage.get("predictions")),
                },
            })
        return rows

    def fetch_fixtures(self, *, date: str | None = None, league_id: int | None = None,
                       season: int | None = None, team_id: int | None = None,
                       next_count: int | None = None) -> list[dict[str, Any]]:
        params = {"date":date,"league":league_id,"season":season,"team":team_id,"next":next_count}
        return self._get("fixtures", params).get("response", [])

    def fetch_team_player_statistics(self, *, team_id: int, league_id: int,
                                     season: int, page: int = 1) -> dict[str, Any]:
        """Player statistics including appearances/minutes; page through results when needed."""
        payload = self._get("players", {"team":team_id,"league":league_id,
                                        "season":season,"page":page})
        return {"players":payload.get("response",[]),
                "paging":payload.get("paging") or {},
                "results":payload.get("results",0)}

    def fetch_fixture_injuries(self, fixture_id: int) -> list[dict[str, Any]]:
        """Provider injury status is fixture-specific and may be unknown/incomplete."""
        return self._get("injuries", {"fixture":fixture_id}).get("response", [])

    def fetch_official_lineups(self, fixture_id: int) -> list[dict[str, Any]]:
        """Official lineups are a late validation source, never the early XI predictor."""
        return self._get("fixtures/lineups", {"fixture":fixture_id}).get("response", [])
