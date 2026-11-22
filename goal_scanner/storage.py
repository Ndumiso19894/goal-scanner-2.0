"""SQLite persistence for fixtures, odds, evidence and immutable snapshots."""
import json, sqlite3
from pathlib import Path
from .snapshots import PredictionSnapshot

class Store:
    def __init__(self, path: str = "data/goal_scanner.sqlite3"):
        self.path=path
        Path(path).parent.mkdir(parents=True,exist_ok=True)
        with self.connect() as db:
            db.executescript("""
            CREATE TABLE IF NOT EXISTS fixtures(id TEXT PRIMARY KEY, kickoff TEXT NOT NULL, home TEXT NOT NULL, away TEXT NOT NULL, competition TEXT);
            CREATE TABLE IF NOT EXISTS evidence(id INTEGER PRIMARY KEY, fixture_id TEXT NOT NULL, subject TEXT NOT NULL, payload TEXT NOT NULL, retrieved_at TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS odds(id INTEGER PRIMARY KEY, fixture_id TEXT NOT NULL, market TEXT NOT NULL, selection TEXT NOT NULL, decimal_odds REAL NOT NULL, captured_at TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS snapshots(id INTEGER PRIMARY KEY, fixture_id TEXT NOT NULL, stage TEXT NOT NULL, captured_at TEXT NOT NULL, payload TEXT NOT NULL, digest TEXT NOT NULL UNIQUE);
            """)
    def connect(self):
        db=sqlite3.connect(self.path)
        db.row_factory=sqlite3.Row
        return db
    def save_fixture(self, fixture: dict):
        with self.connect() as db:
            db.execute("INSERT OR REPLACE INTO fixtures VALUES(?,?,?,?,?)",
                (fixture["id"],fixture["kickoff"],fixture["home"],fixture["away"],fixture.get("competition")))
    def save_snapshot(self, snapshot: PredictionSnapshot):
        with self.connect() as db:
            db.execute("INSERT INTO snapshots(fixture_id,stage,captured_at,payload,digest) VALUES(?,?,?,?,?)",
                (snapshot.fixture_id,snapshot.stage.value,snapshot.captured_at.isoformat(),
                 json.dumps(snapshot.payload,sort_keys=True,default=str),snapshot.digest))
    def list_fixtures(self):
        with self.connect() as db:
            return [dict(x) for x in db.execute("SELECT * FROM fixtures ORDER BY kickoff")]
