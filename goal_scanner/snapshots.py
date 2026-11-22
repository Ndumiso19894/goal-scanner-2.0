from dataclasses import dataclass
from datetime import datetime, timezone
from enum import StrEnum
import hashlib,json
class Stage(StrEnum):
    T_MINUS_24H="T-24H"; TEAM_NEWS="TEAM_NEWS"; OFFICIAL_LINEUPS="OFFICIAL_LINEUPS"
@dataclass(frozen=True)
class PredictionSnapshot:
    fixture_id: str
    stage: Stage
    captured_at: datetime
    payload: dict
    digest: str
def make_snapshot(fixture_id: str, stage: Stage, payload: dict) -> PredictionSnapshot:
    captured=datetime.now(timezone.utc)
    serial=json.dumps({"fixture_id":fixture_id,"stage":stage.value,"captured_at":captured.isoformat(),
                       "payload":payload},sort_keys=True,default=str)
    return PredictionSnapshot(fixture_id,stage,captured,payload,hashlib.sha256(serial.encode()).hexdigest())
