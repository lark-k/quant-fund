from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from itertools import product
from uuid import uuid4

import numpy as np
import pandas as pd

from app.backtest.metrics import annual_return, calmar_ratio, max_drawdown, pct, percentile, sharpe_ratio
from app.core.config import Settings
from app.core.schemas import (
    BacktestBatchRunRequest,
    BacktestBatchRunResponse,
    BacktestEquityPoint,
    BacktestGridResult,
    BacktestGridRunRequest,
    BacktestGridRunResponse,
    BacktestResult,
    BacktestRunRequest,
    BacktestStrategyParams,
    BacktestSummary,
    BacktestTrade,
)
from app.features.nav_features import build_nav_frame
from app.features.risk_features import _annualized_volatility
from app.ml.predict import MlSignalPredictor
from app.strategies.fund_profile import (
    FundProfile,
    is_active_fund_type,
    is_qdii_or_overseas_type,
    normalize_fund_type,
    resolve_fund_profile,
)
from app.strategies.scoring import clamp


WEAK_TREND_COOLDOWN_DAYS = 45
WEAK_TREND_CONFIRM_DAYS = 4
POSITION_BENCHMARK_TOLERANCE = 0.5
MIN_USABLE_SHORT_HISTORY_SAMPLES = 90
EARLY_TREND_BOOTSTRAP_SAMPLES = 150
TARGET_ENTRY_POSITION_RATE = 45.0
BENCHMARK_ENTRY_POSITION_RATE = 40.0
MAX_AVG_ANNUAL_TRADE_COUNT = 6.0


