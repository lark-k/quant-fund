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
from app.strategies.scoring import clamp


WEAK_TREND_COOLDOWN_DAYS = 45
WEAK_TREND_CONFIRM_DAYS = 5
POSITION_BENCHMARK_TOLERANCE = 0.5


def run_single_backtest(request: BacktestRunRequest, settings: Settings) -> BacktestResult:
    params = request.strategyParams
    frame = _prepare_frame(request.navSeries, request.startDate, request.endDate, params.warmupDays)
    if len(frame) < max(2, params.minNavSamples):
        return _empty_result(request, settings, len(frame), "历史净值样本不足，无法进行有效回测")

    frame = _attach_signal_columns(frame, params)
    start = pd.to_datetime(request.startDate)
    trade_frame = frame[frame["date"] >= start].copy().reset_index(drop=True)
    if len(trade_frame) < 2:
        return _empty_result(request, settings, len(trade_frame), "回测区间内历史净值样本不足")

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
        strong_trend_lock = _strong_trend_lock(row, params)
        trend_start_buy = _trend_start_buy(row, params)
        midterm_trend_buy = _midterm_trend_buy(row)
        weak_trend_defense = _weak_trend_defense(row)
        if weak_trend_defense:
            weak_trend_cooldown = max(weak_trend_cooldown, WEAK_TREND_COOLDOWN_DAYS)
            weak_recovery_required = True
            weak_trend_watch_days = weak_trend_watch_days + 1
        else:
            weak_trend_cooldown = max(weak_trend_cooldown - 1, 0)
            weak_trend_watch_days = 0
        if weak_recovery_required and _weak_trend_recovered(row):
            weak_recovery_required = False
            weak_trend_defense_handled = False
            weak_trend_watch_days = 0

        if (
            (_score_threshold_buy(row, params) or strong_trend_lock or trend_start_buy or midterm_trend_buy)
            and row.sampleIndex >= params.minNavSamples
            and position_rate < params.maxSinglePositionRate
            and weak_trend_cooldown == 0
            and (not weak_recovery_required or strong_trend_lock)
        ):
            room = max(params.maxSinglePositionRate - position_rate, 0)
            buy_ratio = min(_buy_step_ratio(params, strong_trend_lock, trend_start_buy, midterm_trend_buy, position_rate), room)
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
                reason = _buy_reason(strong_trend_lock, trend_start_buy, midterm_trend_buy)
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
        trend_hold = _should_hold_trend(row, params)
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
                sell_ratio = max(sell_ratio, 25.0)
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
    passed = _is_passed(annual, drawdown, position_benchmark_return, total_return, trade_count, trade_frame, data_coverage)

    return BacktestResult(
        strategyName="QuantRuleEngine",
        modelVersion=settings.rule_model_version,
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
        passed=passed,
        diagnosis=_diagnosis(
            passed,
            total_return,
            position_benchmark_return,
            drawdown,
            position_benchmark_drawdown,
            trade_count,
            trade_frame,
            data_coverage,
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
        modelVersion=settings.rule_model_version,
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
        avgSharpeRatio=round(float(np.mean(sharpe)), 4) if sharpe else 0,
        avgCalmarRatio=round(float(np.mean(calmar)), 4) if calmar else 0,
        passRate=round(len(passed) / len(results) * 100, 4),
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


def _attach_signal_columns(frame: pd.DataFrame, params: BacktestStrategyParams) -> pd.DataFrame:
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
    rolling_max_60 = nav.rolling(60, min_periods=2).max()
    out["maxDrawdown60d"] = ((nav / rolling_max_60 - 1) * 100).fillna(0)
    out["lossDayRatio20d"] = returns.rolling(20, min_periods=2).apply(lambda value: (value < 0).mean() * 100, raw=False).fillna(0)
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
    out.loc[out.index < params.minNavSamples, "totalScore"] = 50.0
    return out


def _should_hold_trend(row, params: BacktestStrategyParams) -> bool:
    return _strong_trend_lock(row, params) or _midterm_trend_pullback(row) or (
        float(row.return20d) > params.trendHoldReturn20d
        and float(row.ma20Deviation) >= params.trendHoldMa20Deviation
    )


def _buy_step_ratio(
    params: BacktestStrategyParams,
    strong_trend_lock: bool,
    trend_start_buy: bool = False,
    midterm_trend_buy: bool = False,
    position_rate: float = 0.0,
) -> float:
    if position_rate <= 1.0 and (strong_trend_lock or trend_start_buy):
        return params.maxSinglePositionRate
    if position_rate <= 1.0 and midterm_trend_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 30.0))
    if strong_trend_lock:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 30.0))
    if trend_start_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 25.0))
    if midterm_trend_buy:
        return max(params.buyStepRatio, min(params.maxSinglePositionRate, 20.0))
    return params.buyStepRatio


def _buy_reason(strong_trend_lock: bool, trend_start_buy: bool, midterm_trend_buy: bool = False) -> str:
    if strong_trend_lock:
        return "strong_trend_buy"
    if trend_start_buy:
        return "trend_start_buy"
    if midterm_trend_buy:
        return "midterm_trend_buy"
    return "score_above_buy_threshold"


