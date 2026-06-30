from __future__ import annotations

from datetime import time

from app.core.schemas import QuantAnalyzeRequest


NO_BUY_AFTER = time(14, 57, 0)


def buy_blockers(request: QuantAnalyzeRequest, features: dict) -> list[str]:
    blockers: list[str] = []
    now = request.market.now
    if now and now.time() >= NO_BUY_AFTER:
        blockers.append("14:57 后不得生成 BUY 建议")
    if request.holding.positionRate >= request.strategyParams.maxSinglePositionRate:
        blockers.append("单基金仓位已达到或超过风险配置上限")
    if not request.market.tradingDay:
        blockers.append("非交易日不生成当日 BUY 建议")
    return blockers
