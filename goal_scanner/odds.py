def remove_margin(odds: dict[str,float]) -> dict[str,float]:
    if len(odds)<2 or any(v<=1 for v in odds.values()): raise ValueError("Complete market odds > 1 required")
    raw={k:1/v for k,v in odds.items()}; total=sum(raw.values())
    return {k:v/total for k,v in raw.items()}
def expected_value(probability: float, decimal_odds: float) -> float:
    if not 0<=probability<=1 or decimal_odds<=1: raise ValueError("Invalid probability or odds")
    return probability*decimal_odds-1
