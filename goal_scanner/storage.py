"""SQLite persistence for fixtures, immutable prediction records and settlements."""
from __future__ import annotations

import json
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .snapshots import PredictionSnapshot

SUPPORTED_MARKETS = {
    "match_over_2_5", "btts_yes", "match_winner",
    "team_corners_over", "match_corners_over",
    "team_bookings_over", "match_bookings_over",
}


def _outcome(market: str, selection: str, home: int, away: int,
            line: float | None = None, statistics: dict[str, Any] | None = None) -> bool | None:
    total = home + away
    if market == "match_over_2_5": return total >= 3
    if market == "btts_yes": return home >= 1 and away >= 1
    if market == "match_winner":
        actual = "home" if home > away else "away" if away > home else "draw"
        return selection == actual
    if market in {"team_corners_over", "match_corners_over", "team_bookings_over", "match_bookings_over"}:
        if line is None or not isinstance(statistics, dict):
            return None
        home_stats = statistics.get("home") or {}
        away_stats = statistics.get("away") or {}
        if market in {"team_corners_over", "match_corners_over"}:
            home_count = home_stats.get("corners")
            away_count = away_stats.get("corners")
        else:
            home_yellow, home_red = home_stats.get("yellow_cards"), home_stats.get("red_cards")
            away_yellow, away_red = away_stats.get("yellow_cards"), away_stats.get("red_cards")
            if None in (home_yellow, home_red, away_yellow, away_red):
                return None
            home_count = int(home_yellow) + int(home_red)
            away_count = int(away_yellow) + int(away_red)
        if home_count is None or away_count is None:
            return None
        if market.startswith("team_"):
            count = home_count if selection == "home" else away_count if selection == "away" else None
            return None if count is None else float(count) > line
        return float(home_count + away_count) > line
    return None