def run_single_backtest(request: BacktestRunRequest, settings: Settings, enable_ml: bool = False) -> BacktestResult:
    params = request.strategyParams
    frame = _prepare_frame(request.navSeries, request.startDate, request.endDate, params.warmupDays)
    if len(frame) < max(2, params.minNavSamples):
        return _empty_result(request, settings, len(frame), "历史净值样本不足，无法进行有效回测")

    frame = _attach_signal_columns(frame, params, settings, enable_ml)
    start = pd.to_datetime(request.startDate)
    trade_frame = frame[frame["date"] >= start].copy().reset_index(drop=True)
    if len(trade_frame) < 2:
        return _empty_result(request, settings, len(trade_frame), "回测区间内历史净值样本不足")

    fund_profile = resolve_fund_profile(request.fundCode, request.fundName, request.fundType)
    fund_type = fund_profile.effectiveType
    data_coverage = _data_coverage_rate(request.startDate, request.endDate, trade_frame)
    cash = float(request.initialCash)
    share = 0.0
    avg_cost = 0.0
    trade_amount_sum = 0.0
    sell_count = 0
    win_sell_count = 0
    weak_trend_cooldown = 0
    weak_trend_watch_days = 0
    weak_trend_defense_handled = False
    weak_trend_defense_sell_count = 0
    extreme_risk_sell_count = 0
    weak_recovery_required = False
    trades: list[BacktestTrade] = []
    equity_curve: list[BacktestEquityPoint] = []

    for row in trade_frame.itertuples(index=False):
        nav = float(row.nav)
        position_value = share * nav
        total_asset = cash + position_value
        position_rate = pct(position_value, total_asset) + 100 if total_asset > 0 and position_value > 0 else 0.0
        action = "HOLD"
        strong_trend_lock = _strong_trend_lock(row, params, fund_type)
        strong_reentry_buy = _strong_reentry_buy(row, params, fund_type)
        trend_start_buy = _trend_start_buy(row, params, fund_type)
        midterm_trend_buy = _midterm_trend_buy(row, fund_type)
        recoverable_pullback_buy = _recoverable_pullback_buy(row)
        benchmark_alignment_buy = _benchmark_alignment_buy(row, params, position_rate, fund_type)
        core_trend_allocation_buy = _core_trend_allocation_buy(row, params, position_rate, fund_type)
        early_trend_bootstrap_buy = _early_trend_bootstrap_buy(row, params, position_rate, fund_type)
        trend_repair_reentry_buy = (
            (weak_recovery_required or weak_trend_defense_handled or weak_trend_cooldown > 0)
            and _trend_repair_reentry_buy(row, params, position_rate, fund_type)
        )
        recoverable_reentry_buy = recoverable_pullback_buy and weak_trend_defense_handled
        underposition_recoverable_buy = _underposition_recoverable_buy(recoverable_pullback_buy, position_rate, params)
        weak_trend_defense = _weak_trend_defense(row, fund_type)
        if weak_trend_defense:
            weak_trend_cooldown = max(weak_trend_cooldown, WEAK_TREND_COOLDOWN_DAYS)
            weak_recovery_required = True
            weak_trend_watch_days = weak_trend_watch_days + 1
        else:
            weak_trend_cooldown = max(weak_trend_cooldown - 1, 0)
            weak_trend_watch_days = 0
        if weak_recovery_required and _weak_trend_recovered(row, params, fund_type):
            weak_recovery_required = False
            weak_trend_defense_handled = False
            weak_trend_watch_days = 0
            weak_trend_cooldown = 0

        if (
            (
                _score_threshold_buy(row, params)
                or strong_trend_lock
                or trend_start_buy
                or midterm_trend_buy
                or recoverable_pullback_buy
                or benchmark_alignment_buy
                or core_trend_allocation_buy
                or early_trend_bootstrap_buy
                or trend_repair_reentry_buy
            )
            and row.sampleIndex >= params.minNavSamples
            and position_rate < params.maxSinglePositionRate
            and (
                weak_trend_cooldown == 0
                or strong_reentry_buy
                or recoverable_reentry_buy
                or underposition_recoverable_buy
                or benchmark_alignment_buy
                or core_trend_allocation_buy
                or early_trend_bootstrap_buy
                or trend_repair_reentry_buy
            )
            and (
                not weak_recovery_required
                or strong_trend_lock
                or strong_reentry_buy
                or recoverable_reentry_buy
                or underposition_recoverable_buy
                or benchmark_alignment_buy
                or core_trend_allocation_buy
                or early_trend_bootstrap_buy
                or trend_repair_reentry_buy
            )
        ):
            room = max(params.maxSinglePositionRate - position_rate, 0)
            buy_ratio = min(
                _buy_step_ratio(
                    params,
                    strong_trend_lock,
                    trend_start_buy,
                    midterm_trend_buy,
                    position_rate,
                    _score_threshold_buy(row, params),
                    recoverable_pullback_buy,
                    benchmark_alignment_buy,
                    core_trend_allocation_buy,
                    early_trend_bootstrap_buy,
                    trend_repair_reentry_buy,
                ),
                room,
            )
            amount = min(total_asset * buy_ratio / 100, cash)
            if amount >= 100:
                position_rate_before = position_rate
                fee = amount * request.feeRate
                net_amount = max(amount - fee, 0)
                bought_share = net_amount / nav
                previous_cost = avg_cost * share
                share += bought_share
                avg_cost = (previous_cost + net_amount) / share if share > 0 else 0
                cash -= amount
                trade_amount_sum += amount
                action = "BUY"
                extreme_risk_sell_count = 0
                reason = _buy_reason(
                    strong_trend_lock,
                    trend_start_buy,
                    midterm_trend_buy,
                    recoverable_pullback_buy,
                    benchmark_alignment_buy,
                    core_trend_allocation_buy,
                    early_trend_bootstrap_buy,
                    trend_repair_reentry_buy,
                )
                position_rate_after = _position_rate(share, nav, cash)
                trades.append(
                    _trade(
                        row,
                        action,
                        amount,
                        bought_share,
                        nav,
                        fee,
                        row.totalScore,
                        reason,
                        buy_ratio,
                        position_rate_before,
                        position_rate_after,
                    )
                )

        position_value = share * nav
        total_asset = cash + position_value
        position_return = pct(nav, avg_cost) if share > 0 and avg_cost > 0 else 0.0
        trend_hold = _should_hold_trend(row, params, fund_type)
        score_exit = row.totalScore <= params.sellThreshold
        if score_exit and trend_hold:
            score_exit = False
        profit_exit = position_return >= params.takeProfitRate
        if profit_exit and trend_hold:
            profit_exit = False
        risk_exit = position_return <= params.stopLossRate
        extreme_risk_exit = position_return <= -18 or abs(float(row.maxDrawdown60d)) >= 22
        weak_exit = (
            weak_trend_defense
            and weak_trend_watch_days >= WEAK_TREND_CONFIRM_DAYS
            and share > 0
            and not weak_trend_defense_handled
            and weak_trend_defense_sell_count == 0
        )
        if strong_trend_lock and not extreme_risk_exit:
            score_exit = False
            profit_exit = False
            risk_exit = False
            weak_exit = False
        should_sell = share > 0 and (score_exit or profit_exit or risk_exit or weak_exit or extreme_risk_exit)
        if should_sell:
            position_rate_before = _position_rate(share, nav, cash)
            sell_ratio = params.sellStepRatio
            if extreme_risk_exit:
                if weak_trend_defense_sell_count > 0 or extreme_risk_sell_count > 0 or position_rate_before <= 25:
                    sell_ratio = 100.0
                else:
                    sell_ratio = max(sell_ratio, 50.0)
            elif risk_exit:
                sell_ratio = max(sell_ratio, 35.0)
            elif profit_exit:
                sell_ratio = max(sell_ratio, 20.0)
            if weak_exit:
                sell_ratio = max(sell_ratio, 15.0)
            reason = _sell_reason(score_exit, profit_exit, risk_exit, weak_exit, extreme_risk_exit)
            sold_share = min(share, share * sell_ratio / 100)
            gross = sold_share * nav
            fee = gross * request.feeRate
            cash += gross - fee
            share -= sold_share
            trade_amount_sum += gross
            sell_count += 1
            if nav > avg_cost:
                win_sell_count += 1
            action = "SELL"
            position_rate_after = _position_rate(share, nav, cash)
            trades.append(
                _trade(
                    row,
                    action,
                    gross,
                    sold_share,
                    nav,
                    fee,
                    row.totalScore,
                    reason,
                    sell_ratio,
                    position_rate_before,
                    position_rate_after,
                )
            )
            defense_exit = score_exit or risk_exit or weak_exit or extreme_risk_exit
            if defense_exit:
                weak_trend_defense_handled = True
                weak_trend_cooldown = max(weak_trend_cooldown, WEAK_TREND_COOLDOWN_DAYS)
                weak_recovery_required = True
            if weak_exit:
                weak_trend_defense_sell_count += 1
            if extreme_risk_exit:
                extreme_risk_sell_count += 1
            if share <= 1e-8:
                share = 0.0
                avg_cost = 0.0

        position_value = share * nav
        total_asset = cash + position_value
        position_rate = position_value / total_asset * 100 if total_asset > 0 else 0.0
        equity_curve.append(
            BacktestEquityPoint(
                date=str(row.date.date()),
                totalAsset=round(total_asset, 4),
                cash=round(cash, 4),
                positionValue=round(position_value, 4),
                positionRate=round(position_rate, 4),
                nav=round(nav, 6),
                signalScore=round(float(row.totalScore), 2),
                action=action,
            )
        )

    equity = pd.Series([point.totalAsset for point in equity_curve], dtype=float)
    first_nav = float(trade_frame["nav"].iloc[0])
    last_nav = float(trade_frame["nav"].iloc[-1])
    benchmark_curve = request.initialCash * trade_frame["nav"] / first_nav
    position_benchmark_rate = max(0.0, min(float(params.maxSinglePositionRate), 100.0)) / 100
    position_benchmark_curve = request.initialCash * (
        (1 - position_benchmark_rate) + position_benchmark_rate * trade_frame["nav"] / first_nav
    )
    final_asset = float(equity.iloc[-1])
    total_return = pct(final_asset, request.initialCash)
    benchmark_return = pct(last_nav, first_nav)
    position_benchmark_final_asset = float(position_benchmark_curve.iloc[-1])
    position_benchmark_return = pct(position_benchmark_final_asset, request.initialCash)
    annual = annual_return(total_return, trade_frame["date"].iloc[0], trade_frame["date"].iloc[-1])
    drawdown = max_drawdown(equity)
    benchmark_drawdown = max_drawdown(benchmark_curve)
    position_benchmark_drawdown = max_drawdown(position_benchmark_curve)
    sharpe = sharpe_ratio(equity)
    calmar = calmar_ratio(annual, drawdown)
    trade_count = len(trades)
    win_rate = win_sell_count / sell_count * 100 if sell_count else 0.0
    turnover = trade_amount_sum / request.initialCash * 100 if request.initialCash > 0 else 0.0
    passed = _is_passed(annual, drawdown, position_benchmark_return, total_return, trade_count, trade_frame, data_coverage, fund_profile)
    ml_stats = _ml_stats(trade_frame)

    return BacktestResult(
        strategyName="QuantRuleEngine",
        modelVersion=_model_version(settings, enable_ml),
        fundCode=request.fundCode,
        fundName=request.fundName,
        fundType=request.fundType,
        startDate=str(trade_frame["date"].iloc[0].date()),
        endDate=str(trade_frame["date"].iloc[-1].date()),
        initialCash=round(request.initialCash, 4),
        finalAsset=round(final_asset, 4),
        benchmarkFinalAsset=round(float(benchmark_curve.iloc[-1]), 4),
        positionBenchmarkFinalAsset=round(position_benchmark_final_asset, 4),
        totalReturnRate=round(total_return, 4),
        annualReturnRate=round(annual, 4),
        benchmarkReturnRate=round(benchmark_return, 4),
        positionBenchmarkReturnRate=round(position_benchmark_return, 4),
        excessReturnRate=round(total_return - benchmark_return, 4),
        positionExcessReturnRate=round(total_return - position_benchmark_return, 4),
        maxDrawdownRate=round(drawdown, 4),
        benchmarkMaxDrawdownRate=round(benchmark_drawdown, 4),
        positionBenchmarkMaxDrawdownRate=round(position_benchmark_drawdown, 4),
        positionBenchmarkRate=round(position_benchmark_rate * 100, 4),
        winRate=round(win_rate, 4),
        sharpeRatio=None if sharpe is None else round(sharpe, 4),
        calmarRatio=None if calmar is None else round(calmar, 4),
        tradeCount=trade_count,
        turnoverRate=round(turnover, 4),
        navSampleSize=len(trade_frame),
        dataCoverageRate=round(data_coverage, 4),
        mlApplied=ml_stats["applied"],
        mlAppliedDays=ml_stats["applied_days"],
        mlScoreAdjustmentAvg=ml_stats["avg"],
        mlScoreAdjustmentAbsAvg=ml_stats["abs_avg"],
        mlScoreAdjustmentMaxAbs=ml_stats["max_abs"],
        mlExpectedReturnAvg=ml_stats["expected_return_avg"],
        mlExpectedReturnPositiveDays=ml_stats["expected_return_positive_days"],
        mlExpectedReturnPositiveDayRate=ml_stats["expected_return_positive_rate"],
        mlProbabilityAvg=ml_stats["probability_avg"],
        mlBullishDays=ml_stats["bullish_days"],
        mlBullishDayRate=ml_stats["bullish_day_rate"],
        mlSignalStrengthAvg=ml_stats["signal_strength_avg"],
        mlConfidenceScoreAvg=ml_stats["confidence_score_avg"],
        mlConfidenceMediumHighDayRate=ml_stats["confidence_medium_high_rate"],
        passed=passed,
        diagnosis=_diagnosis(
            passed,
            annual,
            total_return,
            position_benchmark_return,
            drawdown,
            position_benchmark_drawdown,
            trade_count,
            trade_frame,
            data_coverage,
            fund_profile,
        ),
        equityCurve=equity_curve,
        trades=trades,
    )


