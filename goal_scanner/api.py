"""Optional API: install with pip install -e '.[api]'."""
try:
    from fastapi import FastAPI,HTTPException
    from pydantic import BaseModel,Field
except ImportError as exc: raise RuntimeError("Install optional dependencies: pip install -e '.[api]'") from exc
from .models import goal_market_probabilities
app=FastAPI(title="Goal Scanner 2.0",version="2.0.0")
class MatchInput(BaseModel):
    home_lambda: float=Field(gt=0,le=6)
    away_lambda: float=Field(gt=0,le=6)
@app.get("/health")
def health(): return {"status":"ok","version":"2.0.0"}
@app.post("/probabilities")
def probabilities(data: MatchInput):
    try: return {k.value:v for k,v in goal_market_probabilities(data.home_lambda,data.away_lambda).items()}
    except ValueError as exc: raise HTTPException(status_code=422,detail=str(exc)) from exc
