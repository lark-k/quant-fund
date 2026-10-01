from datetime import date, datetime, timedelta
from math import sin
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.api.technical import ReplayRequest
from app.technical.rules import analyze
from app.technical.backtest import replay, compare_rules


def history(value=lambda i: 1+i*.001+.012*sin(i*.7+2), count=400):
    dates, day = [], date(2026, 9, 29)
    while len(dates) < count:
        if day.weekday() < 5:
            dates.insert(0, day)
        day -= timedelta(days=1)
    return [dict(date=d.isoformat(), nav=value(i), dailyGrowthRate=(value(i)/value(i-1)-1)*100 if i else None, sourceName='TEST') for i, d in enumerate(dates)]


def request(rows=None, **kwargs):
    rows = rows or history()
    return ReplayRequest(rows=rows, fundCode='021180', fundType='MIXED',
                         startDate=rows[150]['date'], endDate=rows[-1]['date'],
                         tradingDates=[r['date'] for r in rows], **kwargs)


@pytest.mark.parametrize('fund_type', ['MIXED', '混合型', 'ACTIVE_EQUITY', 'INDEX', 'INDEX_ENHANCED', 'ETF', 'ETF_LINK', 'QDII'])
def test_migration_matches_original_typescript_numeric_fixture(fund_type):
    result = analyze(history(count=200), fund_type, datetime(2026, 9, 30, 14))
    assert result['action'] == 'BUY'
    assert 'MA20 1.1900' in result['evidence'][0]['value']
    assert result['evidence'][1]['value'] == 'DIF 0.0082 · DEA 0.0074 · 柱 0.0016'
    assert 'RSI14 59.13' in result['evidence'][3]['value']


@pytest.mark.parametrize('value,action', [(lambda i: 2-i*.003, 'REDUCE'), (lambda i: 1+i*.004, 'HOLD'), (lambda i: 1, 'WATCH'), (lambda i: 1+i*.001+.012*sin(i*.7+1), 'HOLD'), (lambda i: 1+i*.001+.012*sin(i*.7), 'WATCH')])
def test_migration_action_parity(value, action):
    assert analyze(history(value, 200), 'MIXED', datetime(2026, 9, 30, 14))['action'] == action


def test_data_gates_and_split_adjustment():
    rows = history(count=200)
    result = analyze(rows, 'MIXED', datetime(2026, 9, 30, 14))
    split = [{**r, 'nav': r['nav']*(2 if i < 190 else 1)} for i, r in enumerate(rows)]
    assert analyze(split, 'MIXED', datetime(2026, 9, 30, 14)) == result
    for ft in ['', 'UNKNOWN', 'BOND', 'MONEY_MARKET', 'FIXED_INCOME_PLUS', 'QDII债券']:
        assert analyze(rows, ft, datetime(2026, 9, 30))['action'] == 'UNAVAILABLE'
    assert analyze(rows[-119:], 'MIXED', datetime(2026, 9, 30))['action'] == 'UNAVAILABLE'
    assert analyze(rows, 'MIXED', datetime(2026, 10, 6))['action'] == 'UNAVAILABLE'
    assert analyze(rows, 'MIXED', datetime(2026, 9, 30), False)['action'] == 'UNAVAILABLE'
    rows[-2]['dailyGrowthRate'] = None
    assert analyze(rows, 'MIXED', datetime(2026, 9, 30))['action'] == 'UNAVAILABLE'


def test_replay_no_future_leak_and_live_rule_parity():
    req = request()
    original = replay(req)
    assert original['metrics']['tradeCount'] > 0
    rows = [r.model_dump(mode='json') for r in req.rows]
    rows[-1]['nav'] *= 8
    rows[-1]['dailyGrowthRate'] = 700
    changed = replay(req.model_copy(update={'rows': ReplayRequest.model_validate({**req.model_dump(), 'rows': rows}).rows}))
    assert changed['signals'] == original['signals']  # Last execution NAV never enters its own signal.
    assert changed['curve'][:-1] == original['curve'][:-1]
    for entry in original['signals'][::23]:
        available = [r for r in rows if r['date'] < entry['date']]
        live = analyze(available, 'MIXED', datetime.fromisoformat(entry['date']+'T10:00:00'))
        assert entry['action'] == live['action']
        assert entry['conditions'] == live['conditions']
    assert all(t['navAsOf'] < t['executionDate'] for t in original['trades'])