def run_batch_backtest(request: BacktestBatchRunRequest, settings: Settings) -> BacktestBatchRunResponse:
    max_workers = max(1, min(request.options.workers or settings.backtest_workers, settings.backtest_workers, 12))
    task_id = f"bt-{uuid4()}"
    results: list[BacktestResult] = []
    errors: list[dict] = []

    def run_fund(fund) -> BacktestResult:
        return run_single_backtest(
            BacktestRunRequest(
                fundCode=fund.fundCode,
                fundName=fund.fundName,
                fundType=fund.fundType,
                startDate=request.startDate,
                endDate=request.endDate,
                initialCash=request.initialCash,
                feeRate=request.feeRate,
                navSeries=fund.navSeries,
                strategyParams=request.strategyParams,
            ),
            settings,
            bool(request.options.enableMl),
        )

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_map = {executor.submit(run_fund, fund): fund for fund in request.funds}
        for future in as_completed(future_map):
            fund = future_map[future]
            try:
                result = future.result()
                if not request.options.saveEquityCurve:
                    result.equityCurve = []
                if not request.options.saveTrades:
                    result.trades = []
                results.append(result)
            except Exception as exc:  # pragma: no cover - defensive batch isolation
                errors.append({"fundCode": fund.fundCode, "message": str(exc)})

    results.sort(key=lambda item: item.annualReturnRate, reverse=True)
    return BacktestBatchRunResponse(
        taskId=task_id,
        taskName=request.taskName,
        status="COMPLETED" if not errors or results else "FAILED",
        strategyName=request.strategyName,
        modelVersion=_model_version(settings, bool(request.options.enableMl)),
        fundCount=len(request.funds),
        successCount=len(results),
        failedCount=len(errors),
        summary=summarize_results(results),
        results=results,
        errors=errors,
    )


def run_grid_backtest(request: BacktestGridRunRequest, settings: Settings) -> BacktestGridRunResponse:
    keys = list(request.paramGrid.keys())
    combinations = [dict(zip(keys, values)) for values in product(*(request.paramGrid[key] for key in keys))]
    if len(combinations) > settings.max_param_grid:
        raise ValueError(f"paramGrid combinations exceed max_param_grid={settings.max_param_grid}")

    ranked: list[BacktestGridResult] = []
    for params in combinations:
        strategy_params = request.strategyParams.model_copy(update=params)
        batch_request = BacktestBatchRunRequest(**request.model_dump(exclude={"paramGrid", "strategyParams"}), strategyParams=strategy_params)
        response = run_batch_backtest(batch_request, settings)
        score = response.summary.avgCalmarRatio
        ranked.append(
            BacktestGridResult(
                rank=0,
                annualReturnRate=response.summary.avgAnnualReturnRate,
                maxDrawdownRate=response.summary.avgMaxDrawdownRate,
                calmarRatio=score,
                outperformBuyHoldRate=response.summary.outperformBuyHoldRate,
                passRate=response.summary.passRate,
                params={key: float(value) for key, value in params.items()},
            )
        )
    ranked.sort(key=lambda item: (item.calmarRatio, item.outperformBuyHoldRate, item.annualReturnRate), reverse=True)
    top = [item.model_copy(update={"rank": index + 1}) for index, item in enumerate(ranked[:10])]
    return BacktestGridRunResponse(
        taskId=f"bt-grid-{uuid4()}",
        status="COMPLETED",
        combinationCount=len(combinations),
        bestParams=top[0].params if top else {},
        topResults=top,
    )


def summarize_results(results: list[BacktestResult]) -> BacktestSummary:
    if not results:
        return BacktestSummary(diagnosis="没有可用回测结果")
    annual = [item.annualReturnRate for item in results]
    drawdown = [item.maxDrawdownRate for item in results]
    sharpe = [item.sharpeRatio for item in results if item.sharpeRatio is not None]
    calmar = [item.calmarRatio for item in results if item.calmarRatio is not None]
    win_funds = [item for item in results if item.totalReturnRate > 0]
    outperform = [item for item in results if item.excessReturnRate > 0]
    outperform_position = [item for item in results if item.positionExcessReturnRate >= -POSITION_BENCHMARK_TOLERANCE]
    passed = [item for item in results if item.passed]
    ml_applied = [item for item in results if item.mlApplied]
    ml_abs_avg = [item.mlScoreAdjustmentAbsAvg for item in results if item.mlScoreAdjustmentAbsAvg > 0]
    ml_max_abs = [item.mlScoreAdjustmentMaxAbs for item in results if item.mlScoreAdjustmentMaxAbs > 0]
    ml_expected_return = [item.mlExpectedReturnAvg for item in results if item.mlApplied]
    ml_positive_return_days = [item.mlExpectedReturnPositiveDays for item in results if item.mlApplied]
    ml_positive_return_rate = [item.mlExpectedReturnPositiveDayRate for item in results if item.mlApplied]
    ml_probability = [item.mlProbabilityAvg for item in results if item.mlApplied]
    ml_bullish_days = [item.mlBullishDays for item in results if item.mlApplied]
    ml_bullish_rate = [item.mlBullishDayRate for item in results if item.mlApplied]
    ml_signal_strength = [item.mlSignalStrengthAvg for item in results if item.mlApplied]
    ml_confidence_score = [item.mlConfidenceScoreAvg for item in results if item.mlApplied]
    ml_confidence_rate = [item.mlConfidenceMediumHighDayRate for item in results if item.mlApplied]
    summary = BacktestSummary(
        avgAnnualReturnRate=round(float(np.mean(annual)), 4),
        medianAnnualReturnRate=round(float(np.median(annual)), 4),
        p10AnnualReturnRate=round(percentile(annual, 10), 4),
        avgMaxDrawdownRate=round(float(np.mean(drawdown)), 4),
        medianMaxDrawdownRate=round(float(np.median(drawdown)), 4),
        worstMaxDrawdownRate=round(float(np.min(drawdown)), 4),
        winFundRate=round(len(win_funds) / len(results) * 100, 4),
        outperformBuyHoldRate=round(len(outperform) / len(results) * 100, 4),
        outperformPositionBenchmarkRate=round(len(outperform_position) / len(results) * 100, 4),
        avgPositionExcessReturnRate=round(float(np.mean([item.positionExcessReturnRate for item in results])), 4),
        avgTradeCount=round(float(np.mean([item.tradeCount for item in results])), 4),
        avgAnnualTradeCount=round(float(np.mean([item.tradeCount / _result_years(item) for item in results])), 4),
        avgSharpeRatio=round(float(np.mean(sharpe)), 4) if sharpe else 0,
        avgCalmarRatio=round(float(np.mean(calmar)), 4) if calmar else 0,
        passRate=round(len(passed) / len(results) * 100, 4),
        mlAppliedFundRate=round(len(ml_applied) / len(results) * 100, 4),
        avgMlScoreAdjustmentAbs=round(float(np.mean(ml_abs_avg)), 4) if ml_abs_avg else 0,
        maxMlScoreAdjustmentAbs=round(float(np.max(ml_max_abs)), 4) if ml_max_abs else 0,
        avgMlExpectedReturn=round(float(np.mean(ml_expected_return)), 4) if ml_expected_return else 0,
        avgMlExpectedReturnPositiveDays=round(float(np.mean(ml_positive_return_days)), 1) if ml_positive_return_days else 0,
        avgMlExpectedReturnPositiveDayRate=round(float(np.mean(ml_positive_return_rate)), 4) if ml_positive_return_rate else 0,
        avgMlProbability=round(float(np.mean(ml_probability)), 4) if ml_probability else 0,
        avgMlBullishDays=round(float(np.mean(ml_bullish_days)), 1) if ml_bullish_days else 0,
        avgMlBullishDayRate=round(float(np.mean(ml_bullish_rate)), 4) if ml_bullish_rate else 0,
        avgMlSignalStrength=round(float(np.mean(ml_signal_strength)), 4) if ml_signal_strength else 0,
        avgMlConfidenceScore=round(float(np.mean(ml_confidence_score)), 4) if ml_confidence_score else 0,
        avgMlConfidenceMediumHighDayRate=round(float(np.mean(ml_confidence_rate)), 4) if ml_confidence_rate else 0,
    )
    summary.diagnosis = _summary_diagnosis(summary)
    return summary


