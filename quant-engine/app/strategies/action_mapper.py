from __future__ import annotations

from app.core.schemas import QuantAnalyzeRequest, ScoreBreakdown
from app.strategies.constraints import buy_blockers
from app.strategies.fund_profile import (
    is_active_fund_type,
    is_qdii_or_overseas_type,
    normalize_fund_type,
    resolve_effective_fund_type,
)


TARGET_ENTRY_POSITION_RATE = 45.0
BENCHMARK_ENTRY_POSITION_RATE = 40.0
EARLY_TREND_BOOTSTRAP_SAMPLES = 150
WEAK_TREND_CONFIRM_DAYS = 4
WEAK_TREND_COOLDOWN_DAYS = 45
POSITION_REBALANCE_COOLDOWN_DAYS = 20
EXTREME_RISK_DRAWDOWN = 22.0
EXTREME_RISK_RECOVERY_POINTS = 3.0
TAIL_POSITION_RATE = 5.0

ACTION_TEXT = {
    "BUY": "建议加仓",
    "SELL": "建议减仓",
    "HOLD": "建议持有观察",
    "WATCH": "建议重点观察",
    "CONVERT": "建议转换",
}


def map_action(request: QuantAnalyzeRequest, score: ScoreBreakdown, features: dict) -> tuple[str, float, float, list[str]]:
    blockers = buy_blockers(request, features)
    params = request.strategyParams
    total = score.totalScore
    fund_type = resolve_effective_fund_type(request.holding.fundCode, request.holding.fundName, request.holding.fundType)

    strong_trend_lock = _is_strong_trend_lock(features, params, fund_type)
    trend_start_buy = _is_trend_start_buy(features, score, params, fund_type)
    midterm_trend_buy = _is_midterm_trend_buy(features, score, fund_type)
    recoverable_pullback_buy = _is_recoverable_pullback_buy(features, score)
    benchmark_alignment_buy = _is_benchmark_alignment_buy(features, score, params, request.holding.positionRate, fund_type)
    core_trend_allocation_buy = _is_core_trend_allocation_buy(features, score, params, request.holding.positionRate, fund_type)
    early_trend_bootstrap_buy = _is_early_trend_bootstrap_buy(features, score, params, request.holding.positionRate, fund_type)
    trend_repair_buy = _is_trend_repair_buy(features, score, params, request.holding.positionRate, fund_type)

    state = _advance_execution_state(request, score, features, fund_type)
    exit_reason = _market_exit_reason(score, features, request, strong_trend_lock, state)
    if exit_reason is not None:
        ratio = _sell_ratio(exit_reason, request, state)
        _record_execution_state(request, features, state, exit_reason)
        features["decisionReason"] = exit_reason
        return "SELL", _sell_amount(request, ratio), ratio, blockers

    if state["extremeRiskRecoveryWatch"]:
        features["decisionReason"] = "extreme_risk_recovery_watch"
        _record_execution_state(request, features, state, "extreme_risk_recovery_watch")
        return "WATCH", 0.0, 0.0, blockers

    if features.get("shouldReduceByPosition"):
        ratio = params.sellStepRatio if request.holding.holdingProfitRate < 15 else max(params.sellStepRatio, 20)
        features["decisionReason"] = "position_limit"
        _record_execution_state(request, features, state, "position_limit")
        return "SELL", _sell_amount(request, ratio), ratio, blockers

    if (
        state["weakTrendCandidate"]
        or state["weakRecoveryRequired"]
        or state["weakTrendCooldownDays"] > 0
        or state["positionRebalanceCooldownDays"] > 0
    ):
        features["decisionReason"] = "weak_trend_pending" if state["weakTrendCandidate"] else "defense_cooldown"
        _record_execution_state(request, features, state)
        return "WATCH", 0.0, 0.0, blockers

    should_buy = _should_buy(
        score,
        features,
        request,
        strong_trend_lock,
        trend_start_buy,
        midterm_trend_buy,
        recoverable_pullback_buy,
        benchmark_alignment_buy,
        core_trend_allocation_buy,
        early_trend_bootstrap_buy,
        trend_repair_buy,
        fund_type,
    )
    if should_buy and not blockers:
        ratio = _buy_ratio(
            request,
            score,
            strong_trend_lock,
            trend_start_buy,
            midterm_trend_buy,
            recoverable_pullback_buy,
            benchmark_alignment_buy,
            core_trend_allocation_buy,
            early_trend_bootstrap_buy,
            trend_repair_buy,
        )
        amount = _suggest_amount(request, ratio)
        if amount <= 0:
            _record_execution_state(request, features, state)
            return "HOLD", 0.0, 0.0, blockers
        features["decisionReason"] = _buy_reason(
            strong_trend_lock,
            trend_start_buy,
            midterm_trend_buy,
            recoverable_pullback_buy,
            benchmark_alignment_buy,
            core_trend_allocation_buy,
            early_trend_bootstrap_buy,
            trend_repair_buy,
        )
        _record_execution_state(request, features, state)
        return "BUY", amount, ratio, blockers

    if should_buy and blockers:
        _record_execution_state(request, features, state)
        return "WATCH", 0.0, 0.0, blockers

    _record_execution_state(request, features, state)
    if total >= params.buyThreshold and score.trendScore >= 40:
        return "HOLD", 0.0, 0.0, blockers
    return "WATCH", 0.0, 0.0, blockers


