from enum import StrEnum
class Market(StrEnum):
    MATCH_OVER_25 = "match_over_2_5"
    BTTS_YES = "btts_yes"
    HOME_WIN = "home_win"
    DRAW = "draw"
    AWAY_WIN = "away_win"
    TEAM_CORNERS_OVER = "team_corners_over"
    MATCH_CORNERS_OVER = "match_corners_over"
    MATCH_BOOKINGS_OVER = "match_bookings_over"
    TEAM_BOOKINGS_OVER = "team_bookings_over"
ALLOWED_MARKETS = frozenset(Market)
EXCLUDED_MARKETS = frozenset({"match_under","team_under","match_over_1_5","team_over_0_5","team_over_1_5","btts_no"})
def validate_market(market: str) -> Market:
    if market in EXCLUDED_MARKETS: raise ValueError(f"Market is explicitly excluded: {market}")
    try: return Market(market)
    except ValueError as exc: raise ValueError(f"Unsupported market: {market}") from exc
