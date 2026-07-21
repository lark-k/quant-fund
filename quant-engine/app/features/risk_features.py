from __future__ import annotations

import math

import numpy as np
import pandas as pd

from app.core.schemas import NavPoint
from app.features.nav_features import build_nav_frame


def calculate_risk_features(nav_series: list[NavPoint]) -> dict[str, float]:
    frame = build_nav_frame(nav_series)
    if frame.empty:
        return _empty_risk_features()

    nav = frame["nav"].astype(float)
    returns = nav.pct_change().dropna()
    features: dict[str, float] = {}
    for window in (5, 20, 60):
        features[f"volatility{window}d"] = round(_annualized_volatility(returns.tail(window)), 4)
    for window in (20, 60, 120):
        features[f"currentDrawdown{window}d"] = round(_current_drawdown(nav.tail(window)), 4)
        features[f"maxDrawdown{window}d"] = round(_max_drawdown(nav.tail(window)), 4)
        features[f"maxDrawdownWithin{window}d"] = features[f"maxDrawdown{window}d"]

    last20 = returns.tail(20)
    downside = last20[last20 < 0]
    features["downsideVolatility20d"] = round(_annualized_volatility(downside), 4)
    features["lossDayRatio20d"] = round(float((last20 < 0).mean() * 100), 4) if len(last20) else 0.0
    return features


def _annualized_volatility(returns: pd.Series) -> float:
    if len(returns) < 2:
        return 0.0
    return float(returns.std(ddof=1) * math.sqrt(252) * 100)


def _max_drawdown(nav: pd.Series) -> float:
    if nav.empty:
        return 0.0
    rolling_max = nav.cummax()
    drawdown = nav / rolling_max - 1
    return float(drawdown.min() * 100)


def _current_drawdown(nav: pd.Series) -> float:
    if nav.empty:
        return 0.0
    peak = float(nav.max())
    return float((float(nav.iloc[-1]) / peak - 1) * 100) if peak > 0 else 0.0


def rolling_current_drawdown(nav: pd.Series, window: int) -> pd.Series:
    rolling_peak = nav.rolling(window, min_periods=2).max()
    return ((nav / rolling_peak - 1) * 100).replace([np.inf, -np.inf], 0).fillna(0)


def rolling_max_drawdown(nav: pd.Series, window: int) -> pd.Series:
    def drawdown(values: np.ndarray) -> float:
        return _max_drawdown(pd.Series(values, dtype=float))

    return nav.rolling(window, min_periods=2).apply(drawdown, raw=True).fillna(0)


def _empty_risk_features() -> dict[str, float]:
    return {
        "volatility5d": 0.0,
        "volatility20d": 0.0,
        "volatility60d": 0.0,
        "maxDrawdown20d": 0.0,
        "maxDrawdown60d": 0.0,
        "maxDrawdown120d": 0.0,
        "maxDrawdownWithin20d": 0.0,
        "maxDrawdownWithin60d": 0.0,
        "maxDrawdownWithin120d": 0.0,
        "currentDrawdown20d": 0.0,
        "currentDrawdown60d": 0.0,
        "currentDrawdown120d": 0.0,
        "downsideVolatility20d": 0.0,
        "lossDayRatio20d": 0.0,
    }