def _advance_execution_state(
    request: QuantAnalyzeRequest,
    score: ScoreBreakdown,
    features: dict,
    fund_type: str,
) -> dict:
    previous = request.strategyState
    decision_date = _decision_date(request)
    same_decision_day = bool(decision_date and previous.lastActionDate == decision_date)
    weak_candidate = _is_weak_trend_defense(features, score, fund_type)
    recovered = _is_weak_trend_recovered(features, score, request.strategyParams, fund_type)
    cooldown = int(previous.weakTrendCooldownDays)
    position_cooldown = int(previous.positionRebalanceCooldownDays)
    if not same_decision_day:
        cooldown = max(cooldown - 1, 0)
        position_cooldown = max(position_cooldown - 1, 0)
    if same_decision_day:
        candidate_days = int(previous.weakTrendCandidateDays)
    else:
        candidate_days = int(previous.weakTrendCandidateDays) + 1 if weak_candidate else 0
    recovery_required = bool(previous.weakRecoveryRequired)
    handled = bool(previous.weakTrendDefenseHandled)
    extreme_count = int(previous.extremeRiskSellCount)
    last_extreme_drawdown = previous.lastExtremeDrawdown
    extreme_stage = int(previous.extremeRiskStage)
    if extreme_stage == 0 and extreme_count > 0 and last_extreme_drawdown is not None:
        extreme_stage = min(extreme_count, 2)
    current_drawdown = _current_drawdown60(features)

    if recovered:
        recovery_required = False
    if cooldown == 0 and not recovery_required and not weak_candidate:
        handled = False
    if current_drawdown < 18 and request.holding.holdingProfitRate > request.strategyParams.stopLossRate:
        extreme_count = 0
        extreme_stage = 0
        last_extreme_drawdown = None

    state = {
        "weakTrendCandidate": weak_candidate,
        "weakTrendCandidateDays": candidate_days,
        "weakTrendConfirmed": weak_candidate and candidate_days >= WEAK_TREND_CONFIRM_DAYS,
        "weakTrendDefenseHandled": handled,
        "weakTrendCooldownDays": cooldown,
        "positionRebalanceCooldownDays": position_cooldown,
        "weakRecoveryRequired": recovery_required,
        "weakTrendRecovered": recovered,
        "extremeRiskSellCount": extreme_count,
        "extremeRiskStage": extreme_stage,
        "confirmedExtremeRiskStage": extreme_stage,
        "lastExtremeRiskDate": previous.lastExtremeRiskDate,
        "lastExtremeDrawdown": last_extreme_drawdown,
        "lastActionDate": previous.lastActionDate,
        "sameDecisionDay": same_decision_day,
        "currentDrawdown60dForDecision": current_drawdown,
        "extremeRiskRecoveryWatch": False,
        "lastDefenseDate": previous.lastDefenseDate,
    }
    features.update(state)
    return state


