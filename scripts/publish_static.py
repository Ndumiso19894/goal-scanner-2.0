"""Run the goal scanner on GitHub Actions and publish public static JSON feeds."""
from __future__ import annotations

import json
import os
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from urllib.request import Request, urlopen

from goal_scanner import api
from goal_scanner.providers import FootballDataProvider, LiveScoreProvider, ProviderError, TransfermarktProvider, team_similarity

# Football-Data.co.uk division codes and the corresponding LiveScore competition names.
# Only competitions with a matching historical results feed are included.
LEAGUES = [
    ("E0", "Premier League"),
    ("E1", "Championship"),
    ("E2", "League One"),
    ("E3", "League Two"),
    ("SC0", "Premiership"),
    ("D1", "Bundesliga"),
    ("I1", "Serie A"),
    ("SP1", "LaLiga"),
    ("F1", "Ligue 1"),
    ("N1", "Eredivisie"),
    ("B1", "Pro League"),
    ("P1", "Liga Portugal"),
    ("T1", "Süper Lig"),
    ("G1", "Super League"),
    ("EC", "National League"),
    ("D2", "2. Bundesliga"),
    ("I2", "Serie B"),
    ("SP2", "LaLiga 2"),
    ("F2", "Ligue 2"),
    ("N2", "Eerste Divisie"),
    ("B2", "Challenger Pro League"),
    ("P2", "Liga Portugal 2"),
]
COMPETITION_ALIASES = {"League 1": "League One", "League 2": "League Two"}
COMPETITION_COUNTRIES = {
    "Premier League": "England", "Championship": "England", "League One": "England",
    "League Two": "England", "National League": "England", "Premiership": "Scotland",
    "Bundesliga": "Germany", "2. Bundesliga": "Germany", "Serie A": "Italy", "Serie B": "Italy",
    "LaLiga": "Spain", "LaLiga 2": "Spain", "Ligue 1": "France", "Ligue 2": "France",
    "Eredivisie": "Netherlands", "Eerste Divisie": "Netherlands", "Pro League": "Belgium",
    "Challenger Pro League": "Belgium", "Liga Portugal": "Portugal", "Liga Portugal 2": "Portugal",
    "Süper Lig": "Turkey", "Super League": "Greece",
}
MARKETS = sorted(api.SELECTABLE_MARKETS)
OUTPUT = Path("docs/data")
OUTPUT.mkdir(parents=True, exist_ok=True)


def current_season(today: date) -> str:
    start_year = today.year if today.month >= 7 else today.year - 1
    return f"{start_year % 100:02d}{(start_year + 1) % 100:02d}"


def write_json(name: str, payload: dict) -> None:
    path = OUTPUT / name
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _parse_time(value: str):
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except (TypeError, ValueError):
        return None


