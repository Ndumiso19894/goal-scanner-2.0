# Data source integrations

Goal Scanner 2.0 now has read-only adapters for the same four public data sources used by Mr2Odds.

| Provider | Data collected | Permitted use |
|---|---|---|
| LiveScore public app feed | Daily/stage fixtures, kickoff/status/score, half-time score, incidents and published match statistics | Fixture/live context; historical stats only after coverage validation |
| Football-Data.co.uk | Season CSVs and current fixture CSV; FT/HT results, xG/shots/corners/cards/referee where published | Historical goal form; optional features only after coverage checks |
| SportyBet ZA web-app feed | Upcoming fixtures and odds: 1X2, double chance, totals, BTTS, team totals, corners/cards when returned | Odds/value comparison only; never model probability inputs |
| Transfermarkt public API | Competition clubs, squad size, average age and estimated market value | Context only; excluded from probabilities pending validation |

## Usage

Import FootballDataProvider, LiveScoreProvider, SportyBetProvider and TransfermarktProvider from goal_scanner.providers.

Football-Data history:
- Call FootballDataProvider().fetch_season("2627", "E0") for the 2026/27 England Premier League CSV.
- Convert completed rows with FootballDataProvider.to_match_records(rows) for the existing MatchRecord history estimator.

LiveScore:
- Call LiveScoreProvider().fetch_day(date.today(), utc_offset_hours=2) for a day feed.
- Use fetch_incidents(event_id) and fetch_statistics(event_id) for event-level information.

SportyBet:
- Call SportyBetProvider("za").fetch_upcoming(hours_ahead=48).
- Use match_fixtures_to_odds(...) to match events conservatively by both teams and kickoff time.

Transfermarkt:
- Call TransfermarktProvider().fetch_division_squads("E0") for mapped competition squads.
- Use match_team(team_name, clubs) only when match confidence is sufficient.

## Operational and modelling rules

- These are public, unofficial web/app endpoints, not guaranteed developer APIs. They can change, rate-limit, block cloud IPs or have incomplete competition coverage.
- Transport/response failures raise ProviderError. A failed fetch is not a zero score, empty-stat record or proof that a market is unavailable.
- Missing fields remain null/None. Never replace missing xG, shots, corners, cards, possession, referee or squad fields with zero.
- Store retrieved_at, source identifiers and source URLs with ingested data. Cache with source-specific TTLs and expose coverage/freshness in the app.
- Fixture matching is conservative: both team names and kickoff proximity must agree. Ambiguous matches remain unmatched.
- SportyBet odds stay outside the feature matrix and lambda estimation path. They may be used after probabilities are computed for fair odds, margin removal and expected value.
- Transfermarkt metrics remain display/context-only until chronological out-of-sample testing and ablation demonstrate predictive improvement.
- LiveScore statistics are not available for every competition/match. Build per-competition coverage reports before enabling corners/cards models.
- FootballDataProvider.to_match_records returns completed matches only and uses date ordinals for chronological ordering.
