from __future__ import annotations

from app.core.schemas import QuantAnalyzeRequest


def buy_blockers(request: QuantAnalyzeRequest, features: dict) -> list[str]:
    blockers: list[str] = []
    if request.holding.positionRate >= request.strategyParams.maxSinglePositionRate:
        blockers.append("单基金仓位已达到或超过风险配置上限")
    if not request.market.tradingDay:
        blockers.append("非交易日不生成当日 BUY 建议")
    return blockers
