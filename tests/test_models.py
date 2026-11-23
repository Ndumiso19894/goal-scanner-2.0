import pytest
from goal_scanner.markets import Market, validate_market
from goal_scanner.models import goal_market_probabilities, poisson_pmf, count_market_probability

def test_poisson_pmf_sums_to_one():
    assert sum(poisson_pmf(i,2.4) for i in range(20)) == pytest.approx(1,abs=1e-6)

def test_approved_goal_probabilities_are_valid():
    p=goal_market_probabilities(1.5,1.1)
    assert all(0<=v<=1 for v in p.values())
    assert p[Market.HOME_WIN]+p[Market.DRAW]+p[Market.AWAY_WIN] == pytest.approx(1,abs=1e-5)
    assert set(p)=={Market.MATCH_OVER_25,Market.HOME_WIN,Market.DRAW,Market.AWAY_WIN,Market.BTTS_YES}

def test_count_market_probability_uses_half_line():
    assert count_market_probability(10,8.5)>count_market_probability(10,10.5)

def test_original_corner_and_booking_markets_are_supported():
    for name in ("team_corners_over","match_corners_over","match_bookings_over","team_bookings_over"):
        assert validate_market(name) in Market

def test_unwanted_markets_are_rejected():
    for name in ("btts_no","double_chance","home_over_0_5","match_over_1_5"):
        with pytest.raises(ValueError): validate_market(name)
