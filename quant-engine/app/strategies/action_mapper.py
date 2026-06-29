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
        return "SELL", 0.0, ratio, blockers

    if score.riskScore < 32 or total < 35:
        return "SELL", 0.0, 10.0, blockers

    if total >= 80 and not blockers:
        ratio = _buy_ratio(request, score)
        amount = _suggest_amount(request, ratio)
        if amount <= 0:
            return "HOLD", 0.0, 0.0, blockers
        return "BUY", amount, ratio, blockers

    if total >= 80 and blockers:
        return "WATCH", 0.0, 0.0, blockers

    if total >= 58:
        return "HOLD", 0.0, 0.0, blockers
    return "WATCH", 0.0, 0.0, blockers


def _buy_ratio(request: QuantAnalyzeRequest, score: ScoreBreakdown) -> float:
    max_single_buy = 10.0 if score.totalScore >= 88 else 5.0
    room_single = max(request.riskProfile.maxSingleFundPositionRate - request.holding.positionRate, 0)
    room_equity = max(request.riskProfile.maxEquityPositionRate - request.account.equityPositionRate, 0)
    return round(max(min(max_single_buy, room_single, room_equity), 0), 2)


def _suggest_amount(request: QuantAnalyzeRequest, ratio: float) -> float:
    if ratio <= 0 or request.account.totalAsset <= 0:
        return 0.0
    amount = request.account.totalAsset * ratio / 100
    if amount < 100:
        return 0.0
    return round(amount, 2)
