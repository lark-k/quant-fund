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
    total = score.totalScore

    if features.get("shouldReduceByPosition"):
        ratio = 10 if request.holding.holdingProfitRate < 15 else 20
        return "SELL", _sell_amount(request, ratio), ratio, blockers

    if _should_sell_by_market(score, features, request):
        ratio = _sell_ratio_by_market(score, features, request)
        return "SELL", _sell_amount(request, ratio), ratio, blockers

    if _should_buy(score, features) and not blockers:
        ratio = _buy_ratio(request, score)
        amount = _suggest_amount(request, ratio)
        if amount <= 0:
            return "HOLD", 0.0, 0.0, blockers
        return "BUY", amount, ratio, blockers

    if _should_buy(score, features) and blockers:
        return "WATCH", 0.0, 0.0, blockers

    if total >= 55:
        return "HOLD", 0.0, 0.0, blockers
    return "WATCH", 0.0, 0.0, blockers


def _buy_ratio(request: QuantAnalyzeRequest, score: ScoreBreakdown) -> float:
    if score.totalScore >= 86 and score.riskScore >= 45:
        max_single_buy = 8.0
    elif score.totalScore >= 80:
        max_single_buy = 5.0
    else:
        max_single_buy = 3.0
    room_single = max(request.riskProfile.maxSingleFundPositionRate - request.holding.positionRate, 0)
    return round(max(min(max_single_buy, room_single), 0), 2)


def _should_buy(score: ScoreBreakdown, features: dict) -> bool:
    position_ratio = float(features.get("positionToSingleLimit", 1))
    return (
        score.totalScore >= 74
        and score.trendScore >= 62
        and score.opportunityScore >= 55
        and score.positionScore >= 45
        and position_ratio < 0.85
    )


def _should_sell_by_market(score: ScoreBreakdown, features: dict, request: QuantAnalyzeRequest) -> bool:
    return20 = float(features.get("return20d", 0))
    return60 = float(features.get("return60d", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_pressure = float(features.get("lossPressure", 0))
    profit_rate = request.holding.holdingProfitRate

    deep_loss_breakdown = loss_pressure >= 12 and score.trendScore < 45 and drawdown60 >= 12
    trend_breakdown = return20 < -8 and return60 < -5 and score.trendScore < 40
    profit_giveback = profit_rate >= 12 and drawdown60 >= 10 and score.trendScore < 55
    extreme_score_breakdown = score.totalScore < 32 and score.trendScore < 45
    return deep_loss_breakdown or trend_breakdown or profit_giveback or extreme_score_breakdown


def _sell_ratio_by_market(score: ScoreBreakdown, features: dict, request: QuantAnalyzeRequest) -> float:
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    if request.holding.holdingProfitRate >= 15 and drawdown60 >= 12:
        return 20.0
    return 10.0


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