def _team_pre_match_stats(rows: list[dict], team: str, kickoff: str, venue: str) -> dict | None:
    """Build venue-specific form only from completed matches before this kickoff."""
    target_time = _parse_time(kickoff)
    if target_time is None:
        return None
    matches = []
    for row in rows:
        if row.get("home_goals") is None or row.get("away_goals") is None:
            continue
        match_time = _parse_time(row.get("kickoff") or row.get("date"))
        if match_time is None or match_time >= target_time:
            continue
        is_home = team_similarity(team, row.get("home", "")) >= 0.88
        is_away = team_similarity(team, row.get("away", "")) >= 0.88
        if is_home == is_away:
            continue
        if venue == "home" and not is_home or venue == "away" and not is_away:
            continue
        stats = row.get("statistics") or {}
        gf = row["home_goals"] if is_home else row["away_goals"]
        ga = row["away_goals"] if is_home else row["home_goals"]
        result = "W" if gf > ga else "D" if gf == ga else "L"
        corners = stats.get("HC" if is_home else "AC")
        cards = sum((stats.get(k) or 0) for k in (("HY", "HR") if is_home else ("AY", "AR")))
        has_cards = any(stats.get(k) is not None for k in (("HY", "HR") if is_home else ("AY", "AR")))
        matches.append({"time": match_time, "gf": gf, "ga": ga, "result": result,
                        "corners": corners, "cards": cards if has_cards else None, "opponent": row.get("away" if is_home else "home", ""), "venue": "H" if is_home else "A", "shots": (row.get("statistics") or {}).get("HS" if is_home else "AS"), "shots_on_target": (row.get("statistics") or {}).get("HST" if is_home else "AST"), "xg": (row.get("statistics") or {}).get("HxG" if is_home else "AxG")})
    matches.sort(key=lambda item: item["time"], reverse=True)
    if not matches:
        return None
    recent = matches[:5]
    n = len(matches)
    return {
        "played": n, "wins": sum(x["result"] == "W" for x in matches),
        "draws": sum(x["result"] == "D" for x in matches),
        "losses": sum(x["result"] == "L" for x in matches),
        "form": "".join(x["result"] for x in reversed(recent)),
        "goals_for_avg": round(sum(x["gf"] for x in matches) / n, 2),
        "goals_against_avg": round(sum(x["ga"] for x in matches) / n, 2),
        "btts_pct": round(100 * sum(x["gf"] > 0 and x["ga"] > 0 for x in matches) / n),
        "over_2_5_pct": round(100 * sum(x["gf"] + x["ga"] >= 3 for x in matches) / n),
        "clean_sheet_pct": round(100 * sum(x["ga"] == 0 for x in matches) / n),
        "corners_avg": round(sum(x["corners"] for x in matches if x["corners"] is not None) /
                             max(1, sum(x["corners"] is not None for x in matches)), 2)
                       if any(x["corners"] is not None for x in matches) else None,
        "bookings_avg": round(sum(x["cards"] for x in matches if x["cards"] is not None) /
                              max(1, sum(x["cards"] is not None for x in matches)), 2)
                        if any(x["cards"] is not None for x in matches) else None,
        "shots_avg": round(sum(x["shots"] for x in matches if x["shots"] is not None) / max(1, sum(x["shots"] is not None for x in matches)), 2) if any(x["shots"] is not None for x in matches) else None,
        "shots_on_target_avg": round(sum(x["shots_on_target"] for x in matches if x["shots_on_target"] is not None) / max(1, sum(x["shots_on_target"] is not None for x in matches)), 2) if any(x["shots_on_target"] is not None for x in matches) else None,
        "xg_avg": round(sum(x["xg"] for x in matches if x["xg"] is not None) / max(1, sum(x["xg"] is not None for x in matches)), 2) if any(x["xg"] is not None for x in matches) else None,
        "recent_matches": [{"date": x["time"].date().isoformat(), "opponent": x["opponent"], "venue": x["venue"], "score": str(x["gf"]) + "-" + str(x["ga"]), "result": x["result"], "goals": x["gf"] + x["ga"], "shots": x["shots"], "shots_on_target": x["shots_on_target"], "xg": x["xg"]} for x in recent[:10]],
        "recent_trends": {"last5_wins": sum(x["result"] == "W" for x in recent[:5]), "last5_draws": sum(x["result"] == "D" for x in recent[:5]), "last5_losses": sum(x["result"] == "L" for x in recent[:5]), "last5_goals_for": sum(x["gf"] for x in recent[:5]), "last5_goals_against": sum(x["ga"] for x in recent[:5])},
        "source": "Football-Data.co.uk", "scope": "home matches" if venue == "home" else "away matches",
    }


def _head_to_head(rows: list[dict], home: str, away: str, kickoff: str) -> dict | None:
    target_time = _parse_time(kickoff)
    if target_time is None:
        return None
    matches = []
    for row in rows:
        match_time = _parse_time(row.get("kickoff") or row.get("date"))
        if match_time is None or match_time >= target_time or row.get("home_goals") is None:
            continue
        direct = team_similarity(home, row.get("home", "")) >= .88 and team_similarity(away, row.get("away", "")) >= .88
        reverse = team_similarity(home, row.get("away", "")) >= .88 and team_similarity(away, row.get("home", "")) >= .88
        if direct or reverse:
            hg, ag = row["home_goals"], row["away_goals"]
            home_goals, away_goals = (hg, ag) if direct else (ag, hg)
            matches.append((match_time, home_goals, away_goals))
    matches.sort(reverse=True)
    if not matches:
        return {"played": 0, "home_wins": 0, "draws": 0, "away_wins": 0, "source": "Football-Data.co.uk"}
    return {
        "played": len(matches),
        "home_wins": sum(h > a for _, h, a in matches),
        "draws": sum(h == a for _, h, a in matches),
        "away_wins": sum(h < a for _, h, a in matches),
        "recent_scores": [f"{h}-{a}" for _, h, a in matches[:5]],
        "recent_meetings": [{"date": stamp.date().isoformat(), "score": str(h) + "-" + str(a), "home_team_score": h, "away_team_score": a} for stamp, h, a in matches[:10]],
        "source": "Football-Data.co.uk",
    }



