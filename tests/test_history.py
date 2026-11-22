from goal_scanner.history import MatchRecord,estimate_team_rates,expected_goals
def test_history_excludes_future_matches():
    rows=[MatchRecord(1,"A","B",2,0),MatchRecord(3,"A","C",1,1)]
    result=estimate_team_rates(rows,"A",3)
    assert result["matches"]==1 and result["attack"]==2
def test_expected_goals_positive():
    assert all(x>0 for x in expected_goals(1.4,1.1,1.2,1.3))
