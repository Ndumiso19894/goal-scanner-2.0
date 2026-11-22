from datetime import datetime,timezone
from goal_scanner.storage import Store
from goal_scanner.snapshots import make_snapshot,Stage
def test_fixture_and_snapshot_persistence(tmp_path):
    store=Store(str(tmp_path/"data.sqlite3"))
    store.save_fixture({"id":"f1","kickoff":"2026-12-01T18:00:00Z","home":"A","away":"B"})
    snap=make_snapshot("f1",Stage.T_MINUS_24H,{"p":.6})
    store.save_snapshot(snap)
    assert store.list_fixtures()[0]["id"]=="f1"
