from dataclasses import dataclass
from datetime import datetime
from math import log
@dataclass(frozen=True)
class HistoricalPrediction:
    event_time: datetime
    information_cutoff: datetime
    probability: float
    odds: float
    outcome: int
def validate_chronology(rows: list[HistoricalPrediction]) -> None:
    for r in rows:
        if r.information_cutoff>r.event_time: raise ValueError("Look-ahead leakage: cutoff after kickoff")
        if not 0<=r.probability<=1 or r.odds<=1 or r.outcome not in (0,1): raise ValueError("Invalid row")
def evaluate(rows: list[HistoricalPrediction]) -> dict:
    validate_chronology(rows)
    if not rows: return {"n":0,"brier":None,"log_loss":None,"roi":None}
    eps=1e-9
    brier=sum((r.probability-r.outcome)**2 for r in rows)/len(rows)
    loss=-sum(r.outcome*log(max(eps,r.probability))+(1-r.outcome)*log(max(eps,1-r.probability)) for r in rows)/len(rows)
    profit=sum(r.odds-1 if r.outcome else -1 for r in rows)
    return {"n":len(rows),"brier":brier,"log_loss":loss,"roi":profit/len(rows)}
