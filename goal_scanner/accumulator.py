from dataclasses import dataclass
from .decision import CandidateDecision, DecisionLabel
@dataclass
class Accumulator:
    legs: list[CandidateDecision]
    combined_odds: float
    status: str
    reason: str
def build_accumulator(legs: list[CandidateDecision]) -> Accumulator:
    if not 2<=len(legs)<=3: return Accumulator(legs,0,"NO_BET","Use two legs, maximum three")
    if any(x.label!=DecisionLabel.APPROVED for x in legs):
        return Accumulator(legs,0,"NO_BET","Every leg must be independently approved")
    return Accumulator(legs,0,"REVIEW_REQUIRED",
        "Fixture identity and joint dependence must be checked before pricing; marginals are not independent")
