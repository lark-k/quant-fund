from __future__ import annotations

import math
from typing import Any


FEATURE_COLUMNS: list[str] = [
    "return5d",
    "return20d",
    "return60d",
    "ma20Deviation",
    "trendSlope20d",
    "volatility20d",
    "maxDrawdown20d",
    "maxDrawdown60d",
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
]


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