def _record_execution_state(
    request: QuantAnalyzeRequest,
    features: dict,
    state: dict,
    exit_reason: str | None = None,
) -> None:
    if exit_reason in {"score_exit", "risk_exit", "weak_trend_defense", "extreme_risk_exit"}:
        state["weakTrendDefenseHandled"] = True
        state["weakTrendCooldownDays"] = WEAK_TREND_COOLDOWN_DAYS
        state["weakRecoveryRequired"] = True
        now = request.market.now
        state["lastDefenseDate"] = now.date().isoformat() if now else None
    elif exit_reason == "position_limit":
        state["positionRebalanceCooldownDays"] = max(
            state["positionRebalanceCooldownDays"],
            POSITION_REBALANCE_COOLDOWN_DAYS,
        )
    if exit_reason == "extreme_risk_exit":
        if not state["sameDecisionDay"]:
            if request.holding.positionRate <= TAIL_POSITION_RATE or state["extremeRiskStage"] >= 1:
                state["extremeRiskStage"] = 2
            else:
                state["extremeRiskStage"] = 1
            state["extremeRiskSellCount"] = state["extremeRiskStage"]
            state["lastExtremeRiskDate"] = _decision_date(request)
            state["lastExtremeDrawdown"] = state["currentDrawdown60dForDecision"]
    elif exit_reason == "extreme_risk_recovery_watch" and not state["sameDecisionDay"]:
        state["lastExtremeRiskDate"] = _decision_date(request)
        state["lastExtremeDrawdown"] = state["currentDrawdown60dForDecision"]
    state["lastActionDate"] = _decision_date(request) or state["lastActionDate"]
    features.update({
        "weakTrendCandidateDaysAfter": state["weakTrendCandidateDays"],
        "weakTrendDefenseHandledAfter": state["weakTrendDefenseHandled"],
        "weakTrendCooldownDaysAfter": state["weakTrendCooldownDays"],
        "positionRebalanceCooldownDaysAfter": state["positionRebalanceCooldownDays"],
        "weakRecoveryRequiredAfter": state["weakRecoveryRequired"],
        "extremeRiskSellCountAfter": state["extremeRiskSellCount"],
        "extremeRiskStageAfter": state["extremeRiskStage"],
        "extremeRiskConfirmedStage": state["confirmedExtremeRiskStage"],
        "extremeRiskSuggestedStageAfter": state["extremeRiskStage"],
        "extremeRiskTradeConfirmationRequired": exit_reason == "extreme_risk_exit",
        "lastExtremeRiskDateAfter": state["lastExtremeRiskDate"],
        "lastExtremeDrawdownAfter": state["lastExtremeDrawdown"],
        "lastActionDateAfter": state["lastActionDate"],
        "lastDefenseDateAfter": state["lastDefenseDate"],
    })


