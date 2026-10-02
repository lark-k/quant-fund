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


@pytest.mark.parametrize('rows,trading', [([], True), (history()[-119:], False)])
def test_live_data_gates_still_block(rows, trading):
    result = live_analysis(rows, 'MIXED', datetime(2026, 9, 30), trading)
    assert result['action'] == 'UNAVAILABLE'
    assert result['ruleVersion'] == APPROVED_VERSION
    assert 'targetWeight' not in result
    assert result['blockers']


@pytest.mark.parametrize('evaluated_at,trading,fund_type', [
    ('2026-10-02T17:00:00', False, 'MIXED'),  # National Day holiday
    ('2026-10-04T10:00:00', False, 'MIXED'),  # Sunday
    ('2026-10-09T10:00:00', True, 'MIXED'),   # first session after holiday
    ('2026-10-02T10:00:00', True, 'QDII'),    # delayed overseas NAV
    ('2026-11-02T10:00:00', True, 'MIXED'),   # old data is explicit, not disguised as today's NAV
])
def test_asof_view_works_after_holidays_or_delayed_disclosure(evaluated_at, trading, fund_type):
    rows = history(cycle)
    client = TestClient(app)
    response = client.post('/api/v1/nav-technical/analyze', json=dict(
        rows=rows, fundType=fund_type, evaluatedAt=evaluated_at, trading=trading))
    assert response.status_code == 200
    result = response.json()
    assert result['action'] != 'UNAVAILABLE'
    assert result['asOf'] == rows[-1]['date']
    assert result['evaluatedAt'] == evaluated_at.replace('T', ' ')
    assert not result['blockers'] and result['evidence']
    assert rows[-1]['date'] in result['timingNotice']
    assert '不含此后的行情' in result['timingNotice']
    if not trading:
        assert '可查看分析' in result['timingNotice']
    if fund_type == 'QDII':
        assert '公布日期可能不同' in result['timingNotice']
    assert result['executionReady'] is False
    baseline = live_analysis(rows, fund_type, datetime(2026, 9, 30, 14))
    for key in ['action', 'targetWeight', 'trendState']:
        assert result[key] == baseline[key]


@pytest.mark.parametrize('problem', ['future', 'missing_return', 'duplicate', 'invalid_nav', 'gap', 'unsupported'])
def test_relaxed_view_preserves_data_integrity_checks(problem):
    rows = history(cycle)
    fund_type = 'MIXED'
    if problem == 'future': rows[-1]['date'] = '2026-10-10'
    if problem == 'missing_return': rows[-1]['dailyGrowthRate'] = None
    if problem == 'duplicate': rows.append({**rows[-1], 'nav': rows[-1]['nav'] * 2})
    if problem == 'invalid_nav': rows[-1]['nav'] = 0
    if problem == 'gap': del rows[-30:-10]
    if problem == 'unsupported': fund_type = 'MONEY_MARKET'
    result = live_analysis(rows, fund_type, datetime(2026, 10, 2), False)
    assert result['action'] == 'UNAVAILABLE'
    assert result['blockers'] and 'targetWeight' not in result


def test_view_does_not_relax_replay_calendar_or_freshness_and_cache_is_separate():
    rows = history(cycle)
    policy = Policy(APPROVED_CONFIG)
    context = dict(index=400, cash=0, units=0, eligible=0, unsettled=0,
                   known_price=None, buy_percent=50, sell_percent=50, fee_rate=.0015)
    for trading in (False, True):
        view, _ = policy.decide(rows, 'MIXED', datetime(2026, 10, 9), trading, enforce_timing=False, **context)
        replay_signal, _ = policy.decide(rows, 'MIXED', datetime(2026, 10, 9), trading, **context)
        assert view['action'] != 'UNAVAILABLE'
        assert replay_signal['action'] == 'UNAVAILABLE'
        assert any('新鲜度' in b for b in replay_signal['blockers'])
        if not trading:
            assert any('非交易日' in b for b in replay_signal['blockers'])


def test_endpoint_version_and_three_way_comparison():
    client = TestClient(app)
    result = client.post('/api/v1/nav-technical/analyze', json=dict(
        rows=history(), fundType='MIXED', evaluatedAt='2026-09-30T14:00:00', trading=True)).json()
    assert result['ruleVersion'] == APPROVED_VERSION
    req = request(rows=history(cycle), ruleVersion=APPROVED_VERSION, initialPositionPercent=100, buyPercent=50)
    comparison = compare_rules(req)
    assert len(comparison['results']) == 9
    assert {r['ruleVersion'] for r in comparison['results']} == {'NAV-TA v1', 'NAV-TA v2-balanced', APPROVED_VERSION}
