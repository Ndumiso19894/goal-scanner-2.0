FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    GOAL_SCANNER_DB=/data/goal_scanner.sqlite3

WORKDIR /app
COPY pyproject.toml README.md ./
COPY goal_scanner ./goal_scanner

RUN pip install --no-cache-dir ".[api]" \
    && useradd --create-home --uid 10001 scanner \
    && mkdir -p /data \
    && chown -R scanner:scanner /app /data

USER scanner
EXPOSE 8000
CMD ["uvicorn", "goal_scanner.api:app", "--host", "0.0.0.0", "--port", "8000"]
