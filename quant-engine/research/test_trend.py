from datetime import date, datetime
import pytest
from app.tests.test_nav_technical import history, request
from app.technical.backtest import replay as production_replay
from research.replay import replay
from research.trend import Config, Policy


def cyclical_request(**params):
    def value(i):
        if i < 220: return 1 + i*.005
        if i < 270: return 2.1 - (i-220)*.015
        return 1.35 + (i-270)*.014
    return request(rows=history(value), **dict(dict(initialPositionPercent=100, buyPercent=50, sellPercent=50, ruleVersion='NAV-TA v2-balanced'), **params))


@pytest.mark.parametrize('initial', [0, 100])
def test_research_without_policy_exactly_matches_production(initial):
    req = cyclical_request(initialPositionPercent=initial)
    assert replay(req) == production_replay(req)


def test_reentry_and_replenishment_do_not_need_rsi_cooldown():
    req = cyclical_request()
    result = replay(req, Policy(Config(period=60, stop=.2), 1))
    actions = [t['action'] for t in result['trades']]
    assert 'REDUCE' in actions
    assert actions.count('BUY') >= 2
    first_sale = next(i for i, action in enumerate(actions) if action == 'REDUCE')
    assert 'BUY' in actions[first_sale:]
    assert result['signals'][-1]['targetWeight'] == 100
    assert all(s.get('targetWeight') in (20., 100.) for s in result['signals'])
    assert result['curve'][-1]['holding']/result['curve'][-1]['equity'] > .9
    dates = [date.fromisoformat(t['executionDate']) for t in result['trades'][1:]]
    assert all((b-a).days >= 5 for a,b in zip(dates,dates[1:]))


def test_cash_conservation_and_all_recorded_fees():
    result = replay(cyclical_request(), Policy(Config(stop=.15), 1))
    assert result['metrics']['totalFees'] == pytest.approx(sum(t['fee'] for t in result['trades']), abs=.001)
    for p in result['curve']:
        assert p['cash'] >= -.0001 and p['holding'] >= 0 and p['unsettled'] >= 0
        assert p['equity'] == pytest.approx(p['cash']+p['holding']+p['unsettled'], abs=.0002)


def test_execution_nav_cannot_change_same_day_signal_or_order():
    req = cyclical_request(initialPositionPercent=0)
    first = replay(req, Policy(Config(stop=.2), 0))
    purchase = next(t for t in first['trades'] if t['action'] == 'BUY')
    changed = req.model_copy(deep=True)
    index = next(i for i,r in enumerate(changed.rows) if str(r.date) == purchase['executionDate'])
    changed.rows[index].nav *= 4
    changed.rows[index].dailyGrowthRate = 300
    second = replay(changed, Policy(Config(stop=.2), 0))
    assert [s for s in first['signals'] if s['date'] <= purchase['executionDate']] == [s for s in second['signals'] if s['date'] <= purchase['executionDate']]
    assert first['trades'][0]['amount'] == second['trades'][0]['amount']


def test_qdii_unpublished_purchase_nav_cannot_leak_through_position_sizing():
    req = cyclical_request(initialPositionPercent=0, disclosureDelay=2).model_copy(update={'fundType':'QDII'})
    first = replay(req, Policy(Config(stop=.2), 0))
    purchase = next(t for t in first['trades'] if t['action'] == 'BUY')
    changed = req.model_copy(deep=True)
    idx = next(i for i,r in enumerate(changed.rows) if str(r.date) == purchase['executionDate'])
    changed.rows[idx].nav *= 4
    changed.rows[idx].dailyGrowthRate = 300
    second = replay(changed, Policy(Config(stop=.2), 0))
    next_date = str(req.rows[idx+1].date)
    a = next(s for s in first['signals'] if s['date'] == next_date)
    b = next(s for s in second['signals'] if s['date'] == next_date)
    assert a['action'] == b['action'] and a['targetWeight'] == b['targetWeight']
    assert a['knownWeight'] == pytest.approx(b['knownWeight'], abs=1e-10)


def test_invalid_data_remains_blocked():
    req = cyclical_request()
    req.rows[100].dailyGrowthRate = None
    result = replay(req, Policy(Config(), 1))
    assert result['signals'][0]['action'] == 'UNAVAILABLE'
    assert result['signals'][0]['blockers']


def test_protection_does_not_reopen_on_long_ma_alone_before_recovery():
    rows = history(lambda i: 1 if i < 150 else 1+(i-150)*.04 if i < 180 else 2.2-(i-180)*.01, count=200)
    c = Config(period=70, stop=.18)
    current = Policy(c, .2)
    current.braked = True
    legacy = Policy(c, .2, recovery_confirmation=False)
    legacy.braked = True
    context = dict(index=200,cash=8000,units=2000,eligible=2000,unsettled=0,known_price=1,
                   buy_percent=50,sell_percent=50,fee_rate=.0015)
    a, plan = current.decide(rows,'MIXED',datetime(2026,9,30,10),True,**context)
    b, old_plan = legacy.decide(rows,'MIXED',datetime(2026,9,30,10),True,**context)
    assert a['action'] == 'HOLD' and plan['targetWeight'] == 20
    assert b['action'] == 'BUY' and old_plan['targetWeight'] == 100