def _prepare_frame(nav_series, start_date: str, end_date: str, warmup_days: int = 0) -> pd.DataFrame:
    frame = build_nav_frame(nav_series)
    if frame.empty:
        return frame
    start = pd.to_datetime(start_date)
    end = pd.to_datetime(end_date)
    warmup_start = start - pd.Timedelta(days=max(int(warmup_days or 0), 0))
    frame = frame[(frame["date"] >= warmup_start) & (frame["date"] <= end)].copy()
    frame = frame.drop_duplicates(subset=["date"]).sort_values("date").reset_index(drop=True)
    return frame


def _attach_signal_columns(
    frame: pd.DataFrame,
    params: BacktestStrategyParams,
    settings: Settings | None = None,
    enable_ml: bool = False,
) -> pd.DataFrame:
    nav = frame["nav"].astype(float)
    returns = nav.pct_change()
    out = frame.copy()
    out["sampleIndex"] = np.arange(len(out)) + 1
    out["return5d"] = (nav / nav.shift(5) - 1).fillna(0) * 100
    out["return20d"] = (nav / nav.shift(20) - 1).fillna(0) * 100
    out["return60d"] = (nav / nav.shift(60) - 1).fillna(0) * 100
    ma20 = nav.rolling(20, min_periods=2).mean()
    out["ma20Deviation"] = ((nav / ma20 - 1) * 100).replace([np.inf, -np.inf], 0).fillna(0)
    out["volatility20d"] = returns.rolling(20, min_periods=2).apply(_annualized_volatility, raw=False).fillna(0)
    out["trendSlope20d"] = nav.rolling(20, min_periods=2).apply(_trend_slope, raw=True).fillna(0)
    rolling_max_20 = nav.rolling(20, min_periods=2).max()
    out["maxDrawdown20d"] = ((nav / rolling_max_20 - 1) * 100).fillna(0)
    rolling_max_60 = nav.rolling(60, min_periods=2).max()
    out["maxDrawdown60d"] = ((nav / rolling_max_60 - 1) * 100).fillna(0)
    out["lossDayRatio20d"] = returns.rolling(20, min_periods=2).apply(lambda value: (value < 0).mean() * 100, raw=False).fillna(0)
    daily = returns.fillna(0) * 100
    out["consecutiveUpDays"] = _consecutive_streak(daily, positive=True)
    out["consecutiveDownDays"] = _consecutive_streak(daily, positive=False)
    out["positionToSingleLimit"] = 0.0
    out["profitBuffer"] = np.maximum(out["return20d"], 0)
    out["lossPressure"] = np.maximum(-out["return20d"], 0)
    out["themeRate"] = 0.0
    out["estimateGrowthRate"] = 0.0
    out["navSampleSize"] = out["sampleIndex"]
    out["holdingProfitRate"] = out["return20d"]
    out["holdingDays"] = out["sampleIndex"]
    out["trendScore"] = out.apply(_trend_score, axis=1)
    out["opportunityScore"] = out.apply(_opportunity_score, axis=1)
    out["riskScore"] = out.apply(_risk_score, axis=1)
    out["positionScore"] = 75.0
    out["momentumScore"] = 50.0
    out["totalScore"] = (
        out["trendScore"] * 0.35
        + out["opportunityScore"] * 0.25
        + out["riskScore"] * 0.15
        + out["positionScore"] * 0.15
        + out["momentumScore"] * 0.10
    ).clip(0, 100)
    if enable_ml and settings is not None:
        out = _apply_ml_score_adjustment(out, settings)
    out.loc[out.index < params.minNavSamples, "totalScore"] = 50.0
    return out


def _apply_ml_score_adjustment(out: pd.DataFrame, settings: Settings) -> pd.DataFrame:
    predictor = MlSignalPredictor(
        settings.ml_model_dir,
        settings.ml_model_id,
        enabled=True,
        score_adjustment_cap=settings.ml_score_adjustment_cap,
    )
    probabilities: list[float | None] = []
    adjustments: list[float] = []
    expected_returns: list[float | None] = []
    directions: list[str | None] = []
    signal_strengths: list[float | None] = []
    confidence_scores: list[float | None] = []
    confidence_levels: list[str | None] = []
    for row in out.to_dict(orient="records"):
        prediction = predictor.predict(row)
        probabilities.append(prediction.probability)
        adjustments.append(prediction.scoreAdjustment if prediction.available else 0.0)
        expected_returns.append(prediction.expectedReturn if prediction.returnModelAvailable else None)
        directions.append(prediction.direction if prediction.available else None)
        signal_strengths.append(prediction.signalStrength if prediction.available else None)
        confidence_scores.append(prediction.confidenceScore if prediction.available else None)
        confidence_levels.append(prediction.confidenceLevel if prediction.available else None)
    out["mlProbability"] = probabilities
    out["mlScoreAdjustment"] = adjustments
    out["mlExpectedReturn"] = expected_returns
    out["mlDirection"] = directions
    out["mlSignalStrength"] = signal_strengths
    out["mlConfidenceScore"] = confidence_scores
    out["mlConfidenceLevel"] = confidence_levels
    out["totalScore"] = (out["totalScore"] + out["mlScoreAdjustment"]).clip(0, 100)
    return out


