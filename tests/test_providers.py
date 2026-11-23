from goal_scanner.providers import (
    FootballDataProvider, LiveScoreProvider, SportyBetProvider, TransfermarktProvider,
    match_fixtures_to_odds, team_similarity,
)

def test_livescore_event_normalisation():
    stage={"Snm":"Premier League","Cnm":"England"}
    event={"Eid":"123","Esd":"20261122190000","Eps":"FT","Tr1":"2","Tr2":"1","Trh1":"1","Trh2":"0",
           "T1":[{"Nm":"Arsenal","ID":"1","Img":"enet/1.png"}],"T2":[{"Nm":"Chelsea","ID":"2","Img":"enet/2.png"}]}
    fixture=LiveScoreProvider._event(event,stage,2)
    assert fixture and fixture.source_id=="123" and fixture.home_score==2 and fixture.away_score==1
    assert fixture.competition=="Premier League" and fixture.kickoff.endswith("+02:00")
    assert fixture.home_team_id=="1" and fixture.away_team_id=="2"
    assert fixture.home_badge_path=="enet/1.png" and fixture.away_badge_path=="enet/2.png"

def test_football_data_missing_stats_remain_missing():
    row={"Date":"22/11/2026","Time":"15:00","HomeTeam":"Arsenal","AwayTeam":"Chelsea",
         "FTHG":"2","FTAG":"1","HS":"12","AS":"8","HxG":"","AxG":""}
    match=FootballDataProvider._normalise_row(row,"2627","E0")
    assert match and match["home_goals"]==2 and match["away_goals"]==1
    assert match["statistics"]["HS"]==12 and match["statistics"]["HxG"] is None

def test_sportybet_prices_are_parsed_separately():
    markets=[{"id":"1","outcomes":[{"desc":"Home","odds":"2.10","isActive":1},
        {"desc":"Draw","odds":"3.20","isActive":1},{"desc":"Away","odds":"3.50","isActive":1}]},
        {"id":"18","specifier":"total=2.5","outcomes":[{"desc":"Over 2.5","odds":"1.90","isActive":1},
        {"desc":"Under 2.5","odds":"1.90","isActive":1}]}]
    parsed=SportyBetProvider.parse_markets(markets)
    assert parsed["1X2"]["home"]==2.10 and parsed["match_total_goals"][2.5]["over"]==1.90

def test_transfermarkt_mapping_and_team_match():
    assert TransfermarktProvider.competition_code("E0")=="GB1"
    clubs=[{"id":"1","name":"Manchester United","short_name":"Man Utd"}]
    match=TransfermarktProvider.match_team("Man Utd",clubs)
    assert match and match["id"]=="1" and match["match_confidence"]>=.78

def test_odds_matching_requires_both_teams_and_close_kickoff():
    fixtures=[{"id":"fx1","kickoff":"2026-11-22T19:00:00+00:00","home":"Arsenal","away":"Chelsea"}]
    odds=[{"source_id":"od1","kickoff":"2026-11-22T19:05:00+00:00","home":"Arsenal","away":"Chelsea","odds":{"1X2":{}}}]
    assert match_fixtures_to_odds(fixtures,odds)["fx1"]["source_id"]=="od1"
    odds[0]["away"]="Chelsea U21"
    assert match_fixtures_to_odds(fixtures,odds)=={}

def test_team_similarity_handles_empty_and_exact_names():
    assert team_similarity("Arsenal FC","Arsenal")==1.0
    assert team_similarity("","Arsenal")==0.0



def test_extra_country_csv_uses_all_seasons_endpoint(monkeypatch):
    from goal_scanner import providers

    captured = {}
    payload = (
        "Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,HC,AC,HY,AY\n"
        "BRA,15/03/25,18:00,Flamengo,Palmeiras,2,1,6,4,2,3\n"
        "BRA,22/03/25,20:00,Santos,Gremio,1,1,5,5,1,1\n"
    ).encode("utf-8")

    def fake_http(url, timeout=20, headers=None):
        captured["url"] = url
        return payload

    monkeypatch.setattr(providers, "_http_bytes", fake_http)
    rows = providers.FootballDataProvider().fetch_season("2627", "BRA")

    assert captured["url"].endswith("/new/BRA.csv")
    assert len(rows) == 2
    assert rows[0]["home"] == "Flamengo"
    assert rows[0]["away_goals"] == 1
    assert rows[0]["season"] == "all"
    assert rows[0]["division"] == "BRA"
    assert rows[0]["statistics"]["HC"] == 6
