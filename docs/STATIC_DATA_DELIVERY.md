# Static data delivery

Goal Scanner Pro can deliver predictions without a continuously running API server.

## How it works

- The `Publish Goal Scanner data` GitHub Actions workflow runs every four hours and can also be started manually.
- It scans the supported Football-Data.co.uk competitions, collects fixtures and SportyBet prices, estimates the selected markets, and refreshes full-time settlements.
- A small SQLite database is retained between scheduled runs using GitHub Actions cache. It preserves prediction history for settlement and performance reporting.
- The workflow publishes three public JSON documents in `docs/data/`: `latest.json`, `fixtures.json`, and `results.json`.
- The Android app reads these files directly from the repository's raw-content URL. No API server, API key, or separate hosting account is required.

## Public data

This repository is public. The published JSON feeds are therefore public too. Do not put personal information, credentials, bearer tokens, or private provider data in them.

## Refresh frequency and limitations

The scheduled workflow runs every four hours, not continuously. GitHub Actions scheduling can be delayed by GitHub load. The app can refresh the latest published snapshot when it has an internet connection, but it cannot request a new scan instantly. A manual workflow run can trigger an earlier refresh.

The initial goal model remains an uncalibrated historical-rate baseline. Missing provider coverage is reported rather than fabricated. Bookmaker odds are attached after probabilities are calculated and are not used as model inputs.

## Supported competition feeds

The first scheduled set includes England E0-E3, Scotland SC0, Germany D1, Italy I1, Spain SP1, France F1, Netherlands N1, Belgium B1, Portugal P1, Turkey T1, and Greece G1. Coverage depends on current provider availability and exact competition matching.
