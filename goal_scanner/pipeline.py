"""Deterministic candidate evaluation; it does not fetch or invent source data."""
from dataclasses import asdict
from .decision import decide
from .models import goal_market_probabilities
from .quality import assess_quality
from .markets import Market
from .evidence import Evidence

def analyse_goal_markets(home_lambda: float, away_lambda: float, odds: dict[str,float],
                         evidence: list[Evidence], min_edge: float=.03) -> list[dict]:
    probs=goal_market_probabilities(home_lambda,away_lambda)
    quality=assess_quality(evidence)
    output=[]
    for market in (Market.MATCH_OVER_25,Market.BTTS_YES,Market.HOME_WIN,Market.DRAW,Market.AWAY_WIN):
        price=odds.get(market.value)
        if price is None: continue
        d=decide(market.value,probs[market],price,quality,min_edge)
        output.append({"market":market.value,"decision":d.label.value,"probability":round(d.probability,5),
                       "odds":d.odds,"expected_value":d.expected_value,"reasons":d.reasons,
                       "quality_score":quality.score})
    return output
