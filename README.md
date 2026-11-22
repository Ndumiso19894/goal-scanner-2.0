# Goal Scanner 2.0

A separate, evidence-led football analysis and prediction system. This repository is independent of the existing Goal Scanner and will not modify or depend on the old scanner.

## Core principles

- Evidence must be traceable to a source, with publication and retrieval timestamps.
- Unknown or stale information must reduce confidence; rumours are never treated as confirmed facts.
- Model probabilities must be calibrated and evaluated chronologically against out-of-sample data.
- Bookmaker prices are benchmarks, with market margin removed where a complete market is available.
- Predictions are snapshotted at defined information stages to prevent look-ahead leakage.
- The system must return **NO BET** when evidence, data quality, or expected value is insufficient.

## Supported markets

- Match total goals: Over 2.5 only.
- Both teams to score: Yes only.
- Match result: home win, draw, away win.
- Team corners Over and match total corners Over.
- Match bookings Over and team bookings Over.

Under markets, match Over 1.5, team Over 0.5/1.5, and BTTS No are excluded.

## Accumulator policy

Target combined odds: 2.00–3.00. Prefer two legs; use a third only when it improves the ticket. Do not treat correlated selections as independent. Every leg must pass its own evidence, quality, and conservative expected-value checks.

## Development status

Initial repository setup. Implementation, source adapters, models, tests, and automated checks will be added in separate commits.

## Safety and scope

This is an analytical tool, not a guarantee of betting outcomes or profitability. It must never force a selection merely to produce a ticket. The existing scanner and its repository remain untouched.