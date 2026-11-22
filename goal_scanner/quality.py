from dataclasses import dataclass, field
from datetime import datetime, timezone
from .evidence import Evidence
@dataclass
class QualityReport:
    score: float
    blockers: list[str]=field(default_factory=list)
    warnings: list[str]=field(default_factory=list)
    evidence_count: int=0
    @property
    def sufficient(self): return self.score>=.70 and not self.blockers
def assess_quality(evidence: list[Evidence], required_categories: set[str]|None=None,
                   max_age_hours: float=48) -> QualityReport:
    blockers=[]; warnings=[]
    usable=[e for e in evidence if e.is_usable_fact]
    categories={e.subject for e in usable}
    if not usable: blockers.append("No confirmed, sufficiently confident evidence")
    if required_categories:
        missing=required_categories-categories
        if missing: blockers.append("Missing evidence categories: "+", ".join(sorted(missing)))
    now=datetime.now(timezone.utc)
    stale=[e for e in usable if (now-e.retrieved_at).total_seconds()>max_age_hours*3600]
    if stale: warnings.append(f"{len(stale)} confirmed evidence item(s) are stale")
    uncertain=[e for e in evidence if e.verification in {e.verification.RUMOUR,e.verification.UNKNOWN,e.verification.REPORTED}]
    if uncertain: warnings.append(f"{len(uncertain)} unconfirmed item(s) excluded")
    freshness=max(0.0,1-len(stale)/len(usable)) if usable else 0
    confidence=sum(e.confidence for e in usable)/len(usable) if usable else 0
    coverage=min(1.0,len(categories)/max(1,len(required_categories or {"baseline"})))
    score=round(.4*confidence+.35*freshness+.25*coverage,3) if usable else 0
    return QualityReport(score,blockers,warnings,len(usable))
