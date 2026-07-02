from __future__ import annotations

from app.core.schemas import QuantAnalyzeRequest, ScoreBreakdown
from app.strategies.constraints import buy_blockers


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

    if features.get("shouldReduceByPosition"):
        ratio = params.sellStepRatio if request.holding.holdingProfitRate < 15 else max(params.sellStepRatio, 20)
        return "SELL", _sell_amount(request, ratio), ratio, blockers

    strong_trend_lock = _is_strong_trend_lock(features, params)
    trend_start_buy = _is_trend_start_buy(features, score, params)
    midterm_trend_buy = _is_midterm_trend_buy(features, score)

    if _should_sell_by_market(score, features, request, strong_trend_lock):
        ratio = _sell_ratio_by_market(score, features, request)
        return "SELL", _sell_amount(request, ratio), ratio, blockers

    if _should_buy(score, features, request, strong_trend_lock, trend_start_buy, midterm_trend_buy) and not blockers:
        ratio = _buy_ratio(request, score, strong_trend_lock, trend_start_buy, midterm_trend_buy)
        amount = _suggest_amount(request, ratio)
        if amount <= 0:
            return "HOLD", 0.0, 0.0, blockers
        return "BUY", amount, ratio, blockers

    if _should_buy(score, features, request, strong_trend_lock, trend_start_buy, midterm_trend_buy) and blockers:
        return "WATCH", 0.0, 0.0, blockers

    if total >= params.buyThreshold:
        return "HOLD", 0.0, 0.0, blockers
    return "WATCH", 0.0, 0.0, blockers


def _buy_ratio(
    request: QuantAnalyzeRequest,
    score: ScoreBreakdown,
    strong_trend_lock: bool = False,
    trend_start_buy: bool = False,
    midterm_trend_buy: bool = False,
) -> float:
    if request.holding.positionRate <= 1 and (strong_trend_lock or trend_start_buy):
        max_single_buy = request.strategyParams.maxSinglePositionRate
    elif request.holding.positionRate <= 1 and midterm_trend_buy:
        max_single_buy = min(request.strategyParams.maxSinglePositionRate, 30.0)
    elif strong_trend_lock:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 30.0))
    elif trend_start_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 25.0))
    elif midterm_trend_buy:
        max_single_buy = max(request.strategyParams.buyStepRatio, min(request.strategyParams.maxSinglePositionRate, 20.0))
    elif score.totalScore >= 86 and score.riskScore >= 45:
        max_single_buy = min(request.strategyParams.buyStepRatio, 8.0)
    elif score.totalScore >= 80:
        max_single_buy = min(request.strategyParams.buyStepRatio, 5.0)
    else:
        max_single_buy = min(request.strategyParams.buyStepRatio, 3.0)
    room_single = max(request.strategyParams.maxSinglePositionRate - request.holding.positionRate, 0)
    return round(max(min(max_single_buy, room_single), 0), 2)


def _should_buy(
    score: ScoreBreakdown,
    features: dict,
    request: QuantAnalyzeRequest,
    strong_trend_lock: bool = False,
    trend_start_buy: bool = False,
    midterm_trend_buy: bool = False,
) -> bool:
    params = request.strategyParams
    position_ratio = float(features.get("positionToSingleLimit", 1))
    if _is_weak_trend_defense(features, score):
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
    return (
        score.totalScore >= params.buyThreshold
        and score.trendScore >= 55
        and score.opportunityScore >= 48
        and score.riskScore >= 25
        and score.positionScore >= 40
        and position_ratio < 0.85
    )


