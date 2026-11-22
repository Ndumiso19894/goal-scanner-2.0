from math import exp, factorial
from .markets import Market
def poisson_pmf(k: int, lam: float) -> float:
    if k < 0 or lam < 0: return 0.0
    return exp(-lam) * lam**k / factorial(k)
def poisson_cdf(k: int, lam: float) -> float:
    return sum(poisson_pmf(i, lam) for i in range(k+1))
def poisson_over(threshold: int, lam: float) -> float:
    return max(0.0, min(1.0, 1.0-poisson_cdf(threshold,lam)))
def goal_market_probabilities(home_lambda: float, away_lambda: float) -> dict[Market,float]:
    if home_lambda <= 0 or away_lambda <= 0: raise ValueError("Goal expectations must be positive")
    n=15
    home=[poisson_pmf(i,home_lambda) for i in range(n)]
    away=[poisson_pmf(i,away_lambda) for i in range(n)]
    norm=sum(home)*sum(away)
    hw=dr=aw=o25=btts=0.0
    for h,ph in enumerate(home):
        for a,pa in enumerate(away):
            p=ph*pa
            if h>a: hw+=p
            elif h==a: dr+=p
            else: aw+=p
            if h+a>=3: o25+=p
            if h>0 and a>0: btts+=p
    return {Market.HOME_WIN:hw/norm,Market.DRAW:dr/norm,Market.AWAY_WIN:aw/norm,
            Market.MATCH_OVER_25:o25/norm,Market.BTTS_YES:btts/norm}
def count_market_probability(lam: float, line: int) -> float:
    if lam<=0 or line<0: raise ValueError("Invalid count expectation or line")
    return poisson_over(line,lam)
