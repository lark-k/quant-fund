import pytest

from app.core.schemas import AccountSnapshot, BacktestStrategyParams, HoldingSnapshot, MarketContext, ScoreBreakdown, StrategyExecutionState
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
    assert ratio == 33
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
    assert amount == 3500
    assert ratio == 35
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


def test_recoverable_pullback_can_buy_after_short_term_stabilizes():
    request = make_request()
    features = build_features(request)
    features["return5d"] = 0.3
    features["return20d"] = -4.0
    features["return60d"] = -2.0
    features["ma20Deviation"] = -8.0
    features["maxDrawdown60d"] = -12.0
    features["lossDayRatio20d"] = 55.0
    score = ScoreBreakdown(
        totalScore=52,
        trendScore=48,
        opportunityScore=70,
        riskScore=42,
        positionScore=60,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 2500
    assert ratio == 25
    assert blockers == []


def test_recoverable_pullback_does_not_buy_before_short_term_stabilizes():
    request = make_request()
    features = build_features(request)
    features["return5d"] = -0.4
    features["return20d"] = -4.0
    features["return60d"] = -2.0
    features["ma20Deviation"] = -8.0
    features["maxDrawdown60d"] = -12.0
    features["lossDayRatio20d"] = 55.0
    score = ScoreBreakdown(
        totalScore=52,
        trendScore=48,
        opportunityScore=70,
        riskScore=42,
        positionScore=60,
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
    assert ratio == 33
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
    assert amount == 96
    assert ratio == 8
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

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
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
    assert amount == 3300
    assert ratio == 33
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
    features["currentDrawdown60d"] = -23
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
    assert amount == 2500
    assert ratio == 25
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


def test_benchmark_alignment_buy_tops_up_underweight_orderly_trend():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=6000,
        positionRate=18,
        holdingProfitRate=12,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return5d"] = 0.2
    features["return20d"] = 0.4
    features["return60d"] = 5.5
    features["ma20Deviation"] = -7.0
    features["maxDrawdown60d"] = -10.0
    features["lossDayRatio20d"] = 55.0
    score = ScoreBreakdown(
        totalScore=49,
        trendScore=46,
        opportunityScore=52,
        riskScore=35,
        positionScore=60,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 2500
    assert ratio == 25
    assert blockers == []


def test_benchmark_alignment_buy_does_not_chase_near_target_position():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=6000,
        positionRate=41,
        holdingProfitRate=12,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return5d"] = 0.2
    features["return20d"] = 0.4
    features["return60d"] = 5.5
    features["ma20Deviation"] = -7.0
    features["maxDrawdown60d"] = -10.0
    features["lossDayRatio20d"] = 55.0
    score = ScoreBreakdown(
        totalScore=47,
        trendScore=46,
        opportunityScore=52,
        riskScore=35,
        positionScore=60,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
    assert blockers == []


def test_core_trend_allocation_buy_fills_underweight_confirmed_trend():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=6000,
        positionRate=10,
        holdingProfitRate=12,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return5d"] = 0.1
    features["return20d"] = 0.2
    features["return60d"] = 8.5
    features["ma20Deviation"] = -7.5
    features["maxDrawdown60d"] = -12.0
    features["lossDayRatio20d"] = 58.0
    score = ScoreBreakdown(
        totalScore=46,
        trendScore=43,
        opportunityScore=45,
        riskScore=32,
        positionScore=60,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 3500
    assert ratio == 35
    assert blockers == []


def test_early_trend_bootstrap_buy_maps_short_history_winner_to_buy():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=6000,
        positionRate=12,
        holdingProfitRate=4,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["navSampleSize"] = 72
    features["return5d"] = -0.2
    features["return20d"] = 1.1
    features["return60d"] = 0.0
    features["ma20Deviation"] = -6.5
    features["maxDrawdown60d"] = -8.0
    features["lossDayRatio20d"] = 54.0
    features["trendSlope20d"] = 0.08
    score = ScoreBreakdown(
        totalScore=49,
        trendScore=45,
        opportunityScore=52,
        riskScore=35,
        positionScore=60,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 2500
    assert ratio == 25
    assert blockers == []


def test_active_equity_trend_repair_maps_to_buy_but_qdii_stays_cautious():
    holding = HoldingSnapshot(
        fundCode="016874",
        fundType="ACTIVE_EQUITY",
        holdingAmount=6000,
        positionRate=12,
        holdingProfitRate=4,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return5d"] = 0.2
    features["return20d"] = 1.2
    features["return60d"] = 3.2
    features["ma20Deviation"] = -4.2
    features["maxDrawdown60d"] = -10.0
    features["lossDayRatio20d"] = 56.0
    features["navSampleSize"] = 220
    score = ScoreBreakdown(
        totalScore=54,
        trendScore=52,
        opportunityScore=42,
        riskScore=35,
        positionScore=60,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 3000
    assert ratio == 30
    assert blockers == []

    qdii_request = make_request(holding=holding.model_copy(update={"fundCode": "999999", "fundName": "普通海外商品(QDII)C", "fundType": "QDII"}))
    qdii_features = build_features(qdii_request)
    qdii_features.update(features)
    qdii_action, qdii_amount, qdii_ratio, qdii_blockers = map_action(qdii_request, score, qdii_features)

    assert qdii_action == "HOLD"
    assert qdii_amount == 0
    assert qdii_ratio == 0
    assert qdii_blockers == []


def test_active_qdii_profile_uses_active_repair_rules_even_when_raw_type_is_qdii():
    holding = HoldingSnapshot(
        fundCode="012922",
        fundName="易方达全球成长精选混合(QDII)人民币C",
        fundType="QDII",
        holdingAmount=6000,
        positionRate=12,
        holdingProfitRate=4,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return5d"] = 0.2
    features["return20d"] = 1.2
    features["return60d"] = 3.2
    features["ma20Deviation"] = -4.2
    features["maxDrawdown60d"] = -10.0
    features["lossDayRatio20d"] = 56.0
    features["navSampleSize"] = 220
    score = ScoreBreakdown(
        totalScore=54,
        trendScore=52,
        opportunityScore=42,
        riskScore=35,
        positionScore=60,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 3000
    assert ratio == 30
    assert blockers == []


def test_active_qdii_profile_prevents_raw_qdii_weak_defense_sell():
    holding = HoldingSnapshot(
        fundCode="012922",
        fundName="易方达全球成长精选混合(QDII)人民币C",
        fundType="QDII",
        holdingAmount=6000,
        positionRate=12,
        holdingProfitRate=4,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["return20d"] = -6.0
    features["return60d"] = -0.5
    features["ma20Deviation"] = -4.5
    features["maxDrawdown60d"] = -10.0
    features["lossPressure"] = 0.0
    score = ScoreBreakdown(
        totalScore=70,
        trendScore=52,
        opportunityScore=55,
        riskScore=40,
        positionScore=70,
        momentumScore=50,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "BUY"
    assert amount == 500
    assert ratio == 5
    assert blockers == []


def test_weak_trend_defense_maps_to_sell():
    holding = HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=1200,
        positionRate=12,
        holdingProfitRate=-4,
    )
    request = make_request(holding=holding, strategyState=StrategyExecutionState(weakTrendCandidateDays=3))
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
    request = make_request(strategyState=StrategyExecutionState(weakTrendCandidateDays=3))
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
    assert ratio == 33
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
    assert amount == 3500
    assert ratio == 35
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


def test_weak_trend_waits_for_four_distinct_decision_days():
    request = make_request(strategyState=StrategyExecutionState(weakTrendCandidateDays=2))
    features = build_features(request)
    features.update({"return20d": -7, "return60d": -9, "ma20Deviation": -5, "maxDrawdown60d": -9})

    action, amount, ratio, _ = map_action(request, high_score(), features)

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
    assert features["weakTrendCandidateDaysAfter"] == 3
    assert features["weakTrendDefenseHandledAfter"] is False


def test_handled_weak_trend_does_not_repeat_daily_sell():
    request = make_request(strategyState=StrategyExecutionState(
        weakTrendCandidateDays=4,
        weakTrendDefenseHandled=True,
        weakTrendCooldownDays=45,
        weakRecoveryRequired=True,
        lastDefenseDate="2026-06-27",
    ))
    features = build_features(request)
    features.update({"return20d": -7, "return60d": -9, "ma20Deviation": -5, "maxDrawdown60d": -9})

    action, amount, ratio, _ = map_action(request, high_score(), features)

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
    assert features["weakTrendCooldownDaysAfter"] == 44
    assert features["weakTrendDefenseHandledAfter"] is True


def test_recovery_does_not_bypass_remaining_cooldown():
    request = make_request(strategyState=StrategyExecutionState(
        weakTrendDefenseHandled=True,
        weakTrendCooldownDays=2,
        weakRecoveryRequired=True,
    ))
    features = build_features(request)

    action, amount, ratio, _ = map_action(request, high_score(), features)

    assert features["weakTrendRecovered"] is True
    assert features["weakRecoveryRequiredAfter"] is False
    assert features["weakTrendCooldownDaysAfter"] == 1
    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0


def test_extreme_risk_has_priority_over_confirmed_weak_trend():
    holding = HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=4000,
        positionRate=40,
        holdingProfitRate=-4,
    )
    request = make_request(
        holding=holding,
        strategyState=StrategyExecutionState(weakTrendCandidateDays=3),
    )
    features = build_features(request)
    features.update({
        "return20d": -7,
        "return60d": -9,
        "ma20Deviation": -5,
        "maxDrawdown60d": -23,
        "currentDrawdown60d": -23,
    })

    action, amount, ratio, _ = map_action(request, high_score(), features)

    assert action == "SELL"
    assert ratio == 50
    assert amount == 2000
    assert features["decisionReason"] == "extreme_risk_exit"


def test_first_extreme_risk_exit_is_partial_after_weak_trend_defense():
    holding = HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=4000,
        positionRate=40,
        holdingProfitRate=-4,
    )
    request = make_request(
        holding=holding,
        strategyState=StrategyExecutionState(
            weakTrendDefenseHandled=True,
            weakTrendCooldownDays=45,
            weakRecoveryRequired=True,
        ),
    )
    features = build_features(request)
    features.update({
        "return20d": -7,
        "return60d": -9,
        "ma20Deviation": -5,
        "maxDrawdown60d": -23,
        "currentDrawdown60d": -23,
    })

    action, amount, ratio, _ = map_action(request, high_score(), features)

    assert action == "SELL"
    assert ratio == 50
    assert amount == 2000
    assert features["extremeRiskSellCountAfter"] == 1


def test_historical_drawdown_does_not_trigger_extreme_exit_after_recovery():
    request = make_request(holding=HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=4000,
        positionRate=40,
        holdingProfitRate=-4,
    ))
    features = build_features(request)
    features.update({"maxDrawdown60d": -25, "currentDrawdown60d": -12})

    action, _, ratio, _ = map_action(request, high_score(), features)

    assert action != "SELL"


@pytest.mark.parametrize("position_rate", [10, 20, 40])
def test_first_extreme_exit_sells_half_at_normal_position_sizes(position_rate):
    request = make_request(holding=HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=4000,
        positionRate=position_rate,
        holdingProfitRate=-4,
    ))
    features = build_features(request)
    features["currentDrawdown60d"] = -23

    action, amount, ratio, _ = map_action(request, high_score(), features)

    assert action == "SELL"
    assert amount == 2000
    assert ratio == 50
    assert features["extremeRiskConfirmedStage"] == 0
    assert features["extremeRiskStageAfter"] == 1
    assert features["extremeRiskTradeConfirmationRequired"] is True


def test_extreme_exit_clears_only_true_tail_position():
    request = make_request(holding=HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=400,
        positionRate=5,
        holdingProfitRate=-4,
    ))
    features = build_features(request)
    features["currentDrawdown60d"] = -23

    action, amount, ratio, _ = map_action(request, high_score(), features)

    assert action == "SELL"
    assert amount == 400
    assert ratio == 100
    assert features["extremeRiskStageAfter"] == 2


def test_second_extreme_day_exits_when_drawdown_does_not_improve():
    request = make_request(strategyState=StrategyExecutionState(
        extremeRiskStage=1,
        lastExtremeRiskDate="2026-06-26",
        lastExtremeDrawdown=23,
        lastActionDate="2026-06-26",
    ))
    features = build_features(request)
    features["currentDrawdown60d"] = -21
    request.holding.holdingProfitRate = -19

    action, _, ratio, _ = map_action(request, high_score(), features)

    assert action == "SELL"
    assert ratio == 100
    assert features["extremeRiskConfirmedStage"] == 1
    assert features["extremeRiskStageAfter"] == 2


def test_second_extreme_day_watches_when_drawdown_improves_three_points():
    request = make_request(strategyState=StrategyExecutionState(
        extremeRiskStage=1,
        lastExtremeRiskDate="2026-06-26",
        lastExtremeDrawdown=26,
        lastActionDate="2026-06-26",
    ))
    features = build_features(request)
    features["currentDrawdown60d"] = -23

    action, amount, ratio, _ = map_action(request, high_score(), features)

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
    assert features["decisionReason"] == "extreme_risk_recovery_watch"
    assert features["extremeRiskStageAfter"] == 1
    assert features["lastExtremeDrawdownAfter"] == 23


def test_same_day_reanalysis_does_not_advance_extreme_stage():
    request = make_request(strategyState=StrategyExecutionState(
        extremeRiskStage=1,
        lastExtremeRiskDate="2026-06-28",
        lastExtremeDrawdown=23,
        lastActionDate="2026-06-28",
    ))
    features = build_features(request)
    features["currentDrawdown60d"] = -24

    action, _, ratio, _ = map_action(request, high_score(), features)

    assert action == "SELL"
    assert ratio == 50
    assert features["extremeRiskStageAfter"] == 1


def test_legacy_extreme_count_without_drawdown_baseline_does_not_force_full_exit():
    request = make_request(strategyState=StrategyExecutionState(
        extremeRiskSellCount=1,
        lastDefenseDate="2026-06-27",
    ))
    features = build_features(request)
    features["currentDrawdown60d"] = -24

    action, _, ratio, _ = map_action(request, high_score(), features)

    assert action == "SELL"
    assert ratio == 50
    assert features["extremeRiskStageAfter"] == 1
