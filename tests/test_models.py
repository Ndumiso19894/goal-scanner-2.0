import pytest
from goal_scanner.models import poisson_pmf,goal_market_probabilities
from goal_scanner.markets import Market,validate_market
def test_poisson_pmf_sums_to_one(): assert sum(poisson_pmf(i,2.4) for i in range(20))==pytest.approx(1,abs=1e-6)
def test_goal_probabilities_are_valid():
    p=goal_market_probabilities(1.5,1.1)
    assert all(0<=x<=1 for x in p.values())
    assert p[Market.HOME_WIN]+p[Market.DRAW]+p[Market.AWAY_WIN]==pytest.approx(1,abs=1e-5)
def test_excluded_market_rejected():
    with pytest.raises(ValueError): validate_market("btts_no")
