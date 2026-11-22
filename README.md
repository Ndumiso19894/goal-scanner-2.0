# Goal Scanner Pro

Independent, evidence-led football analysis engine with an Android application.

## Android app
The Android client is a native Kotlin + Jetpack Compose app with adaptive light/dark mode, blue accent styling, Home, Fixtures, Scanner, Analysis, Results and Settings screens. It includes both Recommended and Full Scanner views, configurable market filters, a locally stored prediction history and a configurable connection to this repository's FastAPI service.

Build locally with Android Studio or run the **Android APK** GitHub Actions workflow. The debug APK is uploaded as a workflow artifact. The API URL is configured in the app; no public backend URL is assumed or bundled. Until a reachable API is configured and a fixture-level recommendation service is available, the app clearly shows connection/data status rather than inventing fixtures or predictions.

## Backend install
```bash
python -m venv .venv
. .venv/bin/activate
pip install -e ".[dev,api]"
pytest -q
uvicorn goal_scanner.api:app --host 0.0.0.0 --port 8000
```
Windows PowerShell: `.venv\\Scripts\\Activate.ps1`.

## Goal markets
The independent-Poisson baseline exposes probabilities for home/away team Over 0.5 and Over 1.5, match Over 1.5 and Over 2.5, 1X2, double chance (1X/X2/12) and BTTS Yes. The estimator requires positive home and away goal expectations. It does not yet provide a trained fixture-level lambda estimator or calibrated recommendations. Do not present its output as a validated betting edge.

Corner and booking market identifiers remain reserved; their market-specific calibrated models and event-data adapters are not implemented. Contextual features must not affect probabilities until chronological out-of-sample validation and ablation demonstrate improvement.

## Data providers
The four read-only Mr2Odds-compatible sources are integrated in `goal_scanner/providers.py`:
- LiveScore: fixtures, scores, incidents and published statistics.
- Football-Data.co.uk: historical results and team-level data.
- SportyBet ZA: odds only, strictly after model probabilities.
- Transfermarkt: squad context only, not a probability input.

See `docs/data-sources.md` for provenance, freshness and modelling boundaries. Providers are unofficial public feeds, may change or fail, and do not guarantee complete competition coverage. Missing data remains missing; no fabricated lineups, injuries, xG or odds.

## Safety and evaluation
Predictions should be saved as immutable timestamped snapshots. Live scanner performance must be measured separately from manually recorded bets and historical backtests. Settlement must use verified final scores, with a manual correction path. Track accuracy, Brier score, log loss and ROI only where the necessary outcomes and odds were recorded.

This is decision-support software, not a guarantee of profit. **NO BET is a valid result.**