def _score_threshold_buy(row, params: BacktestStrategyParams) -> bool:
    return (
        float(row.totalScore) >= params.buyThreshold
        and float(row.trendScore) >= 55
        and float(row.opportunityScore) >= 48
        and float(row.riskScore) >= 25
        and not (float(row.return5d) <= -1 and float(row.return20d) <= 0)
    )


def _strong_trend_lock(row, params: BacktestStrategyParams) -> bool:
    return (
        float(row.return20d) >= params.trendHoldReturn20d
        and float(row.return60d) >= 8
        and float(row.ma20Deviation) >= params.trendHoldMa20Deviation
        and abs(float(row.maxDrawdown60d)) < 16
    )


def _trend_start_buy(row, params: BacktestStrategyParams) -> bool:
    return (
        float(row.return20d) >= params.trendHoldReturn20d
        and float(row.return5d) >= 0
        and float(row.ma20Deviation) >= params.trendHoldMa20Deviation
        and abs(float(row.maxDrawdown60d)) < 16
        and float(row.riskScore) >= 35
        and float(row.lossDayRatio20d) <= 58
    )


def _midterm_trend_buy(row) -> bool:
    return (
        _midterm_trend_pullback(row)
        and float(row.return5d) >= -1
        and float(row.return20d) >= -4
        and float(row.ma20Deviation) >= -8
        and float(row.riskScore) >= 25
        and float(row.lossDayRatio20d) <= 65
    )


def _weak_trend_defense(row) -> bool:
    if _midterm_trend_pullback(row):
        return False
    if _recoverable_pullback(row):
        return False
    return (
        (
            float(row.return20d) <= -6
            and float(row.return60d) <= 0
            and float(row.ma20Deviation) <= -4
        )
        or (abs(float(row.maxDrawdown60d)) >= 15 and float(row.trendScore) < 50)
    )


def _midterm_trend_pullback(row) -> bool:
    return float(row.return60d) >= 12 and abs(float(row.maxDrawdown60d)) < 22


def _recoverable_pullback(row) -> bool:
    return (
        float(row.return60d) > -8
        and float(row.return20d) > -18
        and abs(float(row.maxDrawdown60d)) < 18
        and float(row.opportunityScore) >= 65
        and float(row.riskScore) >= 30
    )


def _weak_trend_recovered(row) -> bool:
    return (
        float(row.return20d) > 0
        and float(row.return60d) > 0
        and float(row.ma20Deviation) > -2
        and float(row.trendScore) >= 55
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


def _data_coverage_rate(start_date: str, end_date: str, frame: pd.DataFrame) -> float:
    requested_days = max((pd.to_datetime(end_date) - pd.to_datetime(start_date)).days + 1, 1)
    actual_days = max((frame["date"].iloc[-1] - frame["date"].iloc[0]).days + 1, 1)
    return min(actual_days / requested_days * 100, 100)


def _is_passed(annual: float, drawdown: float, benchmark_return: float, total_return: float, trade_count: int, frame: pd.DataFrame, data_coverage: float) -> bool:
    years = max((frame["date"].iloc[-1] - frame["date"].iloc[0]).days / 365, 0.1)
    return (
        data_coverage >= 80
        and total_return + POSITION_BENCHMARK_TOLERANCE >= benchmark_return
        and drawdown > -25
        and trade_count / years <= 12
        and annual > 0
    )


def _diagnosis(passed: bool, total_return: float, benchmark_return: float, drawdown: float, benchmark_drawdown: float, trade_count: int, frame: pd.DataFrame, data_coverage: float) -> str:
    years = max((frame["date"].iloc[-1] - frame["date"].iloc[0]).days / 365, 0.1)
    if passed:
        return "参数在该基金样本上通过：收益跑赢同仓位买入持有，回撤和交易频率可控"
    issues = []
    if data_coverage < 80:
        issues.append("样本覆盖不足")
    if total_return + POSITION_BENCHMARK_TOLERANCE < benchmark_return:
        issues.append("收益未跑赢同仓位买入持有")
    if drawdown <= benchmark_drawdown:
        issues.append("回撤改善不明显")
    if trade_count / years > 12:
        issues.append("交易频率偏高")
    if total_return <= 0:
        issues.append("样本期收益为负")
    return "；".join(issues) if issues else "参数表现一般，建议扩大样本继续验证"


def _summary_diagnosis(summary: BacktestSummary) -> str:
    if summary.outperformPositionBenchmarkRate >= 52 and summary.avgTradeCount <= 12 and summary.passRate >= 50:
        return "整体可用：同仓位跑赢比例、交易频率和通过率达到第一版参考线"
    if summary.outperformPositionBenchmarkRate < 45:
        return "偏弱：多数基金未跑赢同仓位买入持有，建议下调买入阈值或检查卖出条件"
    if summary.avgTradeCount > 12:
        return "偏频繁：平均交易次数较高，建议提高买入阈值或降低卖出敏感度"
    return "中性：部分指标达标，建议结合不同年份和基金类型继续验证"
