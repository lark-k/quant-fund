from __future__ import annotations

import numpy as np
import pandas as pd

from app.core.schemas import NavPoint


WINDOWS = (1, 3, 5, 10, 20, 60, 120)
MA_WINDOWS = (5, 10, 20, 60, 120)


def build_nav_frame(nav_series: list[NavPoint]) -> pd.DataFrame:
    if not nav_series:
        return pd.DataFrame(columns=["date", "nav", "dailyGrowthRate"])
    rows = [point.model_dump() for point in nav_series]
    frame = pd.DataFrame(rows)
    frame["date"] = pd.to_datetime(frame["date"], errors="coerce")
    frame["nav"] = pd.to_numeric(frame["nav"], errors="coerce")
    for column in (
        "dailyGrowthRate",
        "indexReturnRate",
        "marketSh000001ReturnRate",
        "marketSz399001ReturnRate",
        "marketCyb399006ReturnRate",
        "marketHs300ReturnRate",
        "marketZz500ReturnRate",
    ):
        if column not in frame.columns:
            frame[column] = np.nan
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    frame = frame.dropna(subset=["date", "nav"]).sort_values("date")
    return frame.reset_index(drop=True)


def calculate_nav_features(nav_series: list[NavPoint]) -> dict[str, float | int]:
    frame = build_nav_frame(nav_series)
    if frame.empty:
        return _empty_nav_features()

    nav = frame["nav"].astype(float)
    latest = float(nav.iloc[-1])
    features: dict[str, float | int] = {"latestNav": round(latest, 6), "navSampleSize": int(len(nav))}

    for window in WINDOWS:
        features[f"return{window}d"] = round(_window_return(nav, window), 4)
    for window in MA_WINDOWS:
        ma = float(nav.tail(window).mean())
        features[f"ma{window}"] = round(ma, 6)
        features[f"ma{window}Deviation"] = round(_pct(latest, ma), 4)

    features["trendSlope20d"] = round(_trend_slope(nav.tail(20)), 4)
    features["trendSlope60d"] = round(_trend_slope(nav.tail(60)), 4)

    daily = frame["dailyGrowthRate"].copy()
    if daily.isna().all():
        daily = nav.pct_change() * 100
    daily = daily.fillna(0)
    features["consecutiveUpDays"] = _consecutive_days(daily, positive=True)
    features["consecutiveDownDays"] = _consecutive_days(daily, positive=False)
    features.update(_market_features(frame, features))
    return features


def _window_return(nav: pd.Series, window: int) -> float:
    if len(nav) <= window:
        return 0.0
    previous = float(nav.iloc[-window - 1])
    latest = float(nav.iloc[-1])
    return _pct(latest, previous)


def _pct(current: float, base: float) -> float:
    if base == 0:
        return 0.0
    return (current / base - 1) * 100


def _trend_slope(nav: pd.Series) -> float:
    if len(nav) < 2:
        return 0.0
    values = nav.to_numpy(dtype=float)
    mean = float(np.mean(values))
    if mean == 0:
        return 0.0
    x = np.arange(len(values), dtype=float)
    slope = float(np.polyfit(x, values, 1)[0])
    return slope / mean * 100


def _consecutive_days(daily_growth: pd.Series, positive: bool) -> int:
    count = 0
    for value in reversed(daily_growth.to_list()):
        if positive and value > 0:
            count += 1
        elif not positive and value < 0:
            count += 1
        else:
            break
    return count


def _empty_nav_features() -> dict[str, float | int]:
    features: dict[str, float | int] = {"latestNav": 0.0, "navSampleSize": 0}
    for window in WINDOWS:
        features[f"return{window}d"] = 0.0
    for window in MA_WINDOWS:
        features[f"ma{window}"] = 0.0
        features[f"ma{window}Deviation"] = 0.0
    features["trendSlope20d"] = 0.0
    features["trendSlope60d"] = 0.0
    features["consecutiveUpDays"] = 0
    features["consecutiveDownDays"] = 0
    for prefix in (
        "trackingIndex",
        "trackingExcess",
        "marketSh000001",
        "marketSz399001",
        "marketCyb399006",
        "marketHs300",
        "marketZz500",
    ):
        for window in (20, 60, 120):
            features[f"{prefix}Return{window}d"] = 0.0
    return features


def _market_features(frame: pd.DataFrame, nav_features: dict[str, float | int]) -> dict[str, float]:
    features: dict[str, float] = {}
    market_sources = {
        "trackingIndex": "indexReturnRate",
        "marketSh000001": "marketSh000001ReturnRate",
        "marketSz399001": "marketSz399001ReturnRate",
        "marketCyb399006": "marketCyb399006ReturnRate",
        "marketHs300": "marketHs300ReturnRate",
        "marketZz500": "marketZz500ReturnRate",
    }
    for prefix, column in market_sources.items():
        series = frame.get(column)
        returns = _cumulative_return_windows(series)
        for window, value in returns.items():
            features[f"{prefix}Return{window}d"] = round(value, 4)
            if prefix == "trackingIndex":
                fund_return = float(nav_features.get(f"return{window}d", 0.0) or 0.0)
                features[f"trackingExcessReturn{window}d"] = round(fund_return - value, 4)
    return features


def _cumulative_return_windows(series: pd.Series | None) -> dict[int, float]:
    result: dict[int, float] = {}
    if series is None:
        return {window: 0.0 for window in (20, 60, 120)}
    cumulative = pd.to_numeric(series, errors="coerce")
    relative = (1 + cumulative / 100).replace([np.inf, -np.inf], np.nan)
    for window in (20, 60, 120):
        if len(relative) <= window:
            result[window] = 0.0
            continue
        latest = float(relative.iloc[-1])
        previous = float(relative.iloc[-window - 1])
        if not np.isfinite(latest) or not np.isfinite(previous) or previous == 0:
            result[window] = 0.0
        else:
            result[window] = (latest / previous - 1) * 100
    return result
