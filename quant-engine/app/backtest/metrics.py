from __future__ import annotations

import math

import numpy as np
import pandas as pd


def pct(current: float, base: float) -> float:
    if base == 0:
        return 0.0
    return (current / base - 1) * 100


def annual_return(total_return_rate: float, start: pd.Timestamp, end: pd.Timestamp) -> float:
    days = max((end - start).days, 1)
    total = total_return_rate / 100
    return ((1 + total) ** (365 / days) - 1) * 100


def max_drawdown(values: pd.Series) -> float:
    if values.empty:
        return 0.0
    drawdown = values / values.cummax() - 1
    return float(drawdown.min() * 100)


def sharpe_ratio(values: pd.Series) -> float | None:
    returns = values.pct_change().dropna()
    if len(returns) < 2:
        return None
    std = float(returns.std(ddof=1))
    if std == 0:
        return None
    return float(returns.mean() / std * math.sqrt(252))


def calmar_ratio(annual_return_rate: float, max_drawdown_rate: float) -> float | None:
    drawdown = abs(max_drawdown_rate)
    if drawdown == 0:
        return None
    return annual_return_rate / drawdown


def percentile(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    return float(np.percentile(np.array(values, dtype=float), q))

