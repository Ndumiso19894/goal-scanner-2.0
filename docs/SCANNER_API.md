# Fixture scanner and results API

The Android client uses these endpoints. Install the optional API dependencies with `pip install -e ".[api,dev]"`, then run:

```bash
GOAL_SCANNER_DB=data/goal_scanner.sqlite3 uvicorn goal_scanner.api:app --host 0.0.0.0 --port 8000
```

Use a reachable HTTPS reverse proxy for remote/mobile access. Do not expose an unauthenticated development server directly to the public internet.

## Run the scanner

`POST /scanner/run`

Example:

```json
{
  "match_date": "2026-11-22",
  "season": "2627",
  "division": "E0",
  "hours_ahead": 24,
  "minimum_probability": 0.60,
  "markets": [
    "home_over_0_5",
    "away_over_0_5",
    "home_over_1_5",
    "away_over_1_5",
    "match_over_1_5",
    "match_over_2_5",
    "match_winner",
    "double_chance",
    "btts_yes"
  ],
  "mode": "recommended"
}
```

Optional fields: `min_odds`, `max_odds`, `competition_query`, and `team_query`. The recommended mode returns the ten highest balanced rank scores; full mode returns every candidate that passes the selected market, probability and optional odds filters. Only returned recommendations are written as immutable prediction records.

## Model boundaries

- LiveScore supplies upcoming fixtures and later full-time scores.
- Football-Data.co.uk completed results supply historical goal rates.
- The estimator uses home-team home matches and away-team away matches, requires at least four of each and at least twelve prior completed league matches, and excludes all matches on or after the fixture's calendar date.
- Venue averages are shrunk towards league scoring averages using three prior-match equivalents.
- The independent-Poisson market model calculates the nine selected market families and their individual outcomes.
- SportyBet prices are attached after probabilities are computed. Odds never enter the goal-expectation calculation.
- The initial venue-rate model is transparent but uncalibrated. Its probabilities must not be described as validated probabilities or guaranteed outcomes.
- Fixtures with insufficient historical samples are returned under `skipped`, not assigned made-up goal expectations.

## Prediction history and settlement

- `GET /predictions?limit=100&status=PENDING` returns saved snapshots.
- `GET /results` returns settled-record performance and recent history.
- `POST /results/refresh` checks the previous and current local-day LiveScore boards. Only full-time statuses with both final scores settle pending predictions.
- Settlement is idempotent: a prediction already settled is not silently rewritten by another scoreboard refresh.
- Brier score is calculated from saved probabilities and settled binary outcomes.
- Flat-unit ROI is calculated only for settled predictions with recorded decimal odds. It is a record-keeping metric, not a staking recommendation; overlapping markets can be correlated.

## Database

SQLite defaults to `data/goal_scanner.sqlite3`. Set `GOAL_SCANNER_DB` to choose a persistent database path on the server. Back up this file; it contains the prediction history and settlement records.
