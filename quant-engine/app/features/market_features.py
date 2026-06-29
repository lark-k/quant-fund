from __future__ import annotations

from app.core.schemas import HoldingSnapshot, MarketContext


QDII_TYPES = {"QDII", "OVERSEAS", "GLOBAL", "HK", "HONGKONG"}


def calculate_market_features(holding: HoldingSnapshot, market: MarketContext) -> dict[str, float | bool | str | None]:
    fund_type = (holding.fundType or "").upper()
    is_qdii = any(token in fund_type for token in QDII_TYPES)
    return {
        "themeRate": round(holding.relatedThemeRate or 0, 4),
        "estimateGrowthRate": round(holding.currentEstimateGrowthRate or 0, 4),
        "tradingDay": market.tradingDay,
        "trading": market.trading,
        "decisionPhase": market.decisionPhase,
        "isQdiiOrOverseas": is_qdii,
    }