def _buy_ratio(
    request: QuantAnalyzeRequest,
    score: ScoreBreakdown,
    strong_trend_lock: bool = False,
    trend_start_buy: bool = False,
    midterm_trend_buy: bool = False,
    recoverable_pullback_buy: bool = False,
    benchmark_alignment_buy: bool = False,
    core_trend_allocation_buy: bool = False,
    early_trend_bootstrap_buy: bool = False,
    trend_repair_buy: bool = False,
) -> float:
    if request.holding.positionRate <= 1 and (strong_trend_lock or trend_start_buy):
        max_single_buy = request.strategyParams.maxSinglePositionRate
    elif request.holding.positionRate <= 1 and midterm_trend_buy:
        max_single_buy = min(request.strategyParams.maxSinglePositionRate, 35.0)
    elif request.holding.positionRate <= 1 and core_trend_allocation_buy:
        max_single_buy = min(request.strategyParams.maxSinglePositionRate, TARGET_ENTRY_POSITION_RATE)
    elif request.holding.positionRate <= 1 and benchmark_alignment_buy:
        max_single_buy = min(request.strategyParams.maxSinglePositionRate, BENCHMARK_ENTRY_POSITION_RATE)
    elif request.holding.positionRate <= 1 and recoverable_pullback_buy:
        max_single_buy = min(request.strategyParams.maxSinglePositionRate, 30.0)
    elif request.holding.positionRate <= 1 and early_trend_bootstrap_buy:
        max_single_buy = min(request.strategyParams.maxSinglePositionRate, 35.0)
    elif request.holding.positionRate <= 1 and trend_repair_buy:
        max_single_buy = min(request.strategyParams.maxSinglePositionRate, 35.0)
    elif strong_trend_lock:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 35.0))
    elif trend_start_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 30.0))
    elif core_trend_allocation_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 35.0))
    elif midterm_trend_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 25.0))
    elif benchmark_alignment_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 25.0))
    elif recoverable_pullback_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 25.0))
    elif early_trend_bootstrap_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 25.0))
    elif trend_repair_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 30.0))
    elif score.totalScore >= 86 and score.riskScore >= 45:
        max_single_buy = min(request.strategyParams.buyStepRatio, 10.0)
    elif score.totalScore >= 80:
        max_single_buy = min(request.strategyParams.buyStepRatio, 7.0)
    else:
        max_single_buy = min(request.strategyParams.buyStepRatio, 5.0)
    room_single = max(request.strategyParams.maxSinglePositionRate - request.holding.positionRate, 0)
    return round(max(min(max_single_buy, room_single), 0), 2)


def _should_buy(
    score: ScoreBreakdown,
    features: dict,
    request: QuantAnalyzeRequest,
    strong_trend_lock: bool = False,
    trend_start_buy: bool = False,
    midterm_trend_buy: bool = False,
    recoverable_pullback_buy: bool = False,
    benchmark_alignment_buy: bool = False,
    core_trend_allocation_buy: bool = False,
    early_trend_bootstrap_buy: bool = False,
    trend_repair_buy: bool = False,
    fund_type: str | None = None,
) -> bool:
    params = request.strategyParams
    position_ratio = float(features.get("positionToSingleLimit", 1))
    effective_type = fund_type or request.holding.fundType
    if _is_weak_trend_defense(features, score, effective_type):
        return False
    if (
        strong_trend_lock
        and score.totalScore >= 50
        and score.riskScore >= 25
        and score.positionScore >= 35
        and position_ratio < 0.90
    ):
        return True
    if trend_start_buy and score.totalScore >= 50 and score.positionScore >= 35 and position_ratio < 0.90:
        return True
    if midterm_trend_buy and score.totalScore >= 48 and score.positionScore >= 35 and position_ratio < 0.85:
        return True
    if core_trend_allocation_buy and score.positionScore >= 35 and position_ratio < 0.98:
        return True
    if early_trend_bootstrap_buy and score.positionScore >= 35 and position_ratio < 0.90:
        return True
    if trend_repair_buy and score.positionScore >= 35 and position_ratio < 0.90:
        return True
    if recoverable_pullback_buy and score.totalScore >= 48 and score.positionScore >= 35 and position_ratio < 0.90:
        return True
    if benchmark_alignment_buy and score.positionScore >= 35 and position_ratio < 0.90:
        return True
    return (
        score.totalScore >= params.buyThreshold
        and score.trendScore >= 50
        and score.opportunityScore >= 48
        and score.riskScore >= 25
        and score.positionScore >= 40
        and position_ratio < 0.90
        and not _is_short_term_breakdown(features)
    )


def _is_short_term_breakdown(features: dict) -> bool:
    return float(features.get("return5d", 0)) <= -1 and float(features.get("return20d", 0)) <= 0


