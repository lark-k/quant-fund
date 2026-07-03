from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.backtest.engine import (
    _benchmark_alignment_buy,
    _core_trend_allocation_buy,
    _early_trend_bootstrap_buy,
    _is_passed,
    _midterm_trend_buy,
    _orderly_trend_hold,
    _recoverable_pullback_buy,
    _should_hold_trend,
    _strong_reentry_buy,
    _trend_repair_reentry_buy,
    _underposition_recoverable_buy,
    _weak_trend_defense,
    _weak_trend_recovered,
)
from app.core.schemas import BacktestStrategyParams
from app.main import app
from app.strategies.fund_profile import resolve_fund_profile


client = TestClient(app)


def make_nav_series(days: int = 90):
    nav = 1.0
    rows = []
    for index in range(days):
        nav *= 1 + (0.002 if index % 7 != 0 else -0.001)
        rows.append(
            {
                "date": f"2025-01-{index + 1:02d}" if index < 31 else f"2025-02-{index - 30:02d}" if index < 59 else f"2025-03-{index - 58:02d}",
                "nav": round(nav, 6),
                "accumulatedNav": round(nav, 6),
                "dailyGrowthRate": 0.2 if index % 7 != 0 else -0.1,
            }
        )
    return rows


def make_strong_trend_series(days: int = 140):
    nav = 1.0
    rows = []
    for index in range(days):
        step = 0.006
        if index in {70, 95, 120}:
            step = -0.035
        nav *= 1 + step
        rows.append(
            {
                "date": f"2025-01-01",
                "nav": round(nav, 6),
                "accumulatedNav": round(nav, 6),
                "dailyGrowthRate": round(step * 100, 4),
            }
        )
    return _dated(rows)


def make_weak_trend_series(days: int = 140):
    nav = 1.3
    rows = []
    for index in range(days):
        step = -0.004 if index % 5 != 0 else 0.001
        nav *= 1 + step
        rows.append(
            {
                "date": f"2025-01-01",
                "nav": round(nav, 6),
                "accumulatedNav": round(nav, 6),
                "dailyGrowthRate": round(step * 100, 4),
            }
        )
    return _dated(rows)


def make_reversal_weak_trend_series(days: int = 160):
    nav = 1.0
    rows = []
    for index in range(days):
        if index < 45:
            step = 0.006
        else:
            step = -0.006 if index % 5 != 0 else 0.001
        nav *= 1 + step
        rows.append(
            {
                "date": f"2025-01-01",
                "nav": round(nav, 6),
                "accumulatedNav": round(nav, 6),
                "dailyGrowthRate": round(step * 100, 4),
            }
        )
    return _dated(rows)


def make_weak_then_shallow_restart_series(days: int = 180):
    nav = 1.0
    rows = []
    for index in range(days):
        if index < 35:
            step = 0.006
        elif index < 85:
            step = -0.008 if index % 5 != 0 else 0.001
        else:
            step = 0.0025 if index % 4 != 0 else -0.001
        nav *= 1 + step
        rows.append(
            {
                "date": f"2025-01-01",
                "nav": round(nav, 6),
                "accumulatedNav": round(nav, 6),
                "dailyGrowthRate": round(step * 100, 4),
            }
        )
    return _dated(rows)


def make_midterm_pullback_series(days: int = 130):
    nav = 1.0
    rows = []
    for index in range(days):
        if index < 80:
            step = 0.006
        elif index < 88:
            step = -0.02
        else:
            step = 0.001
        nav *= 1 + step
        rows.append(
            {
                "date": f"2025-01-01",
                "nav": round(nav, 6),
                "accumulatedNav": round(nav, 6),
                "dailyGrowthRate": round(step * 100, 4),
            }
        )
    return _dated(rows)


def _dated(rows):
    from datetime import date, timedelta

    start = date(2025, 1, 1)
    for index, row in enumerate(rows):
        row["date"] = (start + timedelta(days=index)).isoformat()
    return rows


