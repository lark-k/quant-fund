from __future__ import annotations

import math
from typing import Any

from app.strategies.fund_profile import resolve_fund_profile

BASE_FEATURE_COLUMNS: list[str] = [
    "return5d",
    "return20d",
    "return60d",
    "return120d",
    "ma20Deviation",
    "ma60Deviation",
    "ma120Deviation",
    "trendSlope20d",
    "volatility20d",
    "volatility60d",
    "maxDrawdown20d",
    "maxDrawdown60d",
    "maxDrawdown120d",
    "lossDayRatio20d",
    "consecutiveUpDays",
    "consecutiveDownDays",
    "positionToSingleLimit",
    "profitBuffer",
    "lossPressure",
    "themeRate",
    "estimateGrowthRate",
    "navSampleSize",
    "holdingProfitRate",
    "holdingDays",
    "trackingIndexReturn20d",
    "trackingIndexReturn60d",
    "trackingIndexReturn120d",
    "trackingExcessReturn20d",
    "trackingExcessReturn60d",
    "trackingExcessReturn120d",
    "marketSh000001Return20d",
    "marketSh000001Return60d",
    "marketSh000001Return120d",
    "marketSz399001Return20d",
    "marketSz399001Return60d",
    "marketSz399001Return120d",
    "marketCyb399006Return20d",
    "marketCyb399006Return60d",
    "marketCyb399006Return120d",
    "marketHs300Return20d",
    "marketHs300Return60d",
    "marketHs300Return120d",
    "marketZz500Return20d",
    "marketZz500Return60d",
    "marketZz500Return120d",
]

PROFILE_FEATURE_COLUMNS: list[str] = [
    "isActiveFund",
    "isActiveQdii",
    "isIndexFund",
    "isEtfFund",
    "isEtfQdii",
    "isQdiiFund",
    "isPassiveFund",
    "typeActiveEquity",
    "typeActiveQdii",
    "typeIndex",
    "typeIndexQdii",
    "typeEtf",
    "typeEtfQdii",
    "profileStableAnnualReturn",
    "profileStableDrawdownFloor",
    "profileStableBenchmarkGap",
    "profileMaxAnnualTrades",
    "profileDefensiveAnnualReturn",
    "profileDefensiveDrawdownFloor",
    "profileDefensiveBenchmarkGap",
]

FEATURE_COLUMNS: list[str] = [*BASE_FEATURE_COLUMNS, *PROFILE_FEATURE_COLUMNS]


def fund_profile_features(fund_code: Any, fund_name: Any, fund_type: Any) -> dict[str, float]:
    profile = resolve_fund_profile(_optional_text(fund_code), _optional_text(fund_name), _optional_text(fund_type))
    effective_type = profile.effectiveType.upper()
    management_style = profile.managementStyle.upper()
    vehicle_type = profile.vehicleType.upper()
    return {
        "isActiveFund": _flag(management_style == "ACTIVE"),
        "isActiveQdii": _flag(effective_type == "ACTIVE_QDII"),
        "isIndexFund": _flag(management_style == "INDEX" or effective_type in {"INDEX", "INDEX_QDII"}),
        "isEtfFund": _flag("ETF" in effective_type or "ETF" in vehicle_type),
        "isEtfQdii": _flag(effective_type == "ETF_QDII"),
        "isQdiiFund": _flag("QDII" in effective_type or "QDII" in vehicle_type),
        "isPassiveFund": _flag(management_style == "PASSIVE"),
        "typeActiveEquity": _flag(effective_type == "ACTIVE_EQUITY"),
        "typeActiveQdii": _flag(effective_type == "ACTIVE_QDII"),
        "typeIndex": _flag(effective_type == "INDEX"),
        "typeIndexQdii": _flag(effective_type == "INDEX_QDII"),
        "typeEtf": _flag(effective_type == "ETF"),
        "typeEtfQdii": _flag(effective_type == "ETF_QDII"),
        "profileStableAnnualReturn": _to_float(profile.stableAnnualReturn),
        "profileStableDrawdownFloor": _to_float(profile.stableDrawdownFloor),
        "profileStableBenchmarkGap": _to_float(profile.stableBenchmarkGap),
        "profileMaxAnnualTrades": _to_float(profile.maxAnnualTrades),
        "profileDefensiveAnnualReturn": _to_float(profile.defensiveAnnualReturn),
        "profileDefensiveDrawdownFloor": _to_float(profile.defensiveDrawdownFloor),
        "profileDefensiveBenchmarkGap": _to_float(profile.defensiveBenchmarkGap),
    }


def feature_row(features: dict[str, Any], columns: list[str] | None = None) -> list[float]:
    return [_to_float(features.get(column, 0.0)) for column in (columns or FEATURE_COLUMNS)]


def feature_matrix(rows: list[dict[str, Any]], columns: list[str] | None = None) -> list[list[float]]:
    return [feature_row(row, columns) for row in rows]


def _to_float(value: Any) -> float:
    if value is None:
        return 0.0
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    try:
        number = float(value)
    except (TypeError, ValueError):
        return 0.0
    if math.isnan(number) or math.isinf(number):
        return 0.0
    return number


def _flag(value: bool) -> float:
    return 1.0 if value else 0.0


def _optional_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
