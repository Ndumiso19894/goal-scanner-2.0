"""Minimal API-Football adapter. Requires API_FOOTBALL_KEY; caches are caller-managed."""
import json, os, time
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

class ProviderError(RuntimeError): pass
class ApiFootball:
    def __init__(self, api_key: str|None=None, base_url: str|None=None, timeout: int=15):
        self.key=api_key or os.getenv("API_FOOTBALL_KEY")
        self.base=(base_url or os.getenv("API_FOOTBALL_BASE_URL") or "https://v3.football.api-sports.io").rstrip("/")
        self.timeout=timeout
        self.last_call=0.0
    def get(self, endpoint: str, params: dict|None=None) -> dict:
        if not self.key: raise ProviderError("API_FOOTBALL_KEY is not configured")
        if not endpoint.startswith("/"): endpoint="/"+endpoint
        from urllib.parse import urlencode
        query=urlencode(params or {})
        url=self.base+endpoint+("?"+query if query else "")
        # Be conservative: do not burst requests on the free plan.
        wait=1.0-(time.monotonic()-self.last_call)
        if wait>0: time.sleep(wait)
        req=Request(url,headers={"x-apisports-key":self.key,"User-Agent":"GoalScanner2/2.0"})
        try:
            with urlopen(req,timeout=self.timeout) as response:
                self.last_call=time.monotonic()
                payload=json.loads(response.read().decode("utf-8"))
        except (HTTPError,URLError,TimeoutError,ValueError) as exc:
            raise ProviderError(f"API-Football request failed: {exc}") from exc
        errors=payload.get("errors")
        if errors: raise ProviderError(f"Provider returned errors: {errors}")
        return payload