def _league_table(rows: list[dict], as_of: date) -> list[dict]:
    """Build a reproducible table from completed results before the current date."""
    table: dict[str, dict] = {}
    for row in rows:
        try:
            played = date.fromisoformat(str(row.get("date") or ""))
            hg, ag = row.get("home_goals"), row.get("away_goals")
            if played >= as_of or hg is None or ag is None:
                continue
            hg, ag = int(hg), int(ag)
        except (TypeError, ValueError):
            continue
        home, away = str(row.get("home") or ""), str(row.get("away") or "")
        if not home or not away:
            continue
        for name in (home, away):
            table.setdefault(name, {"team": name, "played": 0, "won": 0, "drawn": 0, "lost": 0, "gf": 0, "ga": 0, "points": 0, "home": {"played": 0, "won": 0, "drawn": 0, "lost": 0, "gf": 0, "ga": 0, "points": 0}, "away": {"played": 0, "won": 0, "drawn": 0, "lost": 0, "gf": 0, "ga": 0, "points": 0}, "_form": []})
        for name, gf, ga, venue in ((home, hg, ag, "home"), (away, ag, hg, "away")):
            item, split = table[name], table[name][venue]
            result = "W" if gf > ga else "D" if gf == ga else "L"
            pts = 3 if result == "W" else 1 if result == "D" else 0
            for target in (item, split):
                target["played"] += 1
                target["gf"] += gf
                target["ga"] += ga
                target["won"] += int(result == "W")
                target["drawn"] += int(result == "D")
                target["lost"] += int(result == "L")
                target["points"] += pts
            item["_form"].append((played, result))
    result = []
    for item in table.values():
        item["gd"] = item["gf"] - item["ga"]
        item["form"] = "".join(v for _, v in sorted(item.pop("_form"))[-5:])
        for split in ("home", "away"):
            item[split]["gd"] = item[split]["gf"] - item[split]["ga"]
        result.append(item)
    result.sort(key=lambda x: (x["points"], x["gd"], x["gf"], x["team"]), reverse=True)
    for position, item in enumerate(result, 1):
        item["position"] = position
    return result

def _download_club_crests(fixtures: list[dict]) -> int:
    """Save LiveScore team crests in the repository so the app can reuse cached assets."""
    folder = Path("docs/assets/crests")
    folder.mkdir(parents=True, exist_ok=True)
    downloaded = 0
    for fixture in fixtures:
        for side in ("home", "away"):
            team_id = str(fixture.get(f"{side}_team_id") or "").strip()
            image_path = str(fixture.get(f"{side}_badge_path") or "").strip().lstrip("/")
            if not team_id or not image_path:
                continue
            target = folder / f"{team_id}.png"
            if target.exists() and target.stat().st_size > 100:
                continue
            image_url = image_path if image_path.startswith("https://") else (
                "https://lsm-static-prod.livescore.com/medium/" + image_path)
            try:
                request = Request(image_url, headers={"User-Agent": "Mozilla/5.0", "Accept": "image/*"})
                with urlopen(request, timeout=12) as response:
                    data = response.read(500_001)
                if len(data) > 100 and len(data) <= 500_000 and (data.startswith(bytes((137, 80, 78, 71))) or data.startswith(bytes((255, 216, 255))) or data[:4] == b"RIFF"):
                    target.write_bytes(data)
                    downloaded += 1
            except Exception:
                continue
    return downloaded


