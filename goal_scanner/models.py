from math import exp, factorial

from .markets import Market


def poisson_pmf(k: int, lam: float) -> float:
    if k < 0 or lam < 0:
        return 0.0
    return exp(-lam) * lam**k / factorial(k)


def poisson_cdf(k: int, lam: float) -> float:
    return sum(poisson_pmf(i, lam) for i in range(k + 1))


def poisson_over(threshold: int, lam: float) -> float:
    return max(0.0, min(1.0, 1.0 - poisson_cdf(threshold, lam)))


def goal_market_probabilities(home_lambda: float, away_lambda: float) -> dict[Market, float]:
    """Independent-Poisson baseline probabilities for the supported goal markets.

    These are model estimates, not calibrated guarantees. Inputs must come from a
    separately validated goal-expectation estimator; bookmaker odds are not inputs.
    """
    if not (0 < home_lambda <= 6 and 0 < away_lambda <= 6):
        raise ValueError("Goal expectations must be positive and no greater than 6")

    n = 20
    home = [poisson_pmf(i, home_lambda) for i in range(n)]
    away = [poisson_pmf(i, away_lambda) for i in range(n)]
    norm = sum(home) * sum(away)
    home_win = draw = away_win = over_25 = btts = 0.0

    for h, ph in enumerate(home):
        for a, pa in enumerate(away):
            probability = ph * pa
            if h > a:
                home_win += probability
            elif h == a:
                draw += probability
            else:
                away_win += probability
            if h + a >= 3:
                over_25 += probability
            if h > 0 and a > 0:
                btts += probability

    home_win /= norm
    draw /= norm
    away_win /= norm
    over_25 /= norm
    btts /= norm
    total_lambda = home_lambda + away_lambda

    return {
        Market.HOME_OVER_05: poisson_over(0, home_lambda),
        Market.AWAY_OVER_05: poisson_over(0, away_lambda),
        Market.HOME_OVER_15: poisson_over(1, home_lambda),
        Market.AWAY_OVER_15: poisson_over(1, away_lambda),
        Market.MATCH_OVER_15: poisson_over(1, total_lambda),
        Market.MATCH_OVER_25: over_25,
        Market.HOME_WIN: home_win,
        Market.DRAW: draw,
        Market.AWAY_WIN: away_win,
        Market.DOUBLE_CHANCE_1X: home_win + draw,
        Market.DOUBLE_CHANCE_X2: draw + away_win,
        Market.DOUBLE_CHANCE_12: home_win + away_win,
        Market.BTTS_YES: btts,
    }


def count_market_probability(lam: float, line: int) -> float:
    if lam <= 0 or line < 0:
        raise ValueError("Invalid count expectation or line")
    return poisson_over(line, lam)
