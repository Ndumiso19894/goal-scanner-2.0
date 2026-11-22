import pytest
from goal_scanner.odds import remove_margin,expected_value
def test_de_vig():
    p=remove_margin({"home":2.0,"draw":3.5,"away":4.0})
    assert sum(p.values())==pytest.approx(1)
def test_expected_value(): assert expected_value(.6,2)==pytest.approx(.2)
