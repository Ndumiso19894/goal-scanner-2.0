# Predicted lineups across competitions

Goal Scanner can evaluate every competition returned by a configured player-data provider. Competition selection is not hard-coded to a short list. Data coverage is checked per competition and season before player-level requests are made.

## Prediction policy

- Generate an estimated starting XI before official team sheets are published.
- Start with recent starts and minutes, then adjust for availability reports and recovery time.
- Prefer a team's recent formation, but fall back to another supported shape if the available player pool cannot fill it.
- Treat questionable players as uncertain and penalise their selection score; do not automatically mark them absent.
- Exclude players explicitly reported missing or suspended.
- Return an insufficient-data status instead of fabricating an XI when roster or appearance evidence is too thin.
- Keep model-generated and official lineups separate. Official lineups are late validation data, not inputs to an earlier prediction.

## Current components

- goal_scanner/lineups.py contains the provider-neutral prediction engine.
- goal_scanner/api_football.py provides an optional API-Football v3 adapter for all-competition season coverage, fixtures, player statistics, injuries and official lineups.
- Set API_FOOTBALL_KEY in the server environment to enable API-Football requests. The key must not be placed in the Android app or committed to the repository.
- Coverage flags are only a provider-level signal. A competition marked as covered can still have missing player records or no useful appearance history for a particular team or fixture.

## Important limitation

The prediction engine consumes normalized player appearance histories. The API-Football adapter currently exposes season player statistics and official/availability endpoints, but a production ingestion job still needs to assemble recent match-by-match appearances for each team. Do not treat season totals alone as a substitute for recent starting-XI history.

## Evaluation

Store a timestamped snapshot for every prediction, including the fixture, source timestamps, predicted formation, XI, player scores and data-coverage status. Compare each snapshot only with the later official team sheet. Report exact XI hit rate, player-level precision/recall and formation accuracy by competition and prediction lead time. Do not use official lineups or post-kickoff data to reconstruct earlier predictions.