def main() -> None:
    today = datetime.now(timezone.utc).astimezone(api.LOCAL_TZ).date()
    season = current_season(today)
    league_status = []
    for division, competition in LEAGUES:
        try:
            request = api.ScannerRunInput(
                match_date=today,
                season=season,
                division=division,
                hours_ahead=72,
                minimum_probability=0.60,
                markets=MARKETS,
                mode="full",
                competition_query=competition,
            )
            result = api.scanner_run(request)
            league_status.append({
                "division": division,
                "competition": competition,
                "status": result.get("status", "unknown"),
                "fixtures": result.get("fixture_count", 0),
                "recommendations": result.get("recommendation_count", 0),
                "sources": result.get("sources", {}),
            })
        except Exception as exc:  # A single unavailable league must not stop the others.
            league_status.append({
                "division": division,
                "competition": competition,
                "status": "error",
                "error": f"{type(exc).__name__}: {exc}",
            })

    # Settle prior predictions only when the scoreboard marks a match as full-time.
    try:
        settlement = api.refresh_results()
    except Exception as exc:
        settlement = {"status": "error", "error": f"{type(exc).__name__}: {exc}"}

    now = datetime.now(timezone.utc)
    latest_by_market: dict[tuple[str, str, str], dict] = {}
    for row in api.STORE.list_predictions(limit=500, status="PENDING"):
        candidate = row.get("payload") or {}
        try:
            kickoff = datetime.fromisoformat(str(candidate.get("kickoff", "")).replace("Z", "+00:00"))
            if kickoff < now or kickoff > now + timedelta(days=7):
                continue
        except (TypeError, ValueError):
            continue
        candidate = {**candidate, "prediction_id": row["id"], "status": "PENDING"}
        market = str(candidate.get("market", ""))
        selection = str(candidate.get("selection", ""))
        market_key = f"match_winner_{selection}" if market == "match_winner" else market
        try:
            has_sportybet_price = candidate.get("odds_status") == "available" and float(candidate.get("odds") or 0) > 1
        except (TypeError, ValueError):
            has_sportybet_price = False
        if market_key not in api.SELECTABLE_MARKETS or not has_sportybet_price:
            continue
        key = (str(candidate.get("fixture_id", "")), market, selection)
        # list_predictions is newest-first, so the first record is the latest snapshot.
        latest_by_market.setdefault(key, candidate)

    recommendations = sorted(
        latest_by_market.values(),
        key=lambda item: (item.get("rank_score", 0), item.get("estimated_probability", 0)),
        reverse=True,
    )[:100]

    fixture_rows = []
    fixture_errors = []
    provider = LiveScoreProvider()
    for offset in range(3):
        day = today + timedelta(days=offset)
        try:
            for item in provider.fetch_day(day, utc_offset_hours=2):
                fixture_rows.append({
                    "id": item.source_id, "kickoff": item.kickoff, "home": item.home,
                    "away": item.away, "competition": item.competition, "country": item.country,
                    "status": item.status, "home_score": item.home_score,
                    "away_score": item.away_score, "source": item.source,
                    "home_team_id": item.home_team_id, "away_team_id": item.away_team_id,
                    "home_badge_path": item.home_badge_path, "away_badge_path": item.away_badge_path,
                })
        except ProviderError as exc:
            fixture_errors.append(str(exc))
    fixture_rows = [
        item for item in fixture_rows
        if COMPETITION_COUNTRIES.get(COMPETITION_ALIASES.get(item.get("competition", ""), item.get("competition", "")), "").casefold()
        == str(item.get("country") or "").casefold()
    ]
    fixture_rows.sort(key=lambda item: item.get("kickoff") or "")

    # Enrich fixtures with team crests and historical pre-match data where a
    # matching Football-Data competition exists. Missing fields stay null.
    history_by_competition = {}
    for division, competition in LEAGUES:
        try:
            history_by_competition[competition] = FootballDataProvider().fetch_season(season, division)
        except (ProviderError, ValueError):
            history_by_competition[competition] = []
    crest_downloads = _download_club_crests(fixture_rows)
    league_tables = [{"division": division, "competition": competition, "table": _league_table(history_by_competition.get(competition, []), today), "source": "Football-Data.co.uk"} for division, competition in LEAGUES]
    for fixture in fixture_rows:
        fixture["home_crest_url"] = (
            f"https://raw.githubusercontent.com/Ndumiso19894/goal-scanner-2.0/main/docs/assets/crests/{fixture['home_team_id']}.png"
            if fixture.get("home_team_id") else None)
        fixture["away_crest_url"] = (
            f"https://raw.githubusercontent.com/Ndumiso19894/goal-scanner-2.0/main/docs/assets/crests/{fixture['away_team_id']}.png"
            if fixture.get("away_team_id") else None)
        canonical_competition = COMPETITION_ALIASES.get(fixture.get("competition", ""), fixture.get("competition", ""))
        history = history_by_competition.get(canonical_competition, [])
        fixture["pre_match_stats"] = {
            "home": _team_pre_match_stats(history, fixture.get("home", ""), fixture.get("kickoff", ""), "home"),
            "away": _team_pre_match_stats(history, fixture.get("away", ""), fixture.get("kickoff", ""), "away"),
            "head_to_head": _head_to_head(history, fixture.get("home", ""), fixture.get("away", ""), fixture.get("kickoff", "")),
            "source": "Football-Data.co.uk",
            "available": bool(history),
        }

    generated_at = datetime.now(timezone.utc).isoformat()
    sources = {
        "livescore_and_odds": "Collected during each league scan; bookmaker odds are comparison-only.",
        "historical_results": "Football-Data.co.uk completed results, filtered to matches before each fixture.",
        "league_runs": league_status,
    }
    write_json("latest.json", {
        "schema_version": 1,
        "updated_at": generated_at,
        "season": season,
        "status": "ok" if any(x.get("status") in {"ok", "no_qualifying_recommendations"} for x in league_status) else "partial",
        "recommendation_count": len(recommendations),
        "fixture_count": len(fixture_rows),
        "recommendations": recommendations,
        "sources": sources,
        "notes": [
            "Probabilities are uncalibrated historical-rate estimates, not guaranteed outcomes.",
            "Bookmaker odds are attached after model probabilities and are not model inputs.",
            "Only fixtures with sufficient prior home/away history are eligible.",
        ],
    })
    write_json("league-tables.json", {
        "schema_version": 1, "updated_at": generated_at,
        "source": "Football-Data.co.uk completed results; current season only",
        "leagues": league_tables,
    })
    write_json("fixtures.json", {
        "schema_version": 3, "updated_at": generated_at,
        "count": len(fixture_rows), "fixtures": fixture_rows, "errors": fixture_errors,
        "club_crests_downloaded_this_run": crest_downloads,
        "pre_match_stats_source": "Football-Data.co.uk; only completed matches before each fixture are included",
    })
    movements = api.STORE.list_dropping_odds(minimum_drop_pct=3.0, lookback_hours=24, limit=200)
    allowed_movement_markets = {"1X2", "btts", "match_total_goals", "match_corners",
                                "home_corners", "away_corners", "match_cards", "home_cards", "away_cards"}
    movements = [item for item in movements
                 if item.get("market") in allowed_movement_markets
                 and (item.get("market") != "btts" or str(item.get("selection", "")).lower() == "yes")
                 and (item.get("market") != "match_total_goals" or str(item.get("selection", "")).lower() == "2.5/over")
                 and (item.get("market") == "1X2" or item.get("market") == "btts"
                      or str(item.get("selection", "")).lower().endswith("/over"))]
    write_json("odds-movements.json", {
        "schema_version": 1, "updated_at": generated_at,
        "minimum_drop_pct": 3.0, "lookback_hours": 24,
        "count": len(movements), "movements": movements,
        "note": "Only price changes observed in at least two snapshots are shown; odds movement is not a prediction."
    })
    write_json("results.json", {
        "schema_version": 1, "updated_at": generated_at,
        "summary": api.STORE.results_summary(),
        "recent": [row for row in api.STORE.list_predictions(limit=500)
                   if row.get("odds") is not None and float(row.get("odds") or 0) > 1][:100],
        "settlement_refresh": settlement,
    })

    print(f"Published {len(recommendations)} upcoming selections from {len(league_status)} league scans.")
    for item in league_status:
        print(f"{item['division']} {item['competition']}: {item['status']} "
              f"({item.get('fixtures', 0)} fixtures, {item.get('recommendations', 0)} picks)")
    print(f"Fixtures feed: {len(fixture_rows)} rows; settlement: {settlement.get('status', 'unknown')}")


if __name__ == "__main__":
    main()
