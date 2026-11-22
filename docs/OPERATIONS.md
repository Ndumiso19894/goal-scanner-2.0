# Operations and setup

1. Copy .env.example to .env and add an API-Football key only if you have one. Never commit .env.
2. Install: pip install -e ".[dev,api]".
3. Run tests: pytest -q.
4. Run API: uvicorn goal_scanner.api:app --host 127.0.0.1 --port 8000.
5. Keep API keys private; configure them as deployment secrets for hosted workflows.

The provider adapter is a thin authenticated HTTP client, not a full ingestion scheduler. It does not promise free coverage for every league or endpoint. Respect the provider's live quota and terms. Use Store for local SQLite fixture/snapshot persistence. Keep original provider payloads and provenance in any production ingestion extension.
