# Public player-data source assessment

Assessment date: 2026-11-22

## Decision

Do not add a player-data provider to the production ingestion pipeline yet. No reviewed source has been verified to satisfy all of these requirements at once:

- no API key, account, private login or subscription;
- player-level match appearances and starting lineups;
- broad coverage across all competitions supported by Goal Scanner;
- dependable current-season updates;
- permission compatible with redistribution and possible future commercial use.

The lineup engine remains provider-neutral. It must return `insufficient_data` when evidence is missing.

## Sources reviewed

| Source | What it provides | Access / licence concern | Decision |
|---|---|---|---|
| StatsBomb Open Data | Historical match, event and lineup JSON for selected competitions | Public research dataset with attribution requirements; coverage is curated and not a complete live feed. Confirm permitted product use before distribution. | Candidate for offline evaluation only; not a live provider |
| 11footy dataset | Lineups, player match stats and absences across a limited set of competitions; rebuilt after rounds | CC BY-NC 4.0; non-commercial only. | Do not use in the production app or any monetised service |
| openplay | Historical lineups, player-match stats and event data for selected competitions | CC BY 4.0 is stated, but the dataset points to WhoScored match pages and the underlying data rights are not clearly resolved. Snapshot only, not live. | Do not ingest or redistribute until upstream rights are clarified |
| Open-Matchday | Daily fixtures, results and team-level match statistics across many competitions | Its published terms restrict commercial use; it explicitly has no lineups, injuries or player data. | Not suitable for player selection; do not use as a commercial data feed |
| OpenFootball | Public fixtures and results in text/JSON formats | No player-level appearances or lineups. | Fixtures/results fallback only, subject to source freshness |

## Product rules

1. Do not introduce API-Football, paid endpoints, hidden API keys or login-gated scraping.
2. Do not scrape sites whose access rules prohibit automated collection.
3. Do not treat a public webpage or downloadable file as automatically licensed for commercial reuse.
4. Keep source provenance, retrieval timestamp, competition coverage and data freshness with every imported record.
5. Never convert missing injury information into an assertion that a player is fit or unavailable.
6. Use historical open datasets for testing only when their terms allow the intended use, and keep those records separate from live prediction inputs.
7. A competition can be supported by the prediction algorithm while its player-data coverage remains unavailable. Display those as separate statuses.

## Next provider gate

A source can be promoted to a production adapter only after its official documentation or rights holder confirms:
- public access without credentials;
- permitted automated access and caching;
- player-level appearance/lineup fields and update cadence;
- competition-by-competition coverage;
- redistribution and commercial-use rights compatible with the app's intended future.

Until then, retain the existing Mr2Odds integrations unchanged and use the lineup engine only when trusted normalized player records are supplied.
