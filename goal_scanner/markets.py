from enum import StrEnum


class Market(StrEnum):
    HOME_OVER_05 = "home_over_0_5"
    AWAY_OVER_05 = "away_over_0_5"
    HOME_OVER_15 = "home_over_1_5"
    AWAY_OVER_15 = "away_over_1_5"
    MATCH_OVER_15 = "match_over_1_5"
    MATCH_OVER_25 = "match_over_2_5"
    HOME_WIN = "home_win"
    DRAW = "draw"
    AWAY_WIN = "away_win"
    DOUBLE_CHANCE_1X = "double_chance_1x"
    DOUBLE_CHANCE_X2 = "double_chance_x2"
    DOUBLE_CHANCE_12 = "double_chance_12"
    BTTS_YES = "btts_yes"
    TEAM_CORNERS_OVER = "team_corners_over"
    MATCH_CORNERS_OVER = "match_corners_over"
    MATCH_BOOKINGS_OVER = "match_bookings_over"
    TEAM_BOOKINGS_OVER = "team_bookings_over"


ALLOWED_MARKETS = frozenset(Market)
EXCLUDED_MARKETS = frozenset({"match_under", "team_under", "btts_no"})


def validate_market(market: str) -> Market:
    if market in EXCLUDED_MARKETS:
        raise ValueError(f"Market is explicitly excluded: {market}")
    try:
        return Market(market)
    except ValueError as exc:
        raise ValueError(f"Unsupported market: {market}") from exc
