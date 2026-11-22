"""Read-only, local StatsBomb Open Data inventory for non-commercial research only.

This module is deliberately outside goal_scanner. It makes no network requests,
does not modify source files, and does not persist or export derived data.
Do not use StatsBomb-derived results in a commercial product.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

NONCOMMERCIAL_RESEARCH_ONLY = True


def _read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def _safe_child(root: Path, *parts: str) -> Path:
    """Resolve a data path and prevent identifiers escaping the supplied data root."""
    candidate = root.joinpath(*parts).resolve()
    base = root.resolve()
    if candidate != base and base not in candidate.parents:
        raise ValueError("StatsBomb data path escapes the selected data root")
    return candidate


def inventory(data_root: str | Path) -> dict[str, int]:
    """Count competitions, seasons, matches, lineup files and lineup player rows.

    The official source files are only read. No source or derived file is written.
    The return value contains aggregate counts, not player-level records.
    """
    root = Path(data_root).expanduser().resolve()
    competitions_path = _safe_child(root, "competitions.json")
    if not competitions_path.is_file():
        raise FileNotFoundError(f"Missing official competitions.json: {competitions_path}")

    competitions = _read_json(competitions_path)
    if not isinstance(competitions, list):
        raise ValueError("competitions.json must contain a JSON list")

    season_count = 0
    match_count = 0
    lineup_file_count = 0
    lineup_player_rows = 0

    for competition in competitions:
        if not isinstance(competition, dict):
            continue
        competition_id = str(competition.get("competition_id", "")).strip()
        season_id = str(competition.get("season_id", "")).strip()
        if not competition_id or not season_id:
            continue
        season_count += 1

        matches_path = _safe_child(root, "matches", competition_id, f"{season_id}.json")
        if not matches_path.is_file():
            continue
        matches = _read_json(matches_path)
        if not isinstance(matches, list):
            continue

        for match in matches:
            if not isinstance(match, dict) or match.get("match_id") is None:
                continue
            match_count += 1
            match_id = str(match["match_id"])
            lineup_path = _safe_child(root, "lineups", f"{match_id}.json")
            if not lineup_path.is_file():
                continue
            lineups = _read_json(lineup_path)
            if not isinstance(lineups, list):
                continue
            lineup_file_count += 1
            for team in lineups:
                if not isinstance(team, dict):
                    continue
                players = team.get("lineup", [])
                if isinstance(players, list):
                    lineup_player_rows += sum(1 for player in players if isinstance(player, dict))

    return {
        "competition_season_entries": season_count,
        "matches_listed": match_count,
        "matches_with_lineup_files": lineup_file_count,
        "lineup_player_rows": lineup_player_rows,
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Read-only StatsBomb Open Data inventory (non-commercial research only)."
    )
    parser.add_argument(
        "--data-root",
        required=True,
        help="Path to the data/ directory in your own local official StatsBomb Open Data checkout.",
    )
    args = parser.parse_args()
    counts = inventory(args.data_root)
    print("StatsBomb Open Data — private, non-commercial research inventory")
    for name, count in counts.items():
        print(f"{name}: {count}")
    print("No source files were changed and no output files were created.")
    print("Do not use StatsBomb data or derived analysis commercially or in Goal Scanner.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