def _ml_stats(frame: pd.DataFrame) -> dict[str, float | int | bool]:
    if "mlScoreAdjustment" not in frame.columns:
        return _empty_ml_stats()
    values = frame["mlScoreAdjustment"].astype(float).replace([np.inf, -np.inf], 0).fillna(0)
    abs_values = values.abs()
    applied_days = int((abs_values > 1e-9).sum())
    if applied_days == 0:
        return _empty_ml_stats()
    active_values = values[abs_values > 1e-9]
    active_abs = abs_values[abs_values > 1e-9]
    expected_return = frame.get("mlExpectedReturn")
    probability = frame.get("mlProbability")
    direction = frame.get("mlDirection")
    signal_strength = frame.get("mlSignalStrength")
    confidence_score = frame.get("mlConfidenceScore")
    confidence = frame.get("mlConfidenceLevel")
    expected_return_avg = 0.0
    expected_return_positive_days = 0
    expected_return_positive_rate = 0.0
    probability_avg = 0.0
    bullish_days = 0
    bullish_day_rate = 0.0
    signal_strength_avg = 0.0
    confidence_score_avg = 0.0
    confidence_medium_high_rate = 0.0
    if expected_return is not None:
        return_values = expected_return.astype(float).replace([np.inf, -np.inf], np.nan).dropna()
        if len(return_values) > 0:
            expected_return_positive_days = int((return_values > 0).sum())
            expected_return_avg = round(float(return_values.mean()), 4)
            expected_return_positive_rate = round(float((return_values > 0).mean() * 100), 4)
    if probability is not None:
        probability_values = probability.astype(float).replace([np.inf, -np.inf], np.nan).dropna()
        if len(probability_values) > 0:
            probability_avg = round(float(probability_values.mean() * 100), 4)
    if direction is not None:
        direction_values = direction.dropna()
        if len(direction_values) > 0:
            bullish_days = int((direction_values == "BULLISH").sum())
            bullish_day_rate = round(float((direction_values == "BULLISH").mean() * 100), 4)
    if signal_strength is not None:
        signal_strength_values = signal_strength.astype(float).replace([np.inf, -np.inf], np.nan).dropna()
        if len(signal_strength_values) > 0:
            signal_strength_avg = round(float(signal_strength_values.mean() * 100), 4)
    if confidence_score is not None:
        confidence_score_values = confidence_score.astype(float).replace([np.inf, -np.inf], np.nan).dropna()
        if len(confidence_score_values) > 0:
            confidence_score_avg = round(float(confidence_score_values.mean() * 100), 4)
    if confidence is not None:
        confidence_values = confidence.dropna()
        if len(confidence_values) > 0:
            confidence_medium_high_rate = round(float(confidence_values.isin(["MEDIUM", "HIGH"]).mean() * 100), 4)
    return {
        "applied": True,
        "applied_days": applied_days,
        "avg": round(float(active_values.mean()), 4),
        "abs_avg": round(float(active_abs.mean()), 4),
        "max_abs": round(float(active_abs.max()), 4),
        "expected_return_avg": expected_return_avg,
        "expected_return_positive_days": expected_return_positive_days,
        "expected_return_positive_rate": expected_return_positive_rate,
        "probability_avg": probability_avg,
        "bullish_days": bullish_days,
        "bullish_day_rate": bullish_day_rate,
        "signal_strength_avg": signal_strength_avg,
        "confidence_score_avg": confidence_score_avg,
        "confidence_medium_high_rate": confidence_medium_high_rate,
    }


def _empty_ml_stats() -> dict[str, float | int | bool]:
    return {
        "applied": False,
        "applied_days": 0,
        "avg": 0.0,
        "abs_avg": 0.0,
        "max_abs": 0.0,
        "expected_return_avg": 0.0,
        "expected_return_positive_days": 0,
        "expected_return_positive_rate": 0.0,
        "probability_avg": 0.0,
        "bullish_days": 0,
        "bullish_day_rate": 0.0,
        "signal_strength_avg": 0.0,
        "confidence_score_avg": 0.0,
        "confidence_medium_high_rate": 0.0,
    }


def _trend_slope(values: np.ndarray) -> float:
    mean = float(np.mean(values))
    if len(values) < 2 or mean == 0:
        return 0.0
    x = np.arange(len(values), dtype=float)
    return float(np.polyfit(x, values, 1)[0]) / mean * 100


def _consecutive_streak(values: pd.Series, positive: bool) -> pd.Series:
    mask = values.gt(0) if positive else values.lt(0)
    groups = (~mask).cumsum()
    streak = mask.groupby(groups).cumcount() + 1
    return streak.where(mask, 0)


def _model_version(settings: Settings, enable_ml: bool) -> str:
    if not enable_ml:
        return settings.rule_model_version
    predictor = MlSignalPredictor(
        settings.ml_model_dir,
        settings.ml_model_id,
        enabled=True,
        score_adjustment_cap=settings.ml_score_adjustment_cap,
    )
    if predictor.metadata is None:
        return f"{settings.rule_model_version}+ml-unavailable"
    return f"{settings.rule_model_version}+{predictor.metadata.modelVersion}"


def _should_hold_trend(row, params: BacktestStrategyParams, fund_type: str | None = None) -> bool:
    return _strong_trend_lock(row, params, fund_type) or _midterm_trend_pullback(row, fund_type) or _orderly_trend_hold(row, params, fund_type) or (
        float(row.return20d) > params.trendHoldReturn20d
        and float(row.ma20Deviation) >= params.trendHoldMa20Deviation
    )


def _buy_step_ratio(
    params: BacktestStrategyParams,
    strong_trend_lock: bool,
    trend_start_buy: bool = False,
    midterm_trend_buy: bool = False,
    position_rate: float = 0.0,
    score_threshold_buy: bool = False,
    recoverable_pullback_buy: bool = False,
    benchmark_alignment_buy: bool = False,
    core_trend_allocation_buy: bool = False,
    early_trend_bootstrap_buy: bool = False,
    trend_repair_reentry_buy: bool = False,
) -> float:
    target_position = float(params.maxSinglePositionRate)
    if position_rate <= 1.0 and (strong_trend_lock or trend_start_buy):
        return params.maxSinglePositionRate
    if position_rate <= 1.0 and midterm_trend_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 35.0))
    if position_rate <= 1.0 and core_trend_allocation_buy:
        return min(target_position, max(params.buyStepRatio, min(target_position, TARGET_ENTRY_POSITION_RATE)))
    if position_rate <= 1.0 and score_threshold_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 30.0))
    if position_rate <= 1.0 and benchmark_alignment_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, BENCHMARK_ENTRY_POSITION_RATE))
    if position_rate <= 1.0 and recoverable_pullback_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 30.0))
    if position_rate <= 1.0 and early_trend_bootstrap_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 35.0))
    if position_rate <= 1.0 and trend_repair_reentry_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 35.0))
    if strong_trend_lock:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 35.0))
    if trend_start_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 30.0))
    if core_trend_allocation_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 35.0))
    if midterm_trend_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 25.0))
    if benchmark_alignment_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 25.0))
    if recoverable_pullback_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 25.0))
    if early_trend_bootstrap_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 25.0))
    if trend_repair_reentry_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 30.0))
    return params.buyStepRatio


def _buy_reason(
    strong_trend_lock: bool,
    trend_start_buy: bool,
    midterm_trend_buy: bool = False,
    recoverable_pullback_buy: bool = False,
    benchmark_alignment_buy: bool = False,
    core_trend_allocation_buy: bool = False,
    early_trend_bootstrap_buy: bool = False,
    trend_repair_reentry_buy: bool = False,
) -> str:
    if strong_trend_lock:
        return "strong_trend_buy"
    if trend_repair_reentry_buy:
        return "trend_repair_reentry_buy"
    if trend_start_buy:
        return "trend_start_buy"
    if core_trend_allocation_buy:
        return "core_trend_allocation_buy"
    if midterm_trend_buy:
        return "midterm_trend_buy"
    if benchmark_alignment_buy:
        return "benchmark_alignment_buy"
    if recoverable_pullback_buy:
        return "recoverable_pullback_buy"
    if early_trend_bootstrap_buy:
        return "early_trend_bootstrap_buy"
    return "score_above_buy_threshold"


