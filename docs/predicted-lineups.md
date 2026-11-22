# Predicted lineups across competitions

Goal Scanner's lineup prediction engine is competition-agnostic. It can process any competition for which the project can obtain usable player and appearance records. There is no hard-coded shortlist of leagues.

## Prediction policy

- Generate an estimated starting XI before official team sheets are published.
- Start with recent starts and minutes, then adjust for availability reports and recovery time.
- Prefer a team's recent formation, but fall back to another supported shape if the available player pool cannot fill it.
- Treat questionable players as uncertain; do not automatically mark them absent.
- Exclude players only when the supplied data explicitly reports them missing or suspended.
- Return an insufficient-data status instead of fabricating an XI when roster or appearance evidence is too thin.
- Keep model-generated and official lineups separate. Official lineups are late validation data, not a prerequisite for early predictions.

## No-key data policy

- The lineup model itself requires no API key and has no paid-service dependency.
- Existing Mr2Odds sources remain separate and unchanged. Do not assume they provide player-level appearances, injuries or predicted lineups.
- Existing public match/result feeds can provide fixtures and team-level history, but they do not automatically contain enough player-level evidence to select an XI.
- Only use additional public sources that can be accessed without a private API key, subscription or login. Record the source and retrieval time, cache results, and respect each site's access rules.
- If a competition has no dependable public player data, report coverage as unavailable and do not invent an XI.

## Current implementation boundary

- `goal_scanner/lineups.py` contains the provider-neutral prediction engine.
- `POST /lineups/predict` accepts normalized player profiles and recent appearances.
- The Android app is not changed by this work.
- Automatic discovery and ingestion of player-level data from free public sources is a separate integration task. The current Mr2Odds feeds do not yet supply that complete dataset.

## Evaluation

Store a timestamped snapshot for every prediction, including fixture, source timestamps, predicted formation, XI, player scores and data-coverage status. Compare each snapshot only with the later official team sheet. Report exact XI hit rate, player-level precision/recall and formation accuracy by competition and prediction lead time. Do not use official lineups or post-kickoff data to reconstruct earlier predictions.