def _market_exit_reason(
    score: ScoreBreakdown,
    features: dict,
    request: QuantAnalyzeRequest,
    strong_trend_lock: bool,
    state: dict,
) -> str | None:
    if request.holding.holdingAmount <= 0:
        return None
    drawdown60 = _current_drawdown60(features)
    profit_rate = request.holding.holdingProfitRate
    params = request.strategyParams

    if profit_rate <= -18 or drawdown60 >= EXTREME_RISK_DRAWDOWN:
        previous_drawdown = state.get("lastExtremeDrawdown")
        is_distinct_follow_up = (
            state["extremeRiskStage"] >= 1
            and not state["sameDecisionDay"]
            and previous_drawdown is not None
        )
        if is_distinct_follow_up and float(previous_drawdown) - drawdown60 >= EXTREME_RISK_RECOVERY_POINTS:
            state["extremeRiskRecoveryWatch"] = True
            return None
        return "extreme_risk_exit"
    if strong_trend_lock:
        return None
    if profit_rate <= params.stopLossRate:
        return "risk_exit"
    if state["weakTrendConfirmed"] and not state["weakTrendDefenseHandled"]:
        return "weak_trend_defense"
    if profit_rate >= params.takeProfitRate:
        return "profit_exit"
    if score.totalScore <= params.sellThreshold:
        return "score_exit"
    return None


def _should_sell_by_market(
    score: ScoreBreakdown,
    features: dict,
    request: QuantAnalyzeRequest,
    strong_trend_lock: bool = False,
    fund_type: str | None = None,
) -> bool:
    state = _advance_execution_state(
        request,
        score,
        dict(features),
        fund_type or request.holding.fundType,
    )
    return _market_exit_reason(score, features, request, strong_trend_lock, state) is not None


def _normalize_fund_type(fund_type: str | None) -> str:
    return normalize_fund_type(fund_type)


def _is_active_equity_type(fund_type: str | None) -> bool:
    return is_active_fund_type(fund_type)


def _is_qdii_or_overseas_type(fund_type: str | None) -> bool:
    return is_qdii_or_overseas_type(fund_type)


def _is_strong_trend_lock(features: dict, params, fund_type: str | None = None) -> bool:
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    drawdown_limit = 21 if _is_active_equity_type(fund_type) else 18 if _is_qdii_or_overseas_type(fund_type) else 20
    return return20 >= params.trendHoldReturn20d and return60 >= 5 and ma20_deviation >= params.trendHoldMa20Deviation and drawdown60 < drawdown_limit