def _score_threshold_buy(row, params: BacktestStrategyParams) -> bool:
    return (
        float(row.totalScore) >= params.buyThreshold
        and float(row.trendScore) >= 50
        and float(row.opportunityScore) >= 48
        and float(row.riskScore) >= 25
        and not (float(row.return5d) <= -1 and float(row.return20d) <= 0)
    )


def _normalize_fund_type(fund_type: str | None) -> str:
    return normalize_fund_type(fund_type)


def _is_active_equity_type(fund_type: str | None) -> bool:
    return is_active_fund_type(fund_type)


def _is_qdii_or_overseas_type(fund_type: str | None) -> bool:
    return is_qdii_or_overseas_type(fund_type)


def _strong_trend_lock(row, params: BacktestStrategyParams, fund_type: str | None = None) -> bool:
    drawdown_limit = 21 if _is_active_equity_type(fund_type) else 18 if _is_qdii_or_overseas_type(fund_type) else 20
    return (
        float(row.return20d) >= params.trendHoldReturn20d
        and float(row.return60d) >= 5
        and float(row.ma20Deviation) >= params.trendHoldMa20Deviation
        and abs(float(row.maxDrawdown60d)) < drawdown_limit
    )


def _strong_reentry_buy(row, params: BacktestStrategyParams, fund_type: str | None = None) -> bool:
    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return60_min = 8.0 if active else 11.0 if qdii else 10.0
    drawdown_limit = 16.0 if active else 12.5 if qdii else 14.0
    trend_score_min = 56.0 if active else 62.0 if qdii else 60.0
    risk_score_min = 28.0 if active else 32.0 if qdii else 30.0
    return (
        float(row.return20d) >= max(params.trendHoldReturn20d, 2.0)
        and float(row.return60d) >= return60_min
        and float(row.ma20Deviation) >= -4
        and abs(float(row.maxDrawdown60d)) < drawdown_limit
        and float(row.trendScore) >= trend_score_min
        and float(row.riskScore) >= risk_score_min
        and float(row.lossDayRatio20d) <= 58
    )


def _trend_start_buy(row, params: BacktestStrategyParams, fund_type: str | None = None) -> bool:
    qdii = _is_qdii_or_overseas_type(fund_type)
    loss_day_limit = 62.0 if qdii else 68.0 if _is_active_equity_type(fund_type) else 65.0
    risk_score_min = 32.0 if qdii else 28.0 if _is_active_equity_type(fund_type) else 30.0
    return (
        float(row.return20d) >= params.trendHoldReturn20d
        and float(row.return5d) >= 0
        and float(row.ma20Deviation) >= params.trendHoldMa20Deviation
        and abs(float(row.maxDrawdown60d)) < 20
        and float(row.riskScore) >= risk_score_min
        and float(row.lossDayRatio20d) <= loss_day_limit
    )


def _midterm_trend_buy(row, fund_type: str | None = None) -> bool:
    qdii = _is_qdii_or_overseas_type(fund_type)
    risk_score_min = 30.0 if qdii else 24.0 if _is_active_equity_type(fund_type) else 25.0
    loss_day_limit = 66.0 if qdii else 72.0 if _is_active_equity_type(fund_type) else 70.0
    return (
        _midterm_trend_pullback(row, fund_type)
        and float(row.return5d) >= -1
        and float(row.return20d) >= -5
        and float(row.ma20Deviation) >= -10
        and float(row.riskScore) >= risk_score_min
        and float(row.lossDayRatio20d) <= loss_day_limit
    )


def _recoverable_pullback_buy(row) -> bool:
    return (
        _recoverable_pullback(row)
        and float(row.return5d) >= 0
        and float(row.return20d) > -12
        and float(row.ma20Deviation) >= -12
        and float(row.trendScore) >= 45
        and float(row.lossDayRatio20d) <= 70
    )


def _underposition_recoverable_buy(recoverable_pullback_buy: bool, position_rate: float, params: BacktestStrategyParams) -> bool:
    underposition_limit = min(float(params.maxSinglePositionRate) * 0.5, 25.0)
    return recoverable_pullback_buy and position_rate <= underposition_limit


def _benchmark_alignment_buy(row, params: BacktestStrategyParams, position_rate: float, fund_type: str | None = None) -> bool:
    target_position = float(params.maxSinglePositionRate)
    target_near_full = min(target_position * 0.9, target_position - 1.0)
    if position_rate >= target_near_full:
        return False
    if _weak_trend_defense(row, fund_type):
        return False
    qdii = _is_qdii_or_overseas_type(fund_type)
    risk_score_min = 30.0 if qdii else 25.0
    drawdown_limit = 16.0 if qdii else 18.0
    loss_day_limit = 64.0 if qdii else 68.0
    return (
        float(row.totalScore) >= max(float(params.buyThreshold) - 3.0, 48.0)
        and float(row.trendScore) >= 45
        and float(row.opportunityScore) >= 45
        and float(row.riskScore) >= risk_score_min
        and float(row.return20d) >= -1
        and float(row.return60d) >= 0
        and float(row.ma20Deviation) >= float(params.trendHoldMa20Deviation) - 2.0
        and abs(float(row.maxDrawdown60d)) < drawdown_limit
        and float(row.lossDayRatio20d) <= loss_day_limit
        and not (float(row.return5d) <= -2 and float(row.return20d) <= 0)
    )


def _core_trend_allocation_buy(row, params: BacktestStrategyParams, position_rate: float, fund_type: str | None = None) -> bool:
    target_position = float(params.maxSinglePositionRate)
    target_near_full = min(target_position * 0.98, target_position - 0.1)
    if position_rate >= target_near_full:
        return False
    if _weak_trend_defense(row, fund_type):
        return False

    return5 = float(row.return5d)
    return20 = float(row.return20d)
    return60 = float(row.return60d)
    ma20_deviation = float(row.ma20Deviation)
    drawdown60 = abs(float(row.maxDrawdown60d))
    loss_day_ratio20 = float(row.lossDayRatio20d)
    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return60_confirm = 7.0 if active else 9.0 if qdii else 8.0
    risk_score_min = 24.0 if active else 30.0 if qdii else 25.0
    drawdown_limit = 17.0 if active else 14.0 if qdii else 16.0
    loss_day_limit = 64.0 if active else 58.0 if qdii else 62.0
    trend_confirmed = return60 >= return60_confirm or (
        return20 >= max(float(params.trendHoldReturn20d), 1.0)
        and return60 >= 4.0
    )

    return (
        trend_confirmed
        and float(row.totalScore) >= max(float(params.buyThreshold) - 6.0, 46.0)
        and float(row.trendScore) >= 42
        and float(row.opportunityScore) >= 40
        and float(row.riskScore) >= risk_score_min
        and return20 >= -0.5
        and ma20_deviation >= float(params.trendHoldMa20Deviation) - 1.0
        and drawdown60 < drawdown_limit
        and loss_day_ratio20 <= loss_day_limit
        and not (return5 <= -2.5 and return20 <= 0)
    )


