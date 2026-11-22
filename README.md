# Goal Scanner 2.0

Independent, evidence-led football analysis engine. It does not modify, import or depend on the legacy scanner.

## Included in this initial implementation
- Restricted market registry and explicit exclusion of Under markets, match Over 1.5, team Over 0.5/1.5 and BTTS No.
- Transparent independent-Poisson baseline for match Over 2.5, BTTS Yes and 1X2.
- Evidence records with source URL, publication/retrieval timestamps, verification and confidence.
- Quality gate excluding rumours and unknown claims from confirmed evidence.
- Odds margin removal, expected value and APPROVED / REJECTED / INSUFFICIENT_DATA decisions.
- Accumulator safety gate (2–3 legs; joint dependence must be reviewed).
- Hashed prediction snapshots at T-24h, team-news and official-lineup stages.
- Chronological backtest checks, Brier score, log loss and flat-stake ROI.
- CLI, optional FastAPI endpoint, tests and GitHub Actions CI.

## Install
```bash
python -m venv .venv
. .venv/bin/activate
pip install -e ".[dev,api]"
pytest -q
goal-scanner --home-lambda 1.55 --away-lambda 1.10
uvicorn goal_scanner.api:app --reload
```
Windows PowerShell: `.venv\\Scripts\\Activate.ps1`.

## Allowed markets
Match Over 2.5; BTTS Yes; home/draw/away; team corners Over; match total corners Over; match bookings Over; team bookings Over. All Under markets, match Over 1.5, team Over 0.5/1.5 and BTTS No are excluded.

## Important limitations
The goal model is a transparent independent-Poisson baseline, not yet a trained league/team model or a validated betting edge. The corner and booking market identifiers are reserved, but their market-specific calibrated models and event-data adapters are not implemented yet. Do not treat the generic count helper as validated corner/card predictions. Contextual features must not affect probabilities until chronological out-of-sample validation and ablation demonstrate improvement.

No free data provider is guaranteed to supply all injuries, suspensions, player stats, referees, odds and event data for every competition. Show coverage and freshness; missing or stale data blocks or downgrades decisions. No forced bets. Accumulators target combined odds 2.00–3.00, usually two legs, maximum three; never multiply correlated same-fixture probabilities as independent. No staking rule is claimed optimal.

This is decision-support software, not a guarantee of profit. **NO BET is a valid result.**


## Data providers

The four Mr2Odds sources have read-only adapters in goal_scanner/providers.py. See docs/data-sources.md for usage, provenance, freshness and modelling boundaries. No API keys are required by the currently configured public endpoints. SportyBet odds are strictly for price/value comparison; Transfermarkt squad metrics are context-only.
