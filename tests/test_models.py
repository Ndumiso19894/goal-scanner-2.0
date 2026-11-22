import pytest

from goal_scanner.markets import Market, validate_market
from goal_scanner.models import goal_market_probabilities, poisson_pmf


def test_poisson_pmf_sums_to_one():
    assert sum(poisson_pmf(i, 2.4) for i in range(20)) == pytest.approx(1, abs=1e-6)


def test_goal_probabilities_are_valid():
    probabilities = goal_market_probabilities(1.5, 1.1)
    assert all(0 <= value <= 1 for value in probabilities.values())
    assert probabilities[Market.HOME_WIN] + probabilities[Market.DRAW] + probabilities[Market.AWAY_WIN] == pytest.approx(1, abs=1e-5)


def test_team_and_total_goal_markets_are_available():
    probabilities = goal_market_probabilities(1.5, 1.1)
    assert probabilities[Market.HOME_OVER_05] > probabilities[Market.HOME_OVER_15]
    assert probabilities[Market.AWAY_OVER_05] > probabilities[Market.AWAY_OVER_15]
    assert probabilities[Market.MATCH_OVER_15] > probabilities[Market.MATCH_OVER_25]


def test_double_chance_probabilities_are_combinations_of_1x2():
    probabilities = goal_market_probabilities(1.5, 1.1)
    assert probabilities[Market.DOUBLE_CHANCE_1X] == pytest.approx(
        probabilities[Market.HOME_WIN] + probabilities[Market.DRAW]
    )
    assert probabilities[Market.DOUBLE_CHANCE_X2] == pytest.approx(
        probabilities[Market.DRAW] + probabilities[Market.AWAY_WIN]
    )
    assert probabilities[Market.DOUBLE_CHANCE_12] == pytest.approx(
        probabilities[Market.HOME_WIN] + probabilities[Market.AWAY_WIN]
    )


def test_excluded_market_rejected():
    with pytest.raises(ValueError):
        validate_market("btts_no")