def _early_trend_bootstrap_buy(row, params: BacktestStrategyParams, position_rate: float, fund_type: str | None = None) -> bool:
    sample_index = int(row.sampleIndex)
    if sample_index > EARLY_TREND_BOOTSTRAP_SAMPLES:
        return False
    target_position = float(params.maxSinglePositionRate)
    if position_rate >= min(target_position * 0.9, target_position - 1.0):
        return False
    if _weak_trend_defense(row, fund_type):
        return False

    return5 = float(row.return5d)
    return20 = float(row.return20d)
    ma20_deviation = float(row.ma20Deviation)
    drawdown60 = abs(float(row.maxDrawdown60d))
    loss_day_ratio20 = float(row.lossDayRatio20d)
    trend_slope20 = float(getattr(row, "trendSlope20d", 0))
    early_momentum_confirmed = return20 >= max(float(params.trendHoldReturn20d) * 0.6, 0.8) or (
        return20 >= 0.5 and trend_slope20 >= 0.12
    )

    qdii = _is_qdii_or_overseas_type(fund_type)
    risk_score_min = 32.0 if qdii else 28.0
    drawdown_limit = 13.0 if qdii else 15.0
    return (
        sample_index >= int(params.minNavSamples)
        and early_momentum_confirmed
        and float(row.totalScore) >= max(float(params.buyThreshold) - 5.0, 46.0)
        and float(row.trendScore) >= 42
        and float(row.riskScore) >= risk_score_min
        and return5 >= -1.0
        and ma20_deviation >= float(params.trendHoldMa20Deviation) - 2.0
        and drawdown60 < drawdown_limit
        and loss_day_ratio20 <= 62
    )


def _trend_repair_reentry_buy(row, params: BacktestStrategyParams, position_rate: float, fund_type: str | None = None) -> bool:
    target_position = float(params.maxSinglePositionRate)
    if position_rate >= min(target_position * 0.9, target_position - 1.0):
        return False
    if _weak_trend_defense(row, fund_type):
        return False

    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return60_min = 2.5 if active else 5.0 if qdii else 4.0
    trend_score_min = 50.0 if active else 58.0 if qdii else 54.0
    risk_score_min = 28.0 if active else 34.0 if qdii else 30.0
    drawdown_limit = 16.5 if active else 12.5 if qdii else 15.0
    loss_day_limit = 66.0 if active else 58.0 if qdii else 62.0
    return (
        float(row.return20d) >= max(float(params.trendHoldReturn20d) * 0.5, 0.8)
        and float(row.return60d) >= return60_min
        and float(row.return5d) >= -0.5
        and float(row.ma20Deviation) >= max(float(params.trendHoldMa20Deviation), -7.0)
        and abs(float(row.maxDrawdown60d)) < drawdown_limit
        and float(row.trendScore) >= trend_score_min
        and float(row.riskScore) >= risk_score_min
        and float(row.lossDayRatio20d) <= loss_day_limit
    )


def _weak_trend_defense(row, fund_type: str | None = None) -> bool:
    if _midterm_trend_pullback(row, fund_type):
        return False
    if _recoverable_pullback(row):
        return False
    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return20_limit = -7 if active else -5 if qdii else -6
    return60_limit = -1 if active else 0
    ma20_limit = -5 if active else -3.5 if qdii else -4
    drawdown_limit = 20 if active else 16 if qdii else 18
    trend_score_limit = 46 if active else 50 if qdii else 48
    return (
        (
            float(row.return20d) <= return20_limit
            and float(row.return60d) <= return60_limit
            and float(row.ma20Deviation) <= ma20_limit
        )
        or (abs(float(row.maxDrawdown60d)) >= drawdown_limit and float(row.trendScore) < trend_score_limit)
    )


def _midterm_trend_pullback(row, fund_type: str | None = None) -> bool:
    return60_min = 8 if _is_active_equity_type(fund_type) else 12 if _is_qdii_or_overseas_type(fund_type) else 10
    return float(row.return60d) >= return60_min and abs(float(row.maxDrawdown60d)) < 22


def _orderly_trend_hold(row, params: BacktestStrategyParams, fund_type: str | None = None) -> bool:
    return60_min = 7 if _is_active_equity_type(fund_type) else 10 if _is_qdii_or_overseas_type(fund_type) else 8
    loss_day_limit = 68 if _is_active_equity_type(fund_type) else 60 if _is_qdii_or_overseas_type(fund_type) else 65
    return (
        float(row.return60d) >= return60_min
        and float(row.return20d) >= -4
        and float(row.ma20Deviation) >= float(params.trendHoldMa20Deviation) - 3
        and abs(float(row.maxDrawdown60d)) < 18
        and float(row.lossDayRatio20d) <= loss_day_limit
    )


def _recoverable_pullback(row) -> bool:
    return (
        float(row.return60d) > -8
        and float(row.return20d) > -18
        and abs(float(row.maxDrawdown60d)) < 18
        and float(row.opportunityScore) >= 65
        and float(row.riskScore) >= 30
    )


def _weak_trend_recovered(row, params: BacktestStrategyParams, fund_type: str | None = None) -> bool:
    return20 = float(row.return20d)
    return60 = float(row.return60d)
    ma20_deviation = float(row.ma20Deviation)
    drawdown60 = abs(float(row.maxDrawdown60d))
    trend_score = float(row.trendScore)
    risk_score = float(row.riskScore)
    loss_day_ratio20 = float(row.lossDayRatio20d)

    active = _is_active_equity_type(fund_type)
    qdii = _is_qdii_or_overseas_type(fund_type)
    return60_min = 5.5 if active else 6.0 if qdii else 4.5
    trend_score_min = 55.0 if active else 58.0 if qdii else 55.0
    risk_score_min = 30.0 if active else 34.0 if qdii else 30.0
    loss_day_limit = 62.0 if active else 58.0 if qdii else 62.0
    return (
        return20 > max(params.trendHoldReturn20d, 0.0)
        and return60 > return60_min
        and ma20_deviation >= max(params.trendHoldMa20Deviation, -6.0)
        and drawdown60 < 18
        and trend_score >= trend_score_min
        and risk_score >= risk_score_min
        and loss_day_ratio20 <= loss_day_limit
    )


def _sell_reason(
    score_exit: bool,
    profit_exit: bool,
    risk_exit: bool,
    weak_exit: bool,
    extreme_risk_exit: bool,
) -> str:
    if extreme_risk_exit:
        return "extreme_risk_exit"
    if weak_exit:
        return "weak_trend_defense"
    if risk_exit:
        return "risk_exit"
    if profit_exit:
        return "profit_exit"
    if score_exit:
        return "score_exit"
    return "sell_exit"


def _trend_score(row) -> float:
    score = 50 + row.return5d * 1.2 + row.return20d * 1.1 + row.return60d * 0.35 + row.ma20Deviation * 0.7
    return clamp(float(score))


def _opportunity_score(row) -> float:
    score = 50 - row.ma20Deviation * 1.0 - row.return5d * 0.6 + max(row.return20d, 0) * 0.4 + max(row.return60d, 0) * 0.2
    return clamp(float(score))


def _risk_score(row) -> float:
    score = 78 - row.volatility20d * 0.45 - abs(row.maxDrawdown60d) * 1.2 - max(row.lossDayRatio20d - 50, 0) * 0.35
    return clamp(float(score))


def _position_rate(share: float, nav: float, cash: float) -> float:
    position_value = share * nav
    total_asset = cash + position_value
    return position_value / total_asset * 100 if total_asset > 0 else 0.0