class Store:
    def __init__(self, path: str = "data/goal_scanner.sqlite3"):
        self.path = path
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.executescript("""
            CREATE TABLE IF NOT EXISTS fixtures(
                id TEXT PRIMARY KEY, kickoff TEXT NOT NULL, home TEXT NOT NULL,
                away TEXT NOT NULL, competition TEXT
            );
            CREATE TABLE IF NOT EXISTS evidence(
                id INTEGER PRIMARY KEY, fixture_id TEXT NOT NULL, subject TEXT NOT NULL,
                payload TEXT NOT NULL, retrieved_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS odds(
                id INTEGER PRIMARY KEY, fixture_id TEXT NOT NULL, market TEXT NOT NULL,
                selection TEXT NOT NULL, decimal_odds REAL NOT NULL, captured_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS snapshots(
                id INTEGER PRIMARY KEY, fixture_id TEXT NOT NULL, stage TEXT NOT NULL,
                captured_at TEXT NOT NULL, payload TEXT NOT NULL, digest TEXT NOT NULL UNIQUE
            );
            CREATE TABLE IF NOT EXISTS predictions(
                id TEXT PRIMARY KEY, fixture_id TEXT NOT NULL, kickoff TEXT NOT NULL,
                home TEXT NOT NULL, away TEXT NOT NULL, competition TEXT NOT NULL DEFAULT '',
                market TEXT NOT NULL, selection TEXT NOT NULL,
                probability REAL NOT NULL, odds REAL, expected_value REAL,
                rank_score REAL NOT NULL, data_quality REAL NOT NULL,
                model_version TEXT NOT NULL, calibration TEXT NOT NULL,
                payload TEXT NOT NULL, created_at TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                settled_at TEXT, final_home_goals INTEGER, final_away_goals INTEGER,
                settlement_source TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_predictions_fixture ON predictions(fixture_id);
            CREATE INDEX IF NOT EXISTS idx_predictions_status ON predictions(status);
            """)
    def connect(self):
        db = sqlite3.connect(self.path, timeout=20)
        db.row_factory = sqlite3.Row
        return db

    def save_fixture(self, fixture: dict):
        fixture_id = str(fixture.get("id") or fixture.get("source_id") or "")
        if not fixture_id:
            return
        with self.connect() as db:
            db.execute("INSERT OR REPLACE INTO fixtures VALUES(?,?,?,?,?)",
                (fixture_id, fixture["kickoff"], fixture["home"], fixture["away"], fixture.get("competition")))

    def save_snapshot(self, snapshot: PredictionSnapshot):
        with self.connect() as db:
            db.execute("INSERT INTO snapshots(fixture_id,stage,captured_at,payload,digest) VALUES(?,?,?,?,?)",
                (snapshot.fixture_id, snapshot.stage.value, snapshot.captured_at.isoformat(),
                 json.dumps(snapshot.payload, sort_keys=True, default=str), snapshot.digest))

    def save_odds_snapshot(self, fixture_id: str, odds: dict[str, Any], captured_at: str | None = None) -> int:
        """Persist each available decimal price as an immutable time-series sample."""
        if not fixture_id or not isinstance(odds, dict):
            return 0
        timestamp = captured_at or datetime.now(timezone.utc).isoformat()
        rows = []
        def walk(value: Any, path: list[str]) -> None:
            if isinstance(value, dict):
                for key, child in value.items():
                    walk(child, path + [str(key)])
            elif isinstance(value, (int, float)) and not isinstance(value, bool) and value > 1:
                market = path[0] if path else "unknown"
                selection = "/".join(path[1:]) or "price"
                rows.append((str(fixture_id), market, selection, float(value), timestamp))
        walk(odds, [])
        if rows:
            with self.connect() as db:
                db.executemany(
                    "INSERT INTO odds(fixture_id,market,selection,decimal_odds,captured_at) VALUES(?,?,?,?,?)",
                    rows,
                )
        return len(rows)

    def list_dropping_odds(self, minimum_drop_pct: float = 3.0, lookback_hours: int = 24,
                           limit: int = 200) -> list[dict[str, Any]]:
        """Return genuine price shortenings with at least two observations."""
        from datetime import timedelta
        cutoff = (datetime.now(timezone.utc) - timedelta(hours=max(1, lookback_hours))).isoformat()
        with self.connect() as db:
            rows = db.execute("""
                SELECT o.fixture_id,o.market,o.selection,o.decimal_odds,o.captured_at,
                       f.kickoff,f.home,f.away,f.competition
                FROM odds o LEFT JOIN fixtures f ON f.id=o.fixture_id
                WHERE o.captured_at >= ?
                ORDER BY o.fixture_id,o.market,o.selection,o.captured_at
            """, (cutoff,)).fetchall()
        groups: dict[tuple[str, str, str], list[Any]] = {}
        for row in rows:
            key = (row["fixture_id"], row["market"], row["selection"])
            groups.setdefault(key, []).append(row)
        movements = []
        for samples in groups.values():
            if len(samples) < 2:
                continue
            first, latest = samples[0], samples[-1]
            opening, current = float(first["decimal_odds"]), float(latest["decimal_odds"])
            if opening <= current:
                continue
            drop = (opening - current) / opening * 100.0
            if drop < minimum_drop_pct:
                continue
            movements.append({
                "fixture_id": latest["fixture_id"], "home": latest["home"] or "",
                "away": latest["away"] or "", "kickoff": latest["kickoff"] or "",
                "competition": latest["competition"] or "", "market": latest["market"],
                "selection": latest["selection"], "opening_odds": round(opening, 3),
                "current_odds": round(current, 3), "drop_pct": round(drop, 2),
                "first_seen": first["captured_at"], "last_seen": latest["captured_at"],
                "samples": len(samples),
            })
        movements.sort(key=lambda item: (item["drop_pct"], item["samples"]), reverse=True)
        return movements[:max(1, min(int(limit), 500))]

    def save_prediction(self, candidate: dict[str, Any]) -> str:
        prediction_id = uuid.uuid4().hex
        created_at = datetime.now(timezone.utc).isoformat()
        payload = json.dumps(candidate, sort_keys=True, separators=(",", ":"), default=str)
        with self.connect() as db:
            db.execute("""
                INSERT INTO predictions(
                    id,fixture_id,kickoff,home,away,competition,market,selection,
                    probability,odds,expected_value,rank_score,data_quality,
                    model_version,calibration,payload,created_at
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, (
                prediction_id, candidate["fixture_id"], candidate["kickoff"],
                candidate["home"], candidate["away"], candidate.get("competition", ""),
                candidate["market"], candidate["selection"], candidate["estimated_probability"],
                candidate.get("odds"), candidate.get("expected_value"), candidate["rank_score"],
                candidate["data_quality"], candidate["model_version"], candidate["calibration"],
                payload, created_at,
            ))
        return prediction_id

    def settle_fixture(self, fixture_id: str, home_goals: int, away_goals: int,
                       status: str, source: str = "livescore",
                       statistics: dict[str, Any] | None = None) -> int:
        if not fixture_id or home_goals < 0 or away_goals < 0:
            return 0
        if str(status).strip().upper() not in {"FT", "AET", "PEN", "FINISHED", "FULL TIME"}:
            return 0
        settled_at = datetime.now(timezone.utc).isoformat()
        updated = 0
        with self.connect() as db:
            rows = db.execute(
                "SELECT id,market,selection,payload FROM predictions WHERE fixture_id=? AND status='PENDING'",
                (str(fixture_id),)
            ).fetchall()
            for row in rows:
                try:
                    payload = json.loads(row["payload"] or "{}")
                    line = float(payload["line"]) if payload.get("line") is not None else None
                except (TypeError, ValueError, KeyError, json.JSONDecodeError):
                    line = None
                won = _outcome(row["market"], row["selection"], home_goals, away_goals,
                               line=line, statistics=statistics)
                if won is None:
                    continue
                db.execute("""
                    UPDATE predictions SET status=?,settled_at=?,final_home_goals=?,
                        final_away_goals=?,settlement_source=? WHERE id=? AND status='PENDING'
                """, ("WON" if won else "LOST", settled_at, home_goals, away_goals, source, row["id"]))
                updated += 1
        return updated

    def settle_many(self, fixtures: list[dict[str, Any]]) -> int:
        total = 0
        for fixture in fixtures:
            if fixture.get("home_score") is None or fixture.get("away_score") is None:
                continue
            fixture_id = str(fixture.get("source_id") or fixture.get("id") or "")
            total += self.settle_fixture(
                fixture_id, int(fixture["home_score"]), int(fixture["away_score"]),
                str(fixture.get("status") or ""), str(fixture.get("source") or "livescore")
            )
        return total

    def list_predictions(self, limit: int = 100, status: str | None = None) -> list[dict[str, Any]]:
        limit = max(1, min(int(limit), 500))
        placeholders = ",".join("?" for _ in SUPPORTED_MARKETS)
        allowed = tuple(sorted(SUPPORTED_MARKETS))
        with self.connect() as db:
            if status:
                rows = db.execute(
                    f"SELECT * FROM predictions WHERE status=? AND market IN ({placeholders}) ORDER BY created_at DESC LIMIT ?",
                    (status.upper(), *allowed, limit)
                ).fetchall()
            else:
                rows = db.execute(
                    f"SELECT * FROM predictions WHERE market IN ({placeholders}) ORDER BY created_at DESC LIMIT ?",
                    (*allowed, limit)
                ).fetchall()
        result = []
        for row in rows:
            item = dict(row)
            item["payload"] = json.loads(item["payload"])
            result.append(item)
        return result

    def results_summary(self) -> dict[str, Any]:
        with self.connect() as db:
            placeholders = ",".join("?" for _ in SUPPORTED_MARKETS)
            rows = db.execute(
                f"SELECT * FROM predictions WHERE market IN ({placeholders}) ORDER BY created_at",
                tuple(sorted(SUPPORTED_MARKETS))
            ).fetchall()
        settled = [row for row in rows if row["status"] in {"WON", "LOST"}]
        wins = sum(row["status"] == "WON" for row in settled)
        losses = len(settled) - wins
        brier = sum((row["probability"] - (1.0 if row["status"] == "WON" else 0.0)) ** 2
                    for row in settled) / len(settled) if settled else None
        priced = [row for row in settled if row["odds"] is not None]
        profit = sum((row["odds"] - 1.0) if row["status"] == "WON" else -1.0 for row in priced)
        return {
            "predictions": len(rows), "pending": sum(row["status"] == "PENDING" for row in rows),
            "settled": len(settled), "wins": wins, "losses": losses,
            "accuracy": wins / len(settled) if settled else None,
            "brier_score": brier,
            "priced_settled": len(priced),
            "profit_units": round(profit, 4) if priced else None,
            "roi": round(profit / len(priced), 6) if priced else None,
            "note": "ROI uses one flat unit per settled prediction with recorded odds; overlapping markets are not independent bets."
        }

    def list_fixtures(self):
        with self.connect() as db:
            return [dict(x) for x in db.execute("SELECT * FROM fixtures ORDER BY kickoff")]
