from app.core.schemas import AccountSnapshot, BacktestStrategyParams, HoldingSnapshot, MarketContext, ScoreBreakdown
from app.features.feature_builder import build_features
from app.strategies.action_mapper import map_action

from .conftest import make_request


def high_score() -> ScoreBreakdown:
    return ScoreBreakdown(
        totalScore=86,
        trendScore=90,
        opportunityScore=82,
        riskScore=80,
        positionScore=78,
        momentumScore=84,
    )


def buy_score() -> ScoreBreakdown:
    return ScoreBreakdown(
        totalScore=76,
        trendScore=70,
        opportunityScore=60,
        riskScore=35,
        positionScore=75,
        momentumScore=55,
    )


def test_high_score_maps_to_buy_before_1457_when_risk_room_exists():
    request = make_request()
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "BUY"
    assert amount > 0
    assert ratio == 30
    assert blockers == []


def test_equity_fund_can_buy_with_good_trend_even_when_risk_score_is_low():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=1200,
        positionRate=10,
        holdingProfitRate=6,
    )
    request = make_request(holding=holding)
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, buy_score(), features)

    assert action == "BUY"
    assert amount == 360
    assert ratio == 30
    assert blockers == []


def test_low_risk_score_alone_does_not_force_sell_when_trend_is_strong():
    request = make_request()
    features = build_features(request)
    score = ScoreBreakdown(
        totalScore=68,
        trendScore=95,
        opportunityScore=75,
        riskScore=10,
        positionScore=80,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "HOLD"
    assert amount == 0
    assert ratio == 0
    assert blockers == []


def test_1457_or_later_does_not_block_buy():
    request = make_request(market=MarketContext(tradingDay=True, trading=True, now="2026-06-28 14:57:00"))
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "BUY"
    assert amount > 0
    assert ratio == 30
    assert blockers == []


def test_position_over_limit_does_not_buy():
    holding = HoldingSnapshot(
        fundCode="025833",
        fundType="INDEX",
        holdingAmount=1200,
        positionRate=50,
        holdingProfitRate=5,
    )
    request = make_request(holding=holding)
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "SELL"
    assert amount == 180
    assert ratio == 15
    assert len(blockers) == 1


def test_market_breakdown_maps_to_sell():
    holding = HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=1200,
        positionRate=12,
        holdingProfitRate=-14,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["lossPressure"] = 14
    features["maxDrawdown60d"] = -16
    score = ScoreBreakdown(
        totalScore=36,
        trendScore=35,
        opportunityScore=45,
        riskScore=25,
        positionScore=70,
        momentumScore=40,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "SELL"
    assert amount == 420
    assert ratio == 35
    assert blockers == []


def test_strong_trend_lock_allows_buy_instead_of_profit_giveback_sell_when_room_exists():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=1200,
        positionRate=12,
        holdingProfitRate=16,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return20d"] = 6
    features["return60d"] = 18
    features["ma20Deviation"] = -1
    features["maxDrawdown60d"] = -11
    score = ScoreBreakdown(
        totalScore=60,
        trendScore=54,
        opportunityScore=58,
        riskScore=35,
        positionScore=75,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 360
    assert ratio == 30
    assert blockers == []


def test_strong_trend_lock_keeps_extreme_stop_loss_available():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=1200,
        positionRate=12,
        holdingProfitRate=-20,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return20d"] = 6
    features["return60d"] = 18
    features["ma20Deviation"] = -1
    features["maxDrawdown60d"] = -23
    features["lossPressure"] = 20
    score = ScoreBreakdown(
        totalScore=40,
        trendScore=54,
        opportunityScore=45,
        riskScore=15,
        positionScore=60,
        momentumScore=35,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "SELL"
    assert amount == 600
    assert ratio == 50
    assert blockers == []


def test_midterm_strong_pullback_does_not_map_to_weak_trend_sell():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=6000,
        positionRate=20,
        holdingProfitRate=35,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return20d"] = -8
    features["return60d"] = 26
    features["ma20Deviation"] = -11
    features["maxDrawdown60d"] = -19
    score = ScoreBreakdown(
        totalScore=51,
        trendScore=36,
        opportunityScore=70,
        riskScore=33,
        positionScore=45,
        momentumScore=50,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
    assert blockers == []


def test_recoverable_pullback_does_not_map_to_weak_trend_sell_or_profit_giveback():
    holding = HoldingSnapshot(
        fundCode="016874",
        fundType="ACTIVE_EQUITY",
        holdingAmount=6000,
        positionRate=20,
        holdingProfitRate=20,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["lossPressure"] = 0
    features["return20d"] = -12.6
    features["return60d"] = -2.1
    features["ma20Deviation"] = -12.1
    features["maxDrawdown60d"] = -16.9
    score = ScoreBreakdown(
        totalScore=43,
        trendScore=12,
        opportunityScore=70,
        riskScore=35,
        positionScore=55,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
    assert blockers == []


def test_midterm_trend_continuation_can_buy_on_orderly_pullback():
    holding = HoldingSnapshot(
        fundCode="016874",
        fundType="ACTIVE_EQUITY",
        holdingAmount=6000,
        positionRate=18,
        holdingProfitRate=20,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return5d"] = 0.5
    features["return20d"] = -3
    features["return60d"] = 18
    features["ma20Deviation"] = -5
    features["maxDrawdown60d"] = -14
    features["lossDayRatio20d"] = 55
    score = ScoreBreakdown(
        totalScore=52,
        trendScore=48,
        opportunityScore=68,
        riskScore=32,
        positionScore=60,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 1200
    assert ratio == 20
    assert blockers == []


def test_midterm_trend_continuation_does_not_buy_when_5d_breaks_down():
    holding = HoldingSnapshot(
        fundCode="013403",
        fundType="ACTIVE_EQUITY",
        holdingAmount=3000,
        positionRate=18,
        holdingProfitRate=2,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return5d"] = -2.4
    features["return20d"] = -1.5
    features["return60d"] = 15.1
    features["ma20Deviation"] = -1.4
    features["maxDrawdown60d"] = -5.4
    features["lossDayRatio20d"] = 56
    score = ScoreBreakdown(
        totalScore=56,
        trendScore=50,
        opportunityScore=56,
        riskScore=57,
        positionScore=58,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "HOLD"
    assert amount == 0
    assert ratio == 0
    assert blockers == []


def test_weak_trend_defense_maps_to_sell():
    holding = HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=1200,
        positionRate=12,
        holdingProfitRate=-4,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return20d"] = -7
    features["return60d"] = -6
    features["ma20Deviation"] = -5
    features["maxDrawdown60d"] = -9
    score = ScoreBreakdown(
        totalScore=56,
        trendScore=52,
        opportunityScore=45,
        riskScore=40,
        positionScore=72,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "SELL"
    assert amount == 180
    assert ratio == 15
    assert blockers == []


def test_weak_trend_defense_blocks_buy():
    request = make_request()
    features = build_features(request)
    features["return20d"] = -7
    features["return60d"] = -9
    features["ma20Deviation"] = -5
    features["maxDrawdown60d"] = -9

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "SELL"
    assert amount == 180
    assert ratio == 15
    assert blockers == []


def test_equity_position_over_limit_does_not_force_sell_or_block_buy():
    account = AccountSnapshot(accountId=1, totalAsset=10000, equityPositionRate=95)
    request = make_request(account=account)
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert features["shouldReduceByPosition"] is False
    assert action == "BUY"
    assert amount > 0
    assert ratio == 30
    assert blockers == []


def test_qdii_intraday_can_generate_buy_signal():
    holding = HoldingSnapshot(
        fundCode="968000",
        fundType="QDII",
        holdingAmount=1200,
        positionRate=8,
        currentEstimateGrowthRate=1.2,
    )
    request = make_request(holding=holding)
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert features["isQdiiOrOverseas"] is True
    assert action == "BUY"
    assert amount == 360
    assert ratio == 30
    assert blockers == []


def test_strategy_params_override_default_position_limit_and_steps():
    holding = HoldingSnapshot(
        fundCode="025833",
        fundType="INDEX",
        holdingAmount=1200,
        positionRate=30,
        holdingProfitRate=5,
    )
    request = make_request(
        holding=holding,
        strategyParams=BacktestStrategyParams(maxSinglePositionRate=25, sellStepRatio=10),
    )
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert features["shouldReduceByPosition"] is True
    assert action == "SELL"
    assert amount == 120
    assert ratio == 10
    assert len(blockers) == 1