def _trade(
    row,
    action: str,
    amount: float,
    share: float,
    nav: float,
    fee: float,
    score: float,
    reason: str,
    trade_ratio: float,
    position_rate_before: float,
    position_rate_after: float,
) -> BacktestTrade:
    return BacktestTrade(
        date=str(row.date.date()),
        action=action,
        amount=round(amount, 4),
        share=round(share, 4),
        nav=round(nav, 6),
        fee=round(fee, 4),
        score=round(float(score), 2),
        reason=reason,
        tradeRatio=round(float(trade_ratio), 4),
        positionRateBefore=round(float(position_rate_before), 4),
        positionRateAfter=round(float(position_rate_after), 4),
        return5d=round(float(row.return5d), 4),
        return20d=round(float(row.return20d), 4),
        return60d=round(float(row.return60d), 4),
        ma20Deviation=round(float(row.ma20Deviation), 4),
        maxDrawdown60d=round(float(row.maxDrawdown60d), 4),
        trendScore=round(float(row.trendScore), 2),
        opportunityScore=round(float(row.opportunityScore), 2),
        riskScore=round(float(row.riskScore), 2),
    )


def _empty_result(request: BacktestRunRequest, settings: Settings, sample_size: int, diagnosis: str) -> BacktestResult:
    return BacktestResult(
        strategyName="QuantRuleEngine",
        modelVersion=settings.rule_model_version,
        fundCode=request.fundCode,
        fundName=request.fundName,
        fundType=request.fundType,
        startDate=request.startDate,
        endDate=request.endDate,
        initialCash=request.initialCash,
        finalAsset=request.initialCash,
        benchmarkFinalAsset=request.initialCash,
        positionBenchmarkFinalAsset=request.initialCash,
        totalReturnRate=0,
        annualReturnRate=0,
        benchmarkReturnRate=0,
        positionBenchmarkReturnRate=0,
        excessReturnRate=0,
        positionExcessReturnRate=0,
        maxDrawdownRate=0,
        benchmarkMaxDrawdownRate=0,
        positionBenchmarkMaxDrawdownRate=0,
        positionBenchmarkRate=0,
        winRate=0,
        sharpeRatio=None,
        calmarRatio=None,
        tradeCount=0,
        turnoverRate=0,
        navSampleSize=sample_size,
        dataCoverageRate=0,
        passed=False,
        diagnosis=diagnosis,
    )


def _result_years(result: BacktestResult) -> float:
    return max((pd.to_datetime(result.endDate) - pd.to_datetime(result.startDate)).days / 365, 0.1)


def _data_coverage_rate(start_date: str, end_date: str, frame: pd.DataFrame) -> float:
    requested_days = max((pd.to_datetime(end_date) - pd.to_datetime(start_date)).days + 1, 1)
    actual_days = max((frame["date"].iloc[-1] - frame["date"].iloc[0]).days + 1, 1)
    return min(actual_days / requested_days * 100, 100)


def _is_passed(
    annual: float,
    drawdown: float,
    benchmark_return: float,
    total_return: float,
    trade_count: int,
    frame: pd.DataFrame,
    data_coverage: float,
    fund_profile: FundProfile | None = None,
) -> bool:
    years = _frame_years(frame)
    profile = fund_profile or resolve_fund_profile(None, None, None)
    usable_history = data_coverage >= 80 or len(frame) >= MIN_USABLE_SHORT_HISTORY_SAMPLES
    trade_rate = trade_count / years
    return (
        usable_history
        and (
            _benchmark_pass(total_return, benchmark_return, profile)
            or _stable_positive_pass(annual, drawdown, benchmark_return, total_return, trade_rate, profile)
            or _defensive_profile_pass(annual, drawdown, benchmark_return, total_return, trade_rate, profile)
        )
        and drawdown > -25
        and trade_rate <= 12
        and annual > 0
    )


def _diagnosis(
    passed: bool,
    annual: float,
    total_return: float,
    benchmark_return: float,
    drawdown: float,
    benchmark_drawdown: float,
    trade_count: int,
    frame: pd.DataFrame,
    data_coverage: float,
    fund_profile: FundProfile | None = None,
) -> str:
    years = _frame_years(frame)
    profile = fund_profile or resolve_fund_profile(None, None, None)
    trade_rate = trade_count / years
    if passed:
        return "参数在该基金样本上通过：收益、回撤和交易频率符合当前基金画像口径"
    issues = []
    if data_coverage < 80 and len(frame) < MIN_USABLE_SHORT_HISTORY_SAMPLES:
        issues.append("样本覆盖不足")
    if not (
        _benchmark_pass(total_return, benchmark_return, profile)
        or _stable_positive_pass(annual, drawdown, benchmark_return, total_return, trade_rate, profile)
        or _defensive_profile_pass(annual, drawdown, benchmark_return, total_return, trade_rate, profile)
    ):
        issues.append("收益未达到该基金画像的通过口径")
    if drawdown <= benchmark_drawdown and not _defensive_profile_pass(annual, drawdown, benchmark_return, total_return, trade_rate, profile):
        issues.append("回撤改善不明显")
    if trade_rate > 12:
        issues.append("交易频率偏高")
    if total_return <= 0:
        issues.append("样本期收益为负")
    return "；".join(issues) if issues else "参数表现一般，建议扩大样本继续验证"


def _frame_years(frame: pd.DataFrame) -> float:
    return max((frame["date"].iloc[-1] - frame["date"].iloc[0]).days / 365, 0.1)


def _benchmark_pass(total_return: float, benchmark_return: float, profile: FundProfile) -> bool:
    return total_return + profile.benchmarkTolerance >= benchmark_return


def _stable_positive_pass(
    annual: float,
    drawdown: float,
    benchmark_return: float,
    total_return: float,
    trade_rate: float,
    profile: FundProfile,
) -> bool:
    benchmark_gap = benchmark_return - total_return
    return (
        annual >= profile.stableAnnualReturn
        and drawdown > profile.stableDrawdownFloor
        and benchmark_gap <= profile.stableBenchmarkGap
        and trade_rate <= profile.maxAnnualTrades
    )


def _defensive_profile_pass(
    annual: float,
    drawdown: float,
    benchmark_return: float,
    total_return: float,
    trade_rate: float,
    profile: FundProfile,
) -> bool:
    if profile.defensiveAnnualReturn is None or profile.defensiveDrawdownFloor is None or profile.defensiveBenchmarkGap is None:
        return False
    benchmark_gap = benchmark_return - total_return
    return (
        annual >= profile.defensiveAnnualReturn
        and total_return > 0
        and drawdown > profile.defensiveDrawdownFloor
        and benchmark_gap <= profile.defensiveBenchmarkGap
        and trade_rate <= profile.maxAnnualTrades
    )

def _summary_diagnosis(summary: BacktestSummary) -> str:
    annual_trades = summary.avgAnnualTradeCount or summary.avgTradeCount
    if summary.outperformPositionBenchmarkRate >= 52 and annual_trades <= MAX_AVG_ANNUAL_TRADE_COUNT and summary.passRate >= 50:
        return "整体可用：同仓位跑赢比例、交易频率和通过率达到第一版参考线"
    if summary.outperformPositionBenchmarkRate < 45:
        return "偏弱：多数基金未跑赢同仓位买入持有，建议下调买入阈值或检查卖出条件"
    if annual_trades > MAX_AVG_ANNUAL_TRADE_COUNT:
        return "偏频繁：年均交易次数较高，建议提高买入阈值或降低卖出敏感度"
    return "中性：部分指标达标，建议结合不同年份和基金类型继续验证"
