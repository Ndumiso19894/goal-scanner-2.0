from math import exp, factorial
from .markets import Market

def poisson_pmf(k: int, lam: float) -> float:
    if k < 0 or lam < 0: return 0.0
    return exp(-lam) * lam**k / factorial(k)

def poisson_cdf(k: int, lam: float) -> float:
    return sum(poisson_pmf(i, lam) for i in range(k + 1))

def poisson_over(threshold: int, lam: float) -> float:
    return max(0.0, min(1.0, 1.0 - poisson_cdf(threshold, lam)))

def goal_market_probabilities(home_lambda: float, away_lambda: float) -> dict[Market, float]:
    """Independent-Poisson goal estimates for the approved goal/result markets."""
    if not (0 < home_lambda <= 6 and 0 < away_lambda <= 6):
        raise ValueError("Goal expectations must be positive and no greater than 6")
    n = 20
    home = [poisson_pmf(i, home_lambda) for i in range(n)]
    away = [poisson_pmf(i, away_lambda) for i in range(n)]
    norm = sum(home) * sum(away)
    home_win = draw = away_win = over_25 = btts = 0.0
    for h, ph in enumerate(home):
        for a, pa in enumerate(away):
            p = ph * pa
            if h > a: home_win += p
            elif h == a: draw += p
            else: away_win += p
            if h + a >= 3: over_25 += p
            if h > 0 and a > 0: btts += p
    return {
        Market.MATCH_OVER_25: over_25 / norm,
        Market.HOME_WIN: home_win / norm,
        Market.DRAW: draw / norm,
        Market.AWAY_WIN: away_win / norm,
        Market.BTTS_YES: btts / norm,
    }

def count_market_probability(lam: float, line: float) -> float:
    """Probability of going over a half-goal/count line (e.g. 8.5)."""
    if lam <= 0 or line < 0:
        raise ValueError("Invalid count expectation or line")
    return poisson_over(int(line), lam)