def test_backtest_run_returns_metrics():
    response = client.post(
        "/api/v1/backtest/run",
        json={
            "fundCode": "025833",
            "fundName": "Test Fund",
            "fundType": "INDEX",
            "startDate": "2025-01-01",
            "endDate": "2025-03-31",
            "initialCash": 10000,
            "navSeries": make_nav_series(),
            "strategyParams": {"buyThreshold": 55, "sellThreshold": 35, "maxSinglePositionRate": 25},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["fundCode"] == "025833"
    assert body["navSampleSize"] >= 80
    assert "totalReturnRate" in body
    assert "maxDrawdownRate" in body
    assert "positionBenchmarkReturnRate" in body
    assert "positionExcessReturnRate" in body
    assert isinstance(body["equityCurve"], list)


def test_backtest_allows_qdii_fund_type():
    response = client.post(
        "/api/v1/backtest/run",
        json={
            "fundCode": "013403",
            "fundName": "QDII Fund",
            "fundType": "QDII",
            "startDate": "2025-01-01",
            "endDate": "2025-03-31",
            "initialCash": 10000,
            "navSeries": make_nav_series(),
            "strategyParams": {
                "buyThreshold": 55,
                "sellThreshold": 12,
                "maxSinglePositionRate": 45,
            },
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["fundCode"] == "013403"
    assert body["fundType"] == "QDII"
    assert body["navSampleSize"] >= 80
    assert isinstance(body["trades"], list)


def test_backtest_warmup_keeps_requested_start_date():
    response = client.post(
        "/api/v1/backtest/run",
        json={
            "fundCode": "025833",
            "fundName": "Test Fund",
            "fundType": "INDEX",
            "startDate": "2025-02-15",
            "endDate": "2025-03-31",
            "initialCash": 10000,
            "navSeries": make_nav_series(),
            "strategyParams": {
                "buyThreshold": 55,
                "sellThreshold": 35,
                "maxSinglePositionRate": 25,
                "warmupDays": 45,
            },
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["startDate"] == "2025-02-15"
    assert body["equityCurve"][0]["date"] == "2025-02-15"
    assert body["navSampleSize"] == len(body["equityCurve"])


def test_backtest_run_batch_returns_summary():
    fund = {
        "fundCode": "025833",
        "fundName": "Test Fund",
        "fundType": "INDEX",
        "navSeries": make_nav_series(),
    }

    response = client.post(
        "/api/v1/backtest/run-batch",
        json={
            "taskName": "pytest-batch",
            "startDate": "2025-01-01",
            "endDate": "2025-03-31",
            "funds": [fund, fund],
            "strategyParams": {"buyThreshold": 55, "sellThreshold": 35},
            "options": {"workers": 2, "saveEquityCurve": False, "saveTrades": True},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["successCount"] == 2
    assert body["summary"]["diagnosis"]
    assert "outperformPositionBenchmarkRate" in body["summary"]
    assert body["results"][0]["equityCurve"] == []


def test_backtest_strong_trend_lock_reduces_profit_sells():
    response = client.post(
        "/api/v1/backtest/run",
        json={
            "fundCode": "021528",
            "fundName": "Strong Trend Fund",
            "fundType": "ACTIVE_EQUITY",
            "startDate": "2025-01-01",
            "endDate": "2025-05-20",
            "initialCash": 10000,
            "navSeries": make_strong_trend_series(),
            "strategyParams": {
                "buyThreshold": 55,
                "sellThreshold": 12,
                "takeProfitRate": 300,
                "stopLossRate": -18,
                "maxSinglePositionRate": 45,
                "buyStepRatio": 15,
                "sellStepRatio": 10,
                "trendHoldReturn20d": 2,
                "trendHoldMa20Deviation": -6,
            },
        },
    )

    assert response.status_code == 200
    trades = response.json()["trades"]
    assert len(trades) <= 5
    assert "trend_start_buy" in [item["reason"] for item in trades]
    assert trades[0]["amount"] >= 4000
    assert "positionRateBefore" in trades[0]
    assert "positionRateAfter" in trades[0]
    assert "return20d" in trades[0]
    assert "ma20Deviation" in trades[0]
    assert "riskScore" in trades[0]
    assert [item["reason"] for item in trades].count("profit_exit") <= 1


def test_backtest_strong_trend_lock_uses_trend_hold_params():
    base_payload = {
        "fundCode": "021528",
        "fundName": "Strong Trend Fund",
        "fundType": "ACTIVE_EQUITY",
        "startDate": "2025-01-01",
        "endDate": "2025-05-20",
        "initialCash": 10000,
        "navSeries": make_strong_trend_series(),
    }
    permissive = client.post(
        "/api/v1/backtest/run",
        json={
            **base_payload,
            "strategyParams": {
                "buyThreshold": 90,
                "sellThreshold": 8,
                "maxSinglePositionRate": 45,
                "buyStepRatio": 15,
                "trendHoldReturn20d": 2,
                "trendHoldMa20Deviation": -6,
            },
        },
    )
    strict = client.post(
        "/api/v1/backtest/run",
        json={
            **base_payload,
            "strategyParams": {
                "buyThreshold": 90,
                "sellThreshold": 8,
                "maxSinglePositionRate": 45,
                "buyStepRatio": 15,
                "trendHoldReturn20d": 50,
                "trendHoldMa20Deviation": 5,
            },
        },
    )

    assert permissive.status_code == 200
    assert strict.status_code == 200
    permissive_reasons = [item["reason"] for item in permissive.json()["trades"]]
    strict_reasons = [item["reason"] for item in strict.json()["trades"]]
    assert any(reason in {"trend_start_buy", "strong_trend_buy"} for reason in permissive_reasons)
    assert not any(reason in {"trend_start_buy", "strong_trend_buy"} for reason in strict_reasons)


def test_backtest_midterm_strong_pullback_does_not_trigger_weak_defense_sell():
    response = client.post(
        "/api/v1/backtest/run",
        json={
            "fundCode": "021528",
            "fundName": "Midterm Pullback Fund",
            "fundType": "ACTIVE_EQUITY",
            "startDate": "2025-01-01",
            "endDate": "2025-05-10",
            "initialCash": 10000,
            "navSeries": make_midterm_pullback_series(),
            "strategyParams": {
                "buyThreshold": 55,
                "sellThreshold": 12,
                "takeProfitRate": 300,
                "stopLossRate": -18,
                "maxSinglePositionRate": 45,
                "buyStepRatio": 15,
                "sellStepRatio": 15,
                "trendHoldReturn20d": 2,
                "trendHoldMa20Deviation": -6,
            },
        },
    )

    assert response.status_code == 200
    reasons = [item["reason"] for item in response.json()["trades"]]
    assert "weak_trend_defense" not in reasons


def test_backtest_midterm_pullback_counts_as_trend_hold_and_buy_setup():
    row = SimpleNamespace(
        return5d=0.5,
        return20d=-3.0,
        return60d=18.0,
        ma20Deviation=-5.5,
        maxDrawdown60d=-14.0,
        riskScore=35.0,
        lossDayRatio20d=55.0,
    )

    assert _should_hold_trend(row, BacktestStrategyParams())
    assert _midterm_trend_buy(row)


def test_backtest_midterm_buy_requires_short_term_stability():
    row = SimpleNamespace(
        return5d=-2.4,
        return20d=-1.5,
        return60d=15.1,
        ma20Deviation=-1.4,
        maxDrawdown60d=-5.4,
        riskScore=57.0,
        lossDayRatio20d=56.0,
    )

    assert _should_hold_trend(row, BacktestStrategyParams())
    assert not _midterm_trend_buy(row)


def test_backtest_recoverable_pullback_does_not_trigger_weak_defense():
    row = SimpleNamespace(
        return20d=-12.6,
        return60d=-2.1,
        ma20Deviation=-12.1,
        maxDrawdown60d=-16.9,
        trendScore=12.0,
        opportunityScore=70.0,
        riskScore=34.5,
    )

    assert not _weak_trend_defense(row)


def test_backtest_wide_recoverable_pullback_matches_volatile_active_fund():
    row = SimpleNamespace(
        return20d=-15.3,
        return60d=-6.5,
        ma20Deviation=-10.9,
        maxDrawdown60d=-16.4,
        trendScore=8.0,
        opportunityScore=68.0,
        riskScore=36.0,
    )

    assert not _weak_trend_defense(row)


def test_backtest_weak_trend_defense_marks_sell_reason():
    response = client.post(
        "/api/v1/backtest/run",
        json={
            "fundCode": "013403",
            "fundName": "Weak Trend Fund",
            "fundType": "ETF",
            "startDate": "2025-01-01",
            "endDate": "2025-06-09",
            "initialCash": 10000,
            "navSeries": make_reversal_weak_trend_series(),
            "strategyParams": {
                "buyThreshold": 55,
                "sellThreshold": 12,
                "maxSinglePositionRate": 45,
                "buyStepRatio": 15,
                "sellStepRatio": 15,
                "takeProfitRate": 300,
                "stopLossRate": -18,
                "minNavSamples": 20,
                "warmupDays": 90,
                "trendHoldReturn20d": 2,
                "trendHoldMa20Deviation": -6,
            },
        },
    )

    assert response.status_code == 200
    reasons = [item["reason"] for item in response.json()["trades"]]
    assert "weak_trend_defense" in reasons
    assert reasons.count("weak_trend_defense") <= 1
    weak_trade = next(item for item in response.json()["trades"] if item["reason"] == "weak_trend_defense")
    assert weak_trade["tradeRatio"] == 15
    assert weak_trade["return20d"] <= -6
    assert weak_trade["return60d"] <= 0


def test_backtest_weak_trend_defense_sells_only_once_before_extreme_risk():
    response = client.post(
        "/api/v1/backtest/run",
        json={
            "fundCode": "013403",
            "fundName": "Repeated Weak Trend Fund",
            "fundType": "QDII",
            "startDate": "2025-01-01",
            "endDate": "2025-06-09",
            "initialCash": 10000,
            "navSeries": make_reversal_weak_trend_series(),
            "strategyParams": {
                "buyThreshold": 55,
                "sellThreshold": 12,
                "maxSinglePositionRate": 45,
                "buyStepRatio": 15,
                "sellStepRatio": 15,
                "takeProfitRate": 300,
                "stopLossRate": -18,
                "minNavSamples": 20,
                "warmupDays": 90,
                "trendHoldReturn20d": 2,
                "trendHoldMa20Deviation": -6,
            },
        },
    )

    assert response.status_code == 200
    trades = response.json()["trades"]
    reasons = [item["reason"] for item in trades]
    assert reasons.count("weak_trend_defense") <= 1
    extreme_trade = next(item for item in trades if item["reason"] == "extreme_risk_exit")
    assert extreme_trade["tradeRatio"] == 100
    assert extreme_trade["positionRateAfter"] == 0


def test_backtest_weak_recovery_blocks_shallow_trend_restart_buy():
    response = client.post(
        "/api/v1/backtest/run",
        json={
            "fundCode": "013403",
            "fundName": "Weak Restart Fund",
            "fundType": "ACTIVE_EQUITY",
            "startDate": "2025-01-01",
            "endDate": "2025-06-29",
            "initialCash": 10000,
            "navSeries": make_weak_then_shallow_restart_series(),
            "strategyParams": {
                "buyThreshold": 90,
                "sellThreshold": 12,
                "maxSinglePositionRate": 45,
                "buyStepRatio": 15,
                "sellStepRatio": 15,
                "takeProfitRate": 300,
                "stopLossRate": -18,
                "minNavSamples": 20,
                "warmupDays": 90,
                "trendHoldReturn20d": 2,
                "trendHoldMa20Deviation": -6,
            },
        },
    )

    assert response.status_code == 200
    trades = response.json()["trades"]
    weak_sell_index = next(index for index, item in enumerate(trades) if item["reason"] == "weak_trend_defense")
    after_weak_sell = trades[weak_sell_index + 1 :]
    assert not any(item["reason"] == "trend_start_buy" for item in after_weak_sell)
    assert not any(item["reason"] == "recoverable_pullback_buy" for item in after_weak_sell)


def test_strong_reentry_buy_requires_real_recovery_not_shallow_rebound():
    strong = SimpleNamespace(
        return20d=3.2,
        return60d=12.5,
        ma20Deviation=-1.5,
        maxDrawdown60d=-9.0,
        trendScore=66,
        riskScore=42,
        lossDayRatio20d=45,
    )
    shallow = SimpleNamespace(
        return20d=1.2,
        return60d=4.8,
        ma20Deviation=-5.5,
        maxDrawdown60d=-16.0,
        trendScore=53,
        riskScore=38,
        lossDayRatio20d=54,
    )

    assert _strong_reentry_buy(strong, BacktestStrategyParams())
    assert not _strong_reentry_buy(shallow, BacktestStrategyParams())


def test_weak_trend_recovery_requires_confirmed_repair():
    recovered = SimpleNamespace(
        return20d=1.8,
        return60d=6.2,
        ma20Deviation=-5.5,
        maxDrawdown60d=-12.0,
        trendScore=58,
        riskScore=36,
        lossDayRatio20d=52,
    )
    shallow = SimpleNamespace(
        return20d=1.4,
        return60d=4.2,
        ma20Deviation=-5.5,
        maxDrawdown60d=-12.0,
        trendScore=54,
        riskScore=36,
        lossDayRatio20d=52,
    )

    assert _weak_trend_recovered(recovered, BacktestStrategyParams())
    assert not _weak_trend_recovered(shallow, BacktestStrategyParams())


def test_recoverable_pullback_buy_requires_stabilized_short_term():
    stabilized = SimpleNamespace(
        return5d=0.3,
        return20d=-4.0,
        return60d=-2.0,
        ma20Deviation=-8.0,
        maxDrawdown60d=-12.0,
        trendScore=48,
        opportunityScore=70,
        riskScore=42,
        lossDayRatio20d=55,
    )
    falling = SimpleNamespace(
        return5d=-0.4,
        return20d=-4.0,
        return60d=-2.0,
        ma20Deviation=-8.0,
        maxDrawdown60d=-12.0,
        trendScore=48,
        opportunityScore=70,
        riskScore=42,
        lossDayRatio20d=55,
    )

    assert _recoverable_pullback_buy(stabilized)
    assert not _recoverable_pullback_buy(falling)


def test_underposition_recoverable_buy_only_applies_before_half_position():
    params = BacktestStrategyParams(maxSinglePositionRate=45)

    assert _underposition_recoverable_buy(True, 12, params)
    assert _underposition_recoverable_buy(True, 22.5, params)
    assert not _underposition_recoverable_buy(True, 28, params)
    assert not _underposition_recoverable_buy(False, 12, params)


def test_benchmark_alignment_buy_tops_up_underweight_orderly_trend():
    row = SimpleNamespace(
        totalScore=49.0,
        trendScore=46.0,
        opportunityScore=52.0,
        riskScore=35.0,
        return5d=0.2,
        return20d=0.4,
        return60d=5.5,
        ma20Deviation=-7.0,
        maxDrawdown60d=-10.0,
        lossDayRatio20d=55.0,
    )

    assert _benchmark_alignment_buy(row, BacktestStrategyParams(maxSinglePositionRate=45), 18)


def test_benchmark_alignment_buy_stops_near_target_position():
    row = SimpleNamespace(
        totalScore=60.0,
        trendScore=58.0,
        opportunityScore=60.0,
        riskScore=45.0,
        return5d=0.2,
        return20d=1.2,
        return60d=8.0,
        ma20Deviation=-4.0,
        maxDrawdown60d=-8.0,
        lossDayRatio20d=45.0,
    )

    assert not _benchmark_alignment_buy(row, BacktestStrategyParams(maxSinglePositionRate=45), 41)


def test_core_trend_allocation_buy_fills_underweight_confirmed_trend():
    row = SimpleNamespace(
        totalScore=46.0,
        trendScore=43.0,
        opportunityScore=45.0,
        riskScore=32.0,
        return5d=0.1,
        return20d=0.2,
        return60d=8.5,
        ma20Deviation=-7.5,
        maxDrawdown60d=-12.0,
        lossDayRatio20d=58.0,
    )

    assert _core_trend_allocation_buy(row, BacktestStrategyParams(maxSinglePositionRate=45), 18)


def test_core_trend_allocation_buy_stops_near_full_target():
    row = SimpleNamespace(
        totalScore=44.0,
        trendScore=43.0,
        opportunityScore=45.0,
        riskScore=32.0,
        return5d=0.1,
        return20d=0.2,
        return60d=8.5,
        ma20Deviation=-8.5,
        maxDrawdown60d=-12.0,
        lossDayRatio20d=58.0,
    )

    assert not _core_trend_allocation_buy(row, BacktestStrategyParams(maxSinglePositionRate=45), 44.2)


def test_early_trend_bootstrap_buy_allows_short_history_winner():
    row = SimpleNamespace(
        sampleIndex=72,
        totalScore=49.0,
        trendScore=45.0,
        riskScore=35.0,
        return5d=-0.2,
        return20d=1.1,
        ma20Deviation=-6.5,
        maxDrawdown60d=-8.0,
        lossDayRatio20d=54.0,
        trendSlope20d=0.08,
        return60d=0.0,
        opportunityScore=52.0,
    )

    assert _early_trend_bootstrap_buy(row, BacktestStrategyParams(maxSinglePositionRate=45), 0)


def test_early_trend_bootstrap_buy_stops_after_bootstrap_window():
    row = SimpleNamespace(
        sampleIndex=180,
        totalScore=60.0,
        trendScore=55.0,
        riskScore=50.0,
        return5d=0.4,
        return20d=3.0,
        ma20Deviation=-2.0,
        maxDrawdown60d=-6.0,
        lossDayRatio20d=42.0,
        trendSlope20d=0.2,
        return60d=8.0,
        opportunityScore=56.0,
    )

    assert not _early_trend_bootstrap_buy(row, BacktestStrategyParams(maxSinglePositionRate=45), 0)


def test_orderly_trend_hold_protects_controlled_pullback():
    row = SimpleNamespace(
        return20d=-2.5,
        return60d=9.2,
        ma20Deviation=-8.5,
        maxDrawdown60d=-13.0,
        lossDayRatio20d=58.0,
    )

    assert _orderly_trend_hold(row, BacktestStrategyParams(trendHoldMa20Deviation=-7))


def test_trend_repair_reentry_allows_active_equity_repair_after_defense():
    row = SimpleNamespace(
        return5d=0.2,
        return20d=1.2,
        return60d=3.2,
        ma20Deviation=-4.2,
        maxDrawdown60d=-10.0,
        trendScore=52.0,
        opportunityScore=42.0,
        riskScore=35.0,
        lossDayRatio20d=56.0,
    )

    assert _trend_repair_reentry_buy(row, BacktestStrategyParams(maxSinglePositionRate=45), 12, "ACTIVE_EQUITY")
    assert not _trend_repair_reentry_buy(row, BacktestStrategyParams(maxSinglePositionRate=45), 12, "QDII")


def test_backtest_pass_allows_small_position_benchmark_fee_gap():
    import pandas as pd

    frame = pd.DataFrame({"date": pd.to_datetime(["2025-01-01", "2025-12-31"])})

    assert _is_passed(
        annual=12,
        drawdown=-8,
        benchmark_return=20,
        total_return=19.6,
        trade_count=1,
        frame=frame,
        data_coverage=100,
    )


def test_backtest_pass_allows_strong_short_history_slight_benchmark_gap():
    import pandas as pd

    frame = pd.DataFrame({"date": pd.date_range("2024-05-28", periods=105, freq="D")})

    assert _is_passed(
        annual=24,
        drawdown=-13,
        benchmark_return=45,
        total_return=39,
        trade_count=1,
        frame=frame,
        data_coverage=35,
    )


def test_backtest_pass_allows_usable_short_history_with_low_requested_coverage():
    import pandas as pd

    frame = pd.DataFrame({"date": pd.date_range("2025-01-01", periods=140, freq="D")})

    assert _is_passed(
        annual=12,
        drawdown=-8,
        benchmark_return=20,
        total_return=19.6,
        trade_count=3,
        frame=frame,
        data_coverage=35,
    )


def test_backtest_pass_uses_active_qdii_stability_profile():
    import pandas as pd

    frame = pd.DataFrame({"date": pd.date_range("2023-07-03", periods=1096, freq="D")})
    active_qdii = resolve_fund_profile("012922", "", "QDII")
    etf_qdii = resolve_fund_profile("013403", "", "QDII")

    assert _is_passed(
        annual=36.0,
        drawdown=-9.9,
        benchmark_return=169.6,
        total_return=151.4,
        trade_count=4,
        frame=frame,
        data_coverage=100,
        fund_profile=active_qdii,
    )
    assert not _is_passed(
        annual=36.0,
        drawdown=-9.9,
        benchmark_return=169.6,
        total_return=151.4,
        trade_count=4,
        frame=frame,
        data_coverage=100,
        fund_profile=etf_qdii,
    )


def test_backtest_pass_allows_short_history_index_defensive_profile():
    import pandas as pd

    frame = pd.DataFrame({"date": pd.date_range("2025-11-25", periods=220, freq="D")})
    index_profile = resolve_fund_profile("025833", "", "INDEX")

    assert _is_passed(
        annual=6.95,
        drawdown=-6.86,
        benchmark_return=15.63,
        total_return=4.11,
        trade_count=1,
        frame=frame,
        data_coverage=20,
        fund_profile=index_profile,
    )