def _should_sell_by_market(
    score: ScoreBreakdown,
    features: dict,
    request: QuantAnalyzeRequest,
    strong_trend_lock: bool = False,
) -> bool:
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_pressure = float(features.get("lossPressure", 0))
    profit_rate = request.holding.holdingProfitRate
    params = request.strategyParams

    extreme_stop_loss = loss_pressure >= abs(params.stopLossRate) or drawdown60 >= 22
    if extreme_stop_loss:
        return True
    if (strong_trend_lock or _is_midterm_trend_pullback(features)) and loss_pressure < 12:
        return False
    if _is_recoverable_pullback(features, score) and loss_pressure < 8:
        return False

    weak_trend_defense = _is_weak_trend_defense(features, score)
    deep_loss_breakdown = loss_pressure >= 12 and score.trendScore < 45 and drawdown60 >= 12
    trend_breakdown = return20 < -8 and return60 < -5 and score.trendScore < 40
    profit_giveback = profit_rate >= params.takeProfitRate and drawdown60 >= 10 and score.trendScore < 55
    extreme_score_breakdown = score.totalScore < params.sellThreshold and score.trendScore < 45
    return weak_trend_defense or deep_loss_breakdown or trend_breakdown or profit_giveback or extreme_score_breakdown


def _is_strong_trend_lock(features: dict, params) -> bool:
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    return return20 >= params.trendHoldReturn20d and return60 >= 8 and ma20_deviation >= params.trendHoldMa20Deviation and drawdown60 < 16


def _is_trend_start_buy(features: dict, score: ScoreBreakdown, params) -> bool:
    return20 = float(features.get("return20d", 0))
    return5 = float(features.get("return5d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    return (
        return20 >= params.trendHoldReturn20d
        and return5 >= 0
        and ma20_deviation >= params.trendHoldMa20Deviation
        and drawdown60 < 16
        and loss_day_ratio20 <= 58
        and score.riskScore >= 35
    )


def _is_midterm_trend_buy(features: dict, score: ScoreBreakdown) -> bool:
    return5 = float(features.get("return5d", 0))
    return20 = float(features.get("return20d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    loss_day_ratio20 = float(features.get("lossDayRatio20d", 0))
    return (
        _is_midterm_trend_pullback(features)
        and return5 >= -1
        and return20 >= -4
        and ma20_deviation >= -8
        and loss_day_ratio20 <= 65
        and score.riskScore >= 25
    )


def _is_midterm_trend_pullback(features: dict) -> bool:
    return60 = float(features.get("return60d", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    return return60 >= 12 and drawdown60 < 22


def _is_weak_trend_defense(features: dict, score: ScoreBreakdown) -> bool:
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    ma20_deviation = float(features.get("ma20Deviation", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    if _is_midterm_trend_pullback(features):
        return False
    if _is_recoverable_pullback(features, score):
        return False
    return (
        (return20 <= -6 and return60 <= 0 and ma20_deviation <= -4)
        or (drawdown60 >= 15 and score.trendScore < 50)
    )


def _is_recoverable_pullback(features: dict, score: ScoreBreakdown) -> bool:
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    return (
        return60 > -8
        and return20 > -18
        and drawdown60 < 18
        and score.opportunityScore >= 65
        and score.riskScore >= 30
    )


def _sell_ratio_by_market(score: ScoreBreakdown, features: dict, request: QuantAnalyzeRequest) -> float:
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_pressure = float(features.get("lossPressure", 0))
    params = request.strategyParams
    if loss_pressure >= abs(params.stopLossRate) or drawdown60 >= 22:
        return max(params.sellStepRatio, 50.0)
    if loss_pressure >= 12 and score.trendScore < 45 and drawdown60 >= 12:
        return max(params.sellStepRatio, 35.0)
    if request.holding.holdingProfitRate >= params.takeProfitRate and drawdown60 >= 12:
        return max(params.sellStepRatio, 20.0)
    return params.sellStepRatio


def _suggest_amount(request: QuantAnalyzeRequest, ratio: float) -> float:
    if ratio <= 0 or request.holding.holdingAmount <= 0:
        return 0.0
    amount = request.holding.holdingAmount * ratio / 100
    if amount < 100:
        return 0.0
    return round(amount, 2)


def _sell_amount(request: QuantAnalyzeRequest, ratio: float) -> float:
    if ratio <= 0 or request.holding.holdingAmount <= 0:
        return 0.0
    return round(request.holding.holdingAmount * ratio / 100, 2)
