from datetime import datetime,timedelta,timezone
import pytest
from goal_scanner.backtest import HistoricalPrediction,evaluate,validate_chronology
def test_metrics():
    now=datetime.now(timezone.utc)
    rows=[HistoricalPrediction(now,now-timedelta(hours=1),.7,2,1),HistoricalPrediction(now,now-timedelta(hours=1),.3,2,0)]
    assert evaluate(rows)["brier"]==pytest.approx(.09)
def test_future_information_rejected():
    now=datetime.now(timezone.utc)
    with pytest.raises(ValueError): validate_chronology([HistoricalPrediction(now,now+timedelta(seconds=1),.5,2,1)])
