from __future__ import annotations

from io import StringIO

import numpy as np
import pandas as pd

from app.core.schemas import BacktestFund, MlTrainingLabelConfig, MlTrainingSampleExportRequest, MlTrainingSampleExportResponse
from app.features.nav_features import build_nav_frame
from app.ml.features import FEATURE_COLUMNS


BASE_COLUMNS = ["fundCode", "fundName", "fundType", "date", "horizonDays"]
AUDIT_COLUMNS = ["forwardReturn", "forwardMaxDrawdown"]


def export_training_samples(request: MlTrainingSampleExportRequest) -> MlTrainingSampleExportResponse:
    frames = [_samples_for_fund(fund, request.startDate, request.endDate, request.labelConfig) for fund in request.funds]
    frames = [frame for frame in frames if not frame.empty]
    if frames:
        output = pd.concat(frames, ignore_index=True)
        output = output.sort_values(["fundCode", "date"]).reset_index(drop=True)
    else:
        output = pd.DataFrame(columns=[*BASE_COLUMNS, *FEATURE_COLUMNS, *AUDIT_COLUMNS, "label"])

    csv_buffer = StringIO()
    output.to_csv(csv_buffer, index=False, lineterminator="\n")
    positive_count = int((output["label"] == 1).sum()) if "label" in output else 0
    negative_count = int((output["label"] == 0).sum()) if "label" in output else 0
    return MlTrainingSampleExportResponse(
        fileName=f"quantfund_ml_training_{request.startDate}_{request.endDate}.csv",
        rowCount=len(output),
        fundCount=len(request.funds),
        positiveCount=positive_count,
        negativeCount=negative_count,
        featureColumns=FEATURE_COLUMNS,
        labelConfig=request.labelConfig,
        csvContent=csv_buffer.getvalue(),
    )


def _samples_for_fund(
    fund: BacktestFund,
    start_date: str,
    end_date: str,
    label_config: MlTrainingLabelConfig,
) -> pd.DataFrame:
    frame = build_nav_frame(fund.navSeries)
    if len(frame) < max(30, label_config.horizonDays + 5):
        return pd.DataFrame()

    nav = frame["nav"].astype(float)
    daily = frame["dailyGrowthRate"].copy()
    if daily.isna().all():
        daily = nav.pct_change() * 100
    daily = daily.fillna(0)

    features = pd.DataFrame(index=frame.index)
    features["return5d"] = _past_return(nav, 5)
    features["return20d"] = _past_return(nav, 20)
    features["return60d"] = _past_return(nav, 60)
    ma20 = nav.rolling(20, min_periods=2).mean()
    features["ma20Deviation"] = _pct(nav, ma20)
    features["trendSlope20d"] = _rolling_slope(nav, 20)
    returns = nav.pct_change()
    features["volatility20d"] = returns.rolling(20, min_periods=2).std(ddof=1).fillna(0) * np.sqrt(252) * 100
    features["maxDrawdown20d"] = _rolling_max_drawdown(nav, 20)
    features["maxDrawdown60d"] = _rolling_max_drawdown(nav, 60)
    features["lossDayRatio20d"] = (returns.lt(0).rolling(20, min_periods=1).mean() * 100).fillna(0)
    features["consecutiveUpDays"] = _consecutive_streak(daily, positive=True)
    features["consecutiveDownDays"] = _consecutive_streak(daily, positive=False)
    features["positionToSingleLimit"] = 0.0
    features["profitBuffer"] = np.maximum(features["return20d"], 0)
    features["lossPressure"] = np.maximum(-features["return20d"], 0)
    features["themeRate"] = 0.0
    features["estimateGrowthRate"] = 0.0
    features["navSampleSize"] = np.arange(1, len(frame) + 1)
    features["holdingProfitRate"] = features["return20d"]
    features["holdingDays"] = features["navSampleSize"]

    forward_return = _forward_return(nav, label_config.horizonDays)
    forward_drawdown = _forward_max_drawdown(nav, label_config.horizonDays)
    labels = (
        (forward_return >= label_config.minForwardReturn)
        & (forward_drawdown >= label_config.maxForwardDrawdown)
    ).astype(int)

    output = pd.DataFrame(
        {
            "fundCode": fund.fundCode,
            "fundName": fund.fundName,
            "fundType": fund.fundType,
            "date": frame["date"].dt.strftime("%Y-%m-%d"),
            "horizonDays": label_config.horizonDays,
        }
    )
    for column in FEATURE_COLUMNS:
        output[column] = features[column].replace([np.inf, -np.inf], np.nan).fillna(0).round(6)
    output["forwardReturn"] = forward_return.round(6)
    output["forwardMaxDrawdown"] = forward_drawdown.round(6)
    output["label"] = labels
    valid_until = len(output) - max(0, label_config.horizonDays)
    output = output.iloc[:valid_until]
    output = output[(output["date"] >= start_date) & (output["date"] <= end_date)]
    min_history = max(20, int(getattr(label_config, "minNavSamples", 20) or 20))
    output = output[output["navSampleSize"] >= min_history]
    return output.reset_index(drop=True)


def _pct(current: pd.Series, base: pd.Series) -> pd.Series:
    return (current / base.replace(0, np.nan) - 1) * 100


def _past_return(nav: pd.Series, window: int) -> pd.Series:
    return _pct(nav, nav.shift(window))


def _forward_return(nav: pd.Series, horizon: int) -> pd.Series:
    return _pct(nav.shift(-horizon), nav).fillna(0)


def _rolling_slope(nav: pd.Series, window: int) -> pd.Series:
    def slope(values: np.ndarray) -> float:
        mean = float(np.mean(values))
        if len(values) < 2 or mean == 0:
            return 0.0
        x = np.arange(len(values), dtype=float)
        return float(np.polyfit(x, values, 1)[0]) / mean * 100

    return nav.rolling(window, min_periods=2).apply(slope, raw=True).fillna(0)


def _rolling_max_drawdown(nav: pd.Series, window: int) -> pd.Series:
    def drawdown(values: np.ndarray) -> float:
        series = pd.Series(values)
        rolling_max = series.cummax()
        return float((series / rolling_max - 1).min() * 100)

    return nav.rolling(window, min_periods=2).apply(drawdown, raw=True).fillna(0)


def _forward_max_drawdown(nav: pd.Series, horizon: int) -> pd.Series:
    values = nav.to_numpy(dtype=float)
    result = np.zeros(len(values), dtype=float)
    for index in range(len(values) - horizon):
        window = values[index : index + horizon + 1]
        peak = np.maximum.accumulate(window)
        result[index] = float(np.min(window / peak - 1) * 100)
    return pd.Series(result, index=nav.index)


def _consecutive_streak(values: pd.Series, positive: bool) -> pd.Series:
    mask = values.gt(0) if positive else values.lt(0)
    groups = (~mask).cumsum()
    streak = mask.groupby(groups).cumcount() + 1
    return streak.where(mask, 0)