def test_cash_conservation_fees_and_benchmark():
    result = replay(request())
    assert result['metrics']['totalFees'] == pytest.approx(sum(t['fee'] for t in result['trades']), abs=.001)
    for p in result['curve']:
        assert p['cash'] >= -0.0001
        assert p['holding'] >= -0.0001
        assert p['equity'] == pytest.approx(p['cash']+p['unsettled']+p['holding'], abs=.0002)
        assert p['drawdown'] <= 0
    assert result['curve'][0]['benchmark'] == pytest.approx(10000/1.0015, abs=.0001)
    assert len(result['dataHash']) == 64
    assert result['metrics']['annualReturn'] is None  # 250 observations span less than a year.


def test_missing_returns_duplicates_and_warmup_fail_explicitly():
    req = request()
    req.rows[-2].dailyGrowthRate = None
    with pytest.raises(ValueError, match='收益率'):
        replay(req)
    req = request()
    req.rows.append(req.rows[-1])
    with pytest.raises(ValueError, match='重复'):
        replay(req)
    with pytest.raises(ValueError, match='120'):
        replay(request().model_copy(update={'startDate': date(2025, 3, 20)}))


def test_fifo_redemption_and_settlement_lock(monkeypatch):
    import app.technical.backtest as module
    req = request(buyPercent=100, sellPercent=100, buyFee=0, shortSellFee=1.5, settlementDelay=5)
    dates = [r.date.isoformat() for r in req.rows if r.date >= req.startDate]
    schedule = {dates[0]: 'BUY', dates[1]: 'REDUCE', dates[2]: 'BUY', dates[7]: 'BUY'}
    def forced(rows, ft, now, trading):
        return dict(action=schedule.get(now.date().isoformat(), 'WATCH'), asOf=rows[-1]['date'], blockers=[], conditions={}, explanation='TEST', evidence=[])
    monkeypatch.setattr(module, 'analyze', forced)
    result = replay(req)
    assert [t['action'] for t in result['trades']] == ['BUY', 'REDUCE', 'BUY']
    sale = result['trades'][1]
    assert sale['fee'] == pytest.approx(sale['amount']*.015, abs=.0001)
    assert result['curve'][2]['cash'] == 0
    assert result['curve'][2]['unsettled'] > 0
    assert result['skippedTrades']['可用现金不足 1 元'] == 1


def test_continuous_signal_does_not_trade_every_day(monkeypatch):
    import app.technical.backtest as module
    monkeypatch.setattr(module, 'analyze', lambda rows, ft, now, trading: dict(action='BUY', asOf=rows[-1]['date'], blockers=[], conditions={}, explanation='TEST', evidence=[]))
    assert replay(request())['metrics']['tradeCount'] == 1


def test_api_validation():
    client = TestClient(app)
    payload = request().model_dump(mode='json')
    assert client.post('/api/v1/nav-technical/backtest', json={**payload, 'disclosureDelay': 0}).status_code == 422
    assert client.post('/api/v1/nav-technical/backtest', json={**payload, 'fundType': 'QDII', 'disclosureDelay': 1}).status_code == 422
    assert client.post('/api/v1/nav-technical/backtest', json={**payload, 'sellPercent': 101}).status_code == 422
    result = client.post('/api/v1/nav-technical/backtest', json=payload)
    assert result.status_code == 200
    assert result.json()['ruleVersion'] == 'NAV-TA v1'


def test_qdii_delay_preserves_strict_v1_gates_and_exposes_unverifiable_result():
    req = request(disclosureDelay=2).model_copy(update={'fundType': 'QDII'})
    result = replay(req)
    assert result['validationStatus'] == 'NO_ELIGIBLE_DAYS'
    assert result['metrics']['analyzedDays'] == 0
    assert result['metrics']['tradeCount'] == 0
    assert result['blockedReasons']


