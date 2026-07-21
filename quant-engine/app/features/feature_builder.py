from __future__ import annotations

from app.core.schemas import QuantAnalyzeRequest
from app.features.market_features import calculate_market_features
from app.features.nav_features import calculate_nav_features
from app.features.position_features import calculate_position_features
from app.features.risk_features import calculate_risk_features
from app.ml.features import fund_profile_features


POSITION_LIMIT_REBALANCE_BUFFER = 5.0


def build_features(request: QuantAnalyzeRequest) -> dict[str, float | bool | int | str | None]:
    features: dict[str, float | bool | int | str | None] = {}
    features.update(calculate_nav_features(request.navSeries))
    features.update(calculate_risk_features(request.navSeries))
    features.update(calculate_position_features(request.account, request.riskProfile, request.holding))
    features.update(fund_profile_features(request.holding.fundCode, request.holding.fundName, request.holding.fundType))
    _apply_strategy_position_limit(features, request)
    features.update(calculate_market_features(request.holding, request.market))
    features["holdingProfitRate"] = round(request.holding.holdingProfitRate, 4)
    features["holdingDays"] = request.holding.holdingDays
    return features


def _apply_strategy_position_limit(features: dict[str, float | bool | int | str | None], request: QuantAnalyzeRequest) -> None:
    single_limit = request.strategyParams.maxSinglePositionRate
    if single_limit <= 0:
        return
    features["positionToSingleLimit"] = round(request.holding.positionRate / single_limit, 4)
    features["canBuyMore"] = request.holding.positionRate < single_limit
    features["shouldReduceByPosition"] = request.holding.positionRate >= single_limit + POSITION_LIMIT_REBALANCE_BUFFER
