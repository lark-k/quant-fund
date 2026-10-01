from datetime import datetime

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.tests.test_nav_technical import history, request
from app.technical.backtest import replay, compare_rules
from app.technical.trend import APPROVED_CONFIG, APPROVED_VERSION, Policy, live_analysis
from research.replay import replay as research_replay
from research.trend import Config, Policy as ResearchPolicy


def cycle(i):
    if i < 220:
        return 1 + i * .005
    if i < 270:
        return 2.1 - (i - 220) * .015
    return 1.35 + (i - 270) * .014


@pytest.mark.parametrize('initial', [0, 100])
@pytest.mark.parametrize('delay', [1, 2])
def test_approved_engine_matches_research_execution(initial, delay):
    req = request(rows=history(cycle), ruleVersion=APPROVED_VERSION,
                  initialPositionPercent=initial, buyPercent=50, disclosureDelay=delay)
    result = replay(req)
    expected = research_replay(req, ResearchPolicy(Config(**Policy(APPROVED_CONFIG).parameters()), initial / 100))
    assert result['curve'] == expected['curve']
    assert result['metrics'] == expected['metrics']
    assert result['signals'] == expected['signals']
    assert [{k: v for k, v in t.items() if k not in ('evidence', 'reason')} for t in result['trades']] == [
        {k: v for k, v in t.items() if k not in ('evidence', 'reason')} for t in expected['trades']]
    assert result['ruleVersion'] == APPROVED_VERSION
    assert result['ruleId'] == 'T70-B2-F20-S18-I3'


def test_live_reconstructs_state_is_repeatable_and_never_claims_execution():
    rows = history(cycle)
    a = live_analysis(rows, 'MIXED', datetime(2026, 9, 30, 14))
    b = live_analysis(list(reversed(rows)), 'MIXED', datetime(2026, 9, 30, 14))
    assert a == b
    assert a['targetWeight'] == 100
    assert a['action'] == 'BUY'
    assert a['executionReady'] is False
    assert '仓位不足' in a['title']
    assert a['ruleParameters']['stop'] == .18
    assert 'buyBudget' not in a  # no fictional cash or executable order


def test_defense_does_not_sell_unheld_fund_or_claim_current_weight():
    rows = history(lambda i: 3 - i * .004)
    held = live_analysis(rows, 'MIXED', datetime(2026, 9, 30), has_holding=True)
    unheld = live_analysis(rows, 'MIXED', datetime(2026, 9, 30), has_holding=False)
    assert held['action'] == 'REDUCE' and held['targetWeight'] == 20
    assert '超出目标时' in held['title']
    assert unheld['action'] == 'WATCH'
    assert '不生成卖出指令' in unheld['explanation']


@pytest.mark.parametrize('rows,trading', [([], True), (history()[-119:], True), (history(), False)])
def test_live_data_gates_still_block(rows, trading):
    result = live_analysis(rows, 'MIXED', datetime(2026, 9, 30), trading)
    assert result['action'] == 'UNAVAILABLE'
    assert result['ruleVersion'] == APPROVED_VERSION
    assert 'targetWeight' not in result
    assert result['blockers']


def test_endpoint_version_and_three_way_comparison():
    client = TestClient(app)
    result = client.post('/api/v1/nav-technical/analyze', json=dict(
        rows=history(), fundType='MIXED', evaluatedAt='2026-09-30T14:00:00', trading=True)).json()
    assert result['ruleVersion'] == APPROVED_VERSION
    req = request(rows=history(cycle), ruleVersion=APPROVED_VERSION, initialPositionPercent=100, buyPercent=50)
    comparison = compare_rules(req)
    assert len(comparison['results']) == 9
    assert {r['ruleVersion'] for r in comparison['results']} == {'NAV-TA v1', 'NAV-TA v2-balanced', APPROVED_VERSION}
