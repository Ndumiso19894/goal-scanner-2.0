"""Simple recency-weighted team scoring-rate estimator; baseline only."""
from dataclasses import dataclass
from math import exp
@dataclass(frozen=True)
class MatchRecord:
    date_index: int
    home: str
    away: str
    home_goals: int
    away_goals: int

def estimate_team_rates(matches: list[MatchRecord], team: str, as_of_index: int,
                        decay: float=.03, prior_rate: float=1.25) -> dict[str,float]:
    rows=[m for m in matches if m.date_index<as_of_index and team in (m.home,m.away)]
    if not rows: return {"attack":prior_rate,"concession":prior_rate,"matches":0}
    weights=[exp(-decay*(as_of_index-m.date_index)) for m in rows]
    scored=[]; conceded=[]
    for m,w in zip(rows,weights):
        scored.append((m.home_goals if m.home==team else m.away_goals,w))
        conceded.append((m.away_goals if m.home==team else m.home_goals,w))
    total=sum(weights)
    return {"attack":sum(x*w for x,w in scored)/total,
            "concession":sum(x*w for x,w in conceded)/total,"matches":len(rows)}

def expected_goals(home_attack: float, home_concession: float,
                   away_attack: float, away_concession: float,
                   league_home: float=1.45, league_away: float=1.15) -> tuple[float,float]:
    if min(home_attack,home_concession,away_attack,away_concession,league_home,league_away)<=0:
        raise ValueError("Rates must be positive")
    home=(home_attack/league_home)*(away_concession/league_home)*league_home
    away=(away_attack/league_away)*(home_concession/league_away)*league_away
    return max(.05,home),max(.05,away)