def test_initial_allocation_is_fee_paying_confirmed_later_and_not_rule_signal(monkeypatch):
    import app.technical.backtest as module
    monkeypatch.setattr(module, 'analyze', lambda rows, ft, now, trading: dict(action='REDUCE', asOf=rows[-1]['date'], blockers=[], conditions={}, explanation='TEST', evidence=[]))
    result = replay(request(initialPositionPercent=50))
    assert result['metrics']['initialTradeCount'] == 1
    assert result['metrics']['signalTradeCount'] == 0  # Not confirmed on day one; continuous sell is not retried.
    assert result['trades'][0]['action'] == 'INITIAL_BUY'
    assert result['curve'][0]['cash'] == 5000
    assert result['curve'][0]['holding'] == pytest.approx(5000/1.0015, abs=.0001)
    assert result['metrics']['totalFees'] == pytest.approx(5000-5000/1.0015, abs=.0001)
    assert result['metrics']['totalReturn'] != 0
    for point in result['curve']:
        assert point['equity'] == pytest.approx(point['cash']+point['holding']+point['unsettled'], abs=.0002)


def test_candidate_uses_declared_qdii_delay_without_disabling_other_data_gates():
    req = request(disclosureDelay=2, ruleVersion='NAV-TA v2-balanced').model_copy(update={'fundType': 'QDII'})
    assert replay(req)['metrics']['analyzedDays'] > 0
    rows = history(count=200)
    assert analyze(rows, 'QDII', datetime(2026, 10, 10), rule_version='NAV-TA v2-balanced')['action'] == 'UNAVAILABLE'
    rows[-1]['dailyGrowthRate'] = None
    assert analyze(rows, 'QDII', datetime(2026, 9, 30), rule_version='NAV-TA v2-balanced')['action'] == 'UNAVAILABLE'


def test_candidate_prefix_signals_do_not_use_future_and_live_v1_remains_default():
    req = request(ruleVersion='NAV-TA v2-balanced')
    result = replay(req)
    changed = req.model_copy(deep=True)
    changed.rows[-1].nav *= 5
    changed.rows[-1].dailyGrowthRate = 400
    assert replay(changed)['signals'] == result['signals']
    assert 'buyWeek' not in next(s['conditions'] for s in result['signals'] if s['conditions'])
    assert analyze(history(count=200), 'MIXED', datetime(2026, 9, 30))['ruleVersion'] == 'NAV-TA v1'


def test_comparison_fixed_split_and_no_holdout_leak_into_reference():
    req = request()
    result = compare_rules(req)
    assert result['status'] == 'AVAILABLE'
    assert len(result['results']) == 6
    refs = [r for r in result['results'] if r['segment'] == 'REFERENCE']
    tails = [r for r in result['results'] if r['segment'] == 'HOLDOUT']
    assert refs[0]['endDate'] < tails[0]['startDate']
    changed = req.model_copy(deep=True)
    for row in changed.rows:
        if row.date.isoformat() >= tails[0]['startDate']:
            row.nav *= 2
            row.dailyGrowthRate = 2
    assert [r for r in compare_rules(changed)['results'] if r['segment'] == 'REFERENCE'] == refs
    short = req.model_copy(update={'startDate': req.rows[-50].date})
    assert compare_rules(short)['status'] == 'INSUFFICIENT'


def test_new_parameter_validation_and_legacy_defaults():
    client = TestClient(app)
    payload = request().model_dump(mode='json')
    for change in ({'initialPositionPercent': 101}, {'ruleVersion': 'best-profit'}, {'initialPositionPercent': -1}):
        assert client.post('/api/v1/nav-technical/backtest', json={**payload, **change}).status_code == 422
    payload.pop('initialPositionPercent'); payload.pop('ruleVersion')
    req = ReplayRequest.model_validate(payload)
    assert req.initialPositionPercent == 0 and req.ruleVersion == 'NAV-TA v1'