def _is_trend_start_buy(features: dict, score: ScoreBreakdown, params, fund_type: str | None = None) -> bool:
    return20 = float(features.get("return20d", 0))
    return5 = float(features.get("return5d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    qdii = _is_qdii_or_overseas_type(fund_type)
    loss_day_limit = 62.0 if qdii else 68.0 if _is_active_equity_type(fund_type) else 65.0
    risk_score_min = 32.0 if qdii else 28.0 if _is_active_equity_type(fund_type) else 30.0
    return (
        return20 >= params.trendHoldReturn20d
        and return5 >= 0
        and ma20_deviation >= params.trendHoldMa20Deviation
        and drawdown60 < 20
        and loss_day_ratio20 <= loss_day_limit
        and score.riskScore >= risk_score_min
    )


def _is_midterm_trend_buy(features: dict, score: ScoreBreakdown, fund_type: str | None = None) -> bool:
    return5 = float(features.get("return5d", 0))
    return20 = float(features.get("return20d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    qdii = _is_qdii_or_overseas_type(fund_type)
    risk_score_min = 30.0 if qdii else 24.0 if _is_active_equity_type(fund_type) else 25.0
    loss_day_limit = 66.0 if qdii else 72.0 if _is_active_equity_type(fund_type) else 70.0
    return (
        _is_midterm_trend_pullback(features, fund_type)
        and return5 >= -1
        and return20 >= -5
        and ma20_deviation >= -10
        and loss_day_ratio20 <= loss_day_limit
        and score.riskScore >= risk_score_min
    )


def _is_midterm_trend_pullback(features: dict, fund_type: str | None = None) -> bool:
    return60 = float(features.get("return60d", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    return60_min = 8 if _is_active_equity_type(fund_type) else 12 if _is_qdii_or_overseas_type(fund_type) else 10
    return return60 >= return60_min and drawdown60 < 22


def _is_orderly_trend_hold(features: dict, params, fund_type: str | None = None) -> bool:
    return60 = float(features.get("return60d", 0))
    return20 = float(features.get("return20d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    return60_min = 7 if _is_active_equity_type(fund_type) else 10 if _is_qdii_or_overseas_type(fund_type) else 8
    loss_day_limit = 68 if _is_active_equity_type(fund_type) else 60 if _is_qdii_or_overseas_type(fund_type) else 65
    return (
        return60 >= return60_min
        and return20 >= -4
        and ma20_deviation >= float(params.trendHoldMa20Deviation) - 3
        and drawdown60 < 18
        and loss_day_ratio20 <= loss_day_limit
    )


def _is_weak_trend_defense(features: dict, score: ScoreBreakdown, fund_type: str | None = None) -> bool:
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = _current_drawdown60(features)
    if _is_midterm_trend_pullback(features, fund_type):
        return False
    if _is_recoverable_pullback(features, score):
        return False
    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return20_limit = -7 if active else -5 if qdii else -6
    return60_limit = -1 if active else 0
    ma20_limit = -5 if active else -3.5 if qdii else -4
    drawdown_limit = 20 if active else 16 if qdii else 18
    trend_score_limit = 46 if active else 50 if qdii else 48
    return (
        (return20 <= return20_limit and return60 <= return60_limit and ma20_deviation <= ma20_limit)
        or (drawdown60 >= drawdown_limit and score.trendScore < trend_score_limit)
    )


def _is_weak_trend_recovered(features: dict, score: ScoreBreakdown, params, fund_type: str | None = None) -> bool:
    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return60_min = 5.5 if active else 6.0 if qdii else 4.5
    trend_score_min = 55.0 if active else 58.0 if qdii else 55.0
    risk_score_min = 30.0 if active else 34.0 if qdii else 30.0
    loss_day_limit = 62.0 if active else 58.0 if qdii else 62.0
    return (
        float(features.get("return20d", 0)) > max(float(params.trendHoldReturn20d), 0.0)
        and float(features.get("return60d", 0)) > return60_min
        and float(features.get("ma20Deviation", 0)) >= max(float(params.trendHoldMa20Deviation), -6.0)
        and _current_drawdown60(features) < 18
        and score.trendScore >= trend_score_min
        and score.riskScore >= risk_score_min
        and float(features.get("lossDayRatio20d", 0)) <= loss_day_limit
    )


def _is_recoverable_pullback(features: dict, score: ScoreBreakdown) -> bool:
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    drawdown60 = _current_drawdown60(features)
    return (
        return60 > -8
        and return20 > -18
        and drawdown60 < 18
        and score.opportunityScore >= 65
        and score.riskScore >= 30
    )


def _is_recoverable_pullback_buy(features: dict, score: ScoreBreakdown) -> bool:
    return5 = float(features.get("return5d", 0))
    return20 = float(features.get("return20d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    return (
        _is_recoverable_pullback(features, score)
        and return5 >= 0
        and return20 > -12
        and ma20_deviation >= -12
        and score.trendScore >= 45
        and loss_day_ratio20 <= 70
    )


def _is_benchmark_alignment_buy(features: dict, score: ScoreBreakdown, params, position_rate: float, fund_type: str | None = None) -> bool:
    target_position = float(params.maxSinglePositionRate)
    target_near_full = min(target_position * 0.9, target_position - 1.0)
    if float(position_rate) >= target_near_full:
        return False
    if _is_weak_trend_defense(features, score, fund_type):
        return False

    return5 = float(features.get("return5d", 0))
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    qdii = _is_qdii_or_overseas_type(fund_type)
    risk_score_min = 30.0 if qdii else 25.0
    drawdown_limit = 16.0 if qdii else 18.0
    loss_day_limit = 64.0 if qdii else 68.0
    return (
        score.totalScore >= max(float(params.buyThreshold) - 3.0, 48.0)
        and score.trendScore >= 45
        and score.opportunityScore >= 45
        and score.riskScore >= risk_score_min
        and return20 >= -1
        and return60 >= 0
        and ma20_deviation >= float(params.trendHoldMa20Deviation) - 2.0
        and drawdown60 < drawdown_limit
        and loss_day_ratio20 <= loss_day_limit
        and not (return5 <= -2 and return20 <= 0)
    )


def _is_core_trend_allocation_buy(features: dict, score: ScoreBreakdown, params, position_rate: float, fund_type: str | None = None) -> bool:
    target_position = float(params.maxSinglePositionRate)
    target_near_full = min(target_position * 0.98, target_position - 0.1)
    if float(position_rate) >= target_near_full:
        return False
    if _is_weak_trend_defense(features, score, fund_type):
        return False

    return5 = float(features.get("return5d", 0))
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return60_confirm = 7.0 if active else 9.0 if qdii else 8.0
    risk_score_min = 24.0 if active else 30.0 if qdii else 25.0
    drawdown_limit = 17.0 if active else 14.0 if qdii else 16.0
    loss_day_limit = 64.0 if active else 58.0 if qdii else 62.0
    trend_confirmed = return60 >= return60_confirm or (
        return20 >= max(float(params.trendHoldReturn20d), 1.0)
        and return60 >= 4.0
    )

    return (
        trend_confirmed
        and score.totalScore >= max(float(params.buyThreshold) - 6.0, 46.0)
        and score.trendScore >= 42
        and score.opportunityScore >= 40
        and score.riskScore >= risk_score_min
        and return20 >= -0.5
        and ma20_deviation >= float(params.trendHoldMa20Deviation) - 1.0
        and drawdown60 < drawdown_limit
        and loss_day_ratio20 <= loss_day_limit
        and not (return5 <= -2.5 and return20 <= 0)
    )


def _is_early_trend_bootstrap_buy(features: dict, score: ScoreBreakdown, params, position_rate: float, fund_type: str | None = None) -> bool:
    sample_size = int(features.get("navSampleSize", 0))
    if sample_size <= 0 or sample_size > EARLY_TREND_BOOTSTRAP_SAMPLES:
        return False
    target_position = float(params.maxSinglePositionRate)
    if float(position_rate) >= min(target_position * 0.9, target_position - 1.0):
        return False
    if _is_weak_trend_defense(features, score, fund_type):
        return False

    return5 = float(features.get("return5d", 0))
    return20 = float(features.get("return20d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    trend_slope20 = float(features.get("trendSlope20d", 0))
    early_momentum_confirmed = return20 >= max(float(params.trendHoldReturn20d) * 0.6, 0.8) or (
        return20 >= 0.5 and trend_slope20 >= 0.12
    )

    qdii = _is_qdii_or_overseas_type(fund_type)
    risk_score_min = 32.0 if qdii else 28.0
    drawdown_limit = 13.0 if qdii else 15.0
    return (
        sample_size >= int(params.minNavSamples)
        and early_momentum_confirmed
        and score.totalScore >= max(float(params.buyThreshold) - 5.0, 46.0)
        and score.trendScore >= 42
        and score.riskScore >= risk_score_min
        and return5 >= -1.0
        and ma20_deviation >= float(params.trendHoldMa20Deviation) - 2.0
        and drawdown60 < drawdown_limit
        and loss_day_ratio20 <= 62
    )


def _is_trend_repair_buy(features: dict, score: ScoreBreakdown, params, position_rate: float, fund_type: str | None = None) -> bool:
    target_position = float(params.maxSinglePositionRate)
    if float(position_rate) >= min(target_position * 0.9, target_position - 1.0):
        return False
    if _is_weak_trend_defense(features, score, fund_type):
        return False

    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return60_min = 2.5 if active else 5.0 if qdii else 4.0
    trend_score_min = 50.0 if active else 58.0 if qdii else 54.0
    risk_score_min = 28.0 if active else 34.0 if qdii else 30.0
    drawdown_limit = 16.5 if active else 12.5 if qdii else 15.0
    loss_day_limit = 66.0 if active else 58.0 if qdii else 62.0
    return (
        float(features.get("return20d", 0)) >= max(float(params.trendHoldReturn20d) * 0.5, 0.8)
        and float(features.get("return60d", 0)) >= return60_min
        and float(features.get("return5d", 0)) >= -0.5
        and float(features.get("ma20Deviation", 0)) >= max(float(params.trendHoldMa20Deviation), -7.0)
        and abs(float(features.get("maxDrawdown60d", 0))) < drawdown_limit
        and score.trendScore >= trend_score_min
        and score.riskScore >= risk_score_min
        and float(features.get("lossDayRatio20d", 0)) <= loss_day_limit
    )


def _sell_ratio(exit_reason: str, request: QuantAnalyzeRequest, state: dict) -> float:
    params = request.strategyParams
    if exit_reason == "extreme_risk_exit":
        if request.holding.positionRate <= TAIL_POSITION_RATE:
            return 100.0
        if state["extremeRiskStage"] >= 1 and not state["sameDecisionDay"]:
            return 100.0
        return max(params.sellStepRatio, 50.0)
    if exit_reason == "risk_exit":
        return max(params.sellStepRatio, 35.0)
    if exit_reason == "profit_exit":
        return max(params.sellStepRatio, 20.0)
    if exit_reason == "weak_trend_defense":
        return max(params.sellStepRatio, 15.0)
    return params.sellStepRatio


def _sell_ratio_by_market(score: ScoreBreakdown, features: dict, request: QuantAnalyzeRequest, fund_type: str | None = None) -> float:
    state = _advance_execution_state(request, score, dict(features), fund_type or request.holding.fundType)
    reason = _market_exit_reason(score, features, request, False, state)
    return _sell_ratio(reason or "score_exit", request, state)


def _buy_reason(
    strong_trend_lock: bool,
    trend_start_buy: bool,
    midterm_trend_buy: bool,
    recoverable_pullback_buy: bool,
    benchmark_alignment_buy: bool,
    core_trend_allocation_buy: bool,
    early_trend_bootstrap_buy: bool,
    trend_repair_buy: bool,
) -> str:
    if strong_trend_lock:
        return "strong_trend_buy"
    if trend_start_buy:
        return "trend_start_buy"
    if core_trend_allocation_buy:
        return "core_trend_allocation_buy"
    if midterm_trend_buy:
        return "midterm_trend_buy"
    if benchmark_alignment_buy:
        return "benchmark_alignment_buy"
    if recoverable_pullback_buy:
        return "recoverable_pullback_buy"
    if early_trend_bootstrap_buy:
        return "early_trend_bootstrap_buy"
    if trend_repair_buy:
        return "trend_repair_buy"
    return "score_above_buy_threshold"


def _suggest_amount(request: QuantAnalyzeRequest, ratio: float) -> float:
    if ratio <= 0 or request.account.totalAsset <= 0:
        return 0.0
    amount = request.account.totalAsset * ratio / 100
    if amount < 100:
        return 0.0
    return round(amount, 2)


def _sell_amount(request: QuantAnalyzeRequest, ratio: float) -> float:
    if ratio <= 0 or request.holding.holdingAmount <= 0:
        return 0.0
    return round(request.holding.holdingAmount * ratio / 100, 2)


def _current_drawdown60(features: dict) -> float:
    value = features.get("currentDrawdown60d")
    if value is None:
        value = features.get("maxDrawdown60d", 0)
    return abs(float(value or 0))


def _decision_date(request: QuantAnalyzeRequest) -> str | None:
    now = request.market.now
    return now.date().isoformat() if now else None
