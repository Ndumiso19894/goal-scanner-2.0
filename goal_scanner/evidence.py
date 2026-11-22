from dataclasses import dataclass
from datetime import datetime, timezone
from enum import StrEnum
class Verification(StrEnum):
    CONFIRMED="confirmed"; REPORTED="reported"; RUMOUR="rumour"; UNKNOWN="unknown"
@dataclass(frozen=True)
class Evidence:
    subject: str
    claim: str
    source_name: str
    source_url: str
    published_at: datetime|None
    retrieved_at: datetime
    verification: Verification
    confidence: float
    notes: str=""
    def __post_init__(self):
        if not 0<=self.confidence<=1: raise ValueError("confidence must be between 0 and 1")
        if not self.source_url.startswith(("https://","http://")): raise ValueError("Evidence requires URL")
        if self.retrieved_at.tzinfo is None: raise ValueError("retrieved_at must be timezone-aware")
    @property
    def is_usable_fact(self): return self.verification==Verification.CONFIRMED and self.confidence>=.7
    @property
    def age_hours(self): return (datetime.now(timezone.utc)-self.retrieved_at).total_seconds()/3600
