from dataclasses import dataclass, field
from enum import StrEnum
from .markets import Market, validate_market
from .quality import QualityReport
from .odds import expected_value
class DecisionLabel(StrEnum):
    APPROVED="APPROVED"; CONDITIONAL="CONDITIONAL"; REJECTED="REJECTED"; INSUFFICIENT_DATA="INSUFFICIENT_DATA"
@dataclass
class CandidateDecision:
    market: Market
    label: DecisionLabel
    probability: float
    odds: float
    expected_value: float|None
    reasons: list[str]=field(default_factory=list)
def decide(market: str, probability: float, odds: float, quality: QualityReport,
           min_edge: float=.03) -> CandidateDecision:
    selected=validate_market(market)
    if not 0<=probability<=1 or odds<=1: raise ValueError("Invalid probability or odds")
    if not quality.sufficient:
        return CandidateDecision(selected,DecisionLabel.INSUFFICIENT_DATA,probability,odds,None,quality.blockers+quality.warnings)
    ev=expected_value(probability,odds)
    if ev<min_edge:
        return CandidateDecision(selected,DecisionLabel.REJECTED,probability,odds,ev,["Expected value below required edge"])
    return CandidateDecision(selected,DecisionLabel.APPROVED,probability,odds,ev,quality.warnings)
