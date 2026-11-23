# Goal Scanner Pro

Independent, evidence-led football analysis engine with an Android application.

## How the app receives data

Goal Scanner Pro uses a **GitHub-only static data feed**. It does not require a separately hosted API server, database service or paid hosting account.

GitHub Actions runs the scanner, refreshes verified scores and publishes three public JSON snapshots:

- `docs/data/latest.json` — upcoming selections and their analysis.
- `docs/data/fixtures.json` — upcoming fixtures.
- `docs/data/results.json` — prediction history and settled performance.

The Android app downloads these files directly from this repository. The publisher is configured for a four-hour schedule and can also run after changes to the scanner branch or by manual workflow dispatch. Scheduled GitHub Actions workflows become active after this workflow is merged into the repository's default branch. Until then, the existing published snapshot can be read, but automatic scheduled refresh is not yet active.

The repository and published JSON are public. Do not put private information, credentials or API tokens in the feeds.

## Android app

The native Kotlin + Jetpack Compose app includes Home, Fixtures, Scanner, Analysis, Results and Settings screens, with adaptive light/dark styling. It reads the GitHub JSON feed; the Scanner screen filters the latest published scan on the device. It does not launch a new remote scan on demand.

Build locally with Android Studio or run the **Android APK** GitHub Actions workflow. The debug APK is uploaded as a workflow artifact.

## Scanner and markets

The fixture-level scanner estimates home and away expected goals from completed Football-Data.co.uk results using venue splits and a shrinkage baseline. It supports:

- Home team Over 0.5 and Over 1.5 goals
- Away team Over 0.5 and Over 1.5 goals
- Match Over 1.5 and Over 2.5 goals
- Match winner, double chance and BTTS Yes

The initial minimum estimated probability is 60%. Recommended mode ranks a shortlist using estimated probability, sample quality and available price value. Full mode publishes qualifying selections from the supported competitions.

The initial fixture model is an **uncalibrated historical-rate baseline**, not a validated betting edge. It requires at least four prior home matches for the home team, four prior away matches for the away team and twelve prior completed league matches. Same-day and future results are excluded. Insufficient samples produce no recommendation. Probabilities are not guarantees.

Bookmaker prices from SportyBet are attached after probability calculation and are not model inputs. Missing odds remain missing.

## Data providers

- LiveScore: fixtures, scores, incidents and available match statistics.
- Football-Data.co.uk: historical results and available team-level data.
- SportyBet ZA: odds for comparison only.
- Transfermarkt: squad context only, excluded from probability calculation.

These are unofficial public feeds and may change, be rate-limited or become unavailable. Missing data remains missing; the scanner does not fabricate lineups, injuries, xG or odds.

## Evaluation

Predictions are stored as timestamped snapshots in the GitHub Actions cache and published as public results data. Settlement uses recognised full-time statuses and final scores from LiveScore. Track accuracy, Brier score and ROI only where the required outcomes and recorded odds exist. Overlapping markets may be correlated.

This is decision-support software, not a guarantee of profit. **NO BET is a valid result.**
