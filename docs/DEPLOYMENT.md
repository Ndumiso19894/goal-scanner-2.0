# GitHub-only data delivery

Goal Scanner Pro does not need a continuously running API server.

## How it works

1. GitHub Actions checks out the scanner branch and installs the Python scanner.
2. The workflow collects fixtures, historical results and available bookmaker prices from the configured public sources.
3. It estimates goal-market probabilities from historical results, attaches bookmaker prices only after calculating probabilities, and records prediction snapshots.
4. It refreshes pending selections only when LiveScore reports a recognised full-time status with final scores.
5. It writes three public JSON files:
   - `docs/data/latest.json`
   - `docs/data/fixtures.json`
   - `docs/data/results.json`
6. GitHub Actions commits changed JSON files back to the scanner branch.
7. The Android app reads those files directly from the public GitHub raw-content URL.

No separate hosting account, API endpoint, server token or paid database is required for this delivery method.

## Refresh schedule and activation

The workflow is configured to run every four hours, on relevant scanner-branch pushes, and by manual dispatch. **GitHub only runs scheduled workflows from the repository's default branch.** Therefore the scheduled refresh will not become active until the static publisher workflow is merged into `main`. A successful run on the feature branch can publish an initial feed before that.

The workflow uses the GitHub Actions cache to carry its SQLite prediction history between runs. Cache entries are temporary and are not a permanent database backup. If the cache expires or is evicted, historical scanner records may reset; the already-published JSON snapshots remain available until replaced.

## Android feed address

The default app data base is:

```
https://raw.githubusercontent.com/Ndumiso19894/goal-scanner-2.0/feature/android-goal-scanner-pro
```

The app appends `/docs/data/latest.json`, `/docs/data/fixtures.json` and `/docs/data/results.json` as needed. The repository and these files are public, so anyone can read the published selections and results.

## Data and model limitations

The providers are unofficial public feeds and may change or be unavailable. Each league scan reports source status; a missing feed must not be interpreted as zero goals, zero odds or a confirmed absence of events.

The initial probability model is an uncalibrated historical-rate baseline. Its percentages are estimates, not guaranteed outcomes or evidence of a profitable betting edge. Bookmaker odds are for comparison only and do not feed the probability calculation.

## Optional API service

The repository retains a FastAPI service for local development and future integrations. It is not required by the Android app's GitHub-feed mode and should not be deployed or configured for the current setup.
