from __future__ import annotations

from app.core.schemas import QuantAnalyzeRequest
from app.features.market_features import calculate_market_features
from app.features.nav_features import calculate_nav_features
from app.features.position_features import calculate_position_features
from app.features.risk_features import calculate_risk_features


def build_features(request: QuantAnalyzeRequest) -> dict[str, float | bool | int | str | None]:
    features: dict[str, float | bool | int | str | None] = {}
    features.update(calculate_nav_features(request.navSeries))
    features.update(calculate_risk_features(request.navSeries))
    features.update(calculate_position_features(request.account, request.riskProfile, request.holding))
    features.update(calculate_market_features(request.holding, request.market))
    features["holdingProfitRate"] = round(request.holding.holdingProfitRate, 4)
    features["holdingDays"] = request.holding.holdingDays
    return features
