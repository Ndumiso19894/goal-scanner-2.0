# Backend deployment

The Android app is a client. It needs a reachable Goal Scanner Pro API server; a GitHub repository alone is not a running backend.

## Container build

The repository includes a Dockerfile. Build and run locally:

```bash
docker build -t goal-scanner-pro-api .
mkdir -p ./goal-scanner-data
docker run --rm -p 8000:8000 \
  -e GOAL_SCANNER_API_TOKEN="$(openssl rand -hex 32)" \
  -e GOAL_SCANNER_DB=/data/goal_scanner.sqlite3 \
  -v "$(pwd)/goal-scanner-data:/data" \
  goal-scanner-pro-api
```

For a real phone connection, deploy the image to a host that provides HTTPS and a persistent volume. Mount that volume at `/data`. Do not use an ephemeral filesystem for the SQLite database: the prediction history would be lost when the container restarts or is replaced.

## Secure the API

Set `GOAL_SCANNER_API_TOKEN` to a long random secret. The public `/health` endpoint remains available for health checks; all other API endpoints require:

```
Authorization: Bearer YOUR_RANDOM_TOKEN
```

Paste the deployed HTTPS base URL and the same token into Goal Scanner Pro → Settings. The app stores the token locally and sends it in the Authorization header. Do not commit the token to GitHub or share it publicly.

If `GOAL_SCANNER_API_TOKEN` is not set, the API is unauthenticated. That mode is suitable only for local development on a trusted network, not a public deployment.

## Persistent records

The SQLite database is stored at `GOAL_SCANNER_DB`, defaulting to `data/goal_scanner.sqlite3`. Back up the persistent volume regularly. This initial service uses a single SQLite writer-oriented deployment; do not scale it horizontally across multiple containers sharing no common database.

## Android endpoints

- `GET /health` — connectivity and version
- `GET /sources/manifest` — provider roles
- `POST /sources/collect` — raw provider collection
- `POST /scanner/run` — generate and save recommendations
- `GET /predictions` — saved prediction snapshots
- `GET /results` — settled performance summary
- `POST /results/refresh` — fetch verified final scores and settle pending records

The free/public data providers are unofficial feeds and can be rate-limited, changed or unavailable. The scanner reports provider errors and skips fixtures with insufficient prior data.
