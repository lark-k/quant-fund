from copy import deepcopy
from datetime import datetime

import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.tests.test_nav_technical import history
from app.technical.trend import live_analysis


def ledger(**overrides):
    return dict(cashBalance=1000, holdingShares=100, holdingAmount=200,
                pendingTrades=0, lastTradeDate=None, snapshotVersion=4,
                snapshotAt='2026-10-02T12:00:00') | overrides


def analyze(book=None, bear=False):
    rows = history((lambda i: 3 - i * .004) if bear else (lambda i: 1 + i * .005))
    return live_analysis(rows, 'MIXED', datetime(2026, 10, 2, 12), False, execution=book or ledger())


def test_buy_uses_only_allocated_cash_and_is_repeatable_without_mutation():
    book = ledger()
    before = deepcopy(book)
    a = analyze(book)
    assert a == analyze(book) and book == before
    assert a['action'] == 'BUY'
    p = a['execution']
    assert p['suggestedAmount'] == 500
    assert p['estimatedCashAfter'] == 500
    assert p['holdingAmount'] == pytest.approx(100 * p['referenceNav'])
    assert p['strategyAssets'] == pytest.approx(p['holdingAmount'] + 1000)
    assert p['targetWeight'] == 100 and p['pendingTrades'] == 0
    assert a['ruleId'] == 'T70-B2-F20-S18-I3'


def test_defense_sells_at_most_half_and_stops_at_target():
    a = analyze(ledger(cashBalance=0), bear=True)
    p = a['execution']
    assert a['action'] == 'REDUCE' and p['suggestedShares'] == 50
    assert p['suggestedAmount'] == pytest.approx(50 * p['referenceNav'])
    assert p['targetWeight'] == 20
    nav = p['referenceNav']
    # Only trim the excess when already near target, never blindly halve.
    near = analyze(ledger(cashBalance=100 * nav / .30 - 100 * nav), bear=True)['execution']
    assert near['suggestedShares'] == pytest.approx(33.3333)
    exact = analyze(ledger(cashBalance=100 * nav * 4), bear=True)['execution']
    assert exact['status'] == 'AT_TARGET' and exact['suggestedAmount'] == 0


@pytest.mark.parametrize('overrides,status', [
    ({'cashBalance': 0}, 'AT_TARGET'),
    ({'pendingTrades': 1}, 'PENDING_TRADE'),
    ({'cashBalance': -1}, 'CASH_DEFICIT'),
    ({'holdingShares': 0, 'holdingAmount': 100}, 'HOLDING_INCONSISTENT'),
    ({'holdingShares': 0, 'holdingAmount': 0, 'cashBalance': 0}, 'NO_CAPITAL'),
    ({'lastTradeDate': '2026-09-29'}, 'COOLDOWN'),
    ({'lastTradeDate': '2026-10-03'}, 'COOLDOWN'),
])
def test_non_executable_states_produce_no_amount(overrides, status):
    a = analyze(ledger(**overrides))
    assert a['execution']['status'] == status
    assert a['execution']['direction'] == 'NONE'
    assert a['execution']['suggestedAmount'] == 0
    assert not a['executionReady'] and a['action'] in ('HOLD', 'WATCH')


def test_interval_counts_new_nav_points_not_calendar_days():
    rows = history(lambda i: 1 + i * .005)
    assert analyze(ledger(lastTradeDate=rows[-3]['date']))['execution']['status'] == 'COOLDOWN'
    assert analyze(ledger(lastTradeDate=rows[-4]['date']))['execution']['direction'] == 'BUY'
    old = live_analysis(rows, 'QDII', datetime(2026, 10, 9), True,
                        execution=ledger(lastTradeDate=rows[-1]['date']))
    assert old['execution']['observationsSinceTrade'] == 0
    assert old['execution']['status'] == 'COOLDOWN'


def test_defensive_target_does_not_create_new_position():
    a = analyze(ledger(holdingShares=0, holdingAmount=0), bear=True)
    assert a['execution']['status'] == 'WAIT_TREND'
    assert a['execution']['direction'] == 'NONE'


def test_precision_never_overspends_cash_or_oversells_units():
    a = analyze(ledger(cashBalance=1000.01))['execution']
    assert a['suggestedAmount'] == 500
    s = analyze(ledger(cashBalance=0, holdingShares=100.1234), bear=True)['execution']
    assert s['suggestedShares'] <= 100.1234 / 2
    assert s['suggestedAmount'] <= s['suggestedShares'] * s['referenceNav'] + 1e-8
    assert analyze(ledger(cashBalance=1, holdingShares=0, holdingAmount=0))['execution']['status'] == 'BELOW_MINIMUM'


def test_api_accepts_context_and_keeps_data_quality_gates():
    client = TestClient(app)
    body = dict(rows=history(lambda i: 1 + i*.005), fundType='MIXED',
                evaluatedAt='2026-10-02T12:00:00', trading=False, execution=ledger())
    response = client.post('/api/v1/nav-technical/analyze', json=body)
    assert response.status_code == 200 and response.json()['execution']['suggestedAmount'] == 500
    body['rows'] = body['rows'][-100:]
    invalid = client.post('/api/v1/nav-technical/analyze', json=body).json()
    assert invalid['action'] == 'UNAVAILABLE' and 'execution' not in invalid
    body['execution']['holdingShares'] = -1
    assert client.post('/api/v1/nav-technical/analyze', json=body).status_code == 422
