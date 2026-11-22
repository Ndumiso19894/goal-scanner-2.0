from datetime import datetime,timezone
from goal_scanner.evidence import Evidence,Verification
from goal_scanner.quality import assess_quality
from goal_scanner.decision import decide,DecisionLabel
def test_rumour_cannot_approve():
    e=Evidence("lineups","rumour","blog","https://example.org",None,datetime.now(timezone.utc),Verification.RUMOUR,.9)
    assert decide("match_over_2_5",.8,2,assess_quality([e])).label==DecisionLabel.INSUFFICIENT_DATA
