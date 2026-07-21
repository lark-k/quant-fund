from fastapi import APIRouter, HTTPException

from app.core.config import get_settings
from app.core.schemas import (
    QuantAnalyzeRequest,
    QuantBatchAnalyzeRequest,
    QuantBatchAnalyzeResponse,
)
from app.strategies.action_mapper import ACTION_TEXT
from app.strategies.rule_model import RuleQuantModel

router = APIRouter(prefix="/api/v1/quant", tags=["quant"])
PORTFOLIO_DAILY_SELL_BUDGET_RATE = 30.0


@router.post("/analyze")
def analyze(request: QuantAnalyzeRequest):
    model = RuleQuantModel(get_settings())
    return model.analyze(request)


@router.post("/analyze-batch", response_model=QuantBatchAnalyzeResponse)
def analyze_batch(request: QuantBatchAnalyzeRequest):
    settings = get_settings()
    if len(request.items) > settings.max_batch_funds:
        raise HTTPException(
            status_code=400,
            detail=f"items size exceeds max_batch_funds={settings.max_batch_funds}",
        )

    model = RuleQuantModel(settings)
    results = []
    errors = []
    for index, item in enumerate(request.items):
        try:
            results.append(model.analyze(item))
        except Exception as exc:  # pragma: no cover - defensive batch isolation
            errors.append(
                {
                    "index": index,
                    "requestId": getattr(item, "requestId", None),
                    "message": str(exc),
                }
            )
    results = _apply_portfolio_sell_budget(request.items, results)
    return QuantBatchAnalyzeResponse(
        requestId=request.requestId,
        results=results,
        successCount=len(results),
        failedCount=len(errors),
        errors=errors,
    )


def _apply_portfolio_sell_budget(items: list[QuantAnalyzeRequest], results: list):
    item_by_request = {item.requestId: item for item in items}
    candidates = [result for result in results if result.action == "SELL" and result.suggestAmount > 0]
    if not candidates:
        return results

    total_asset = max((float(item.account.totalAsset) for item in items), default=0.0)
    budget = round(total_asset * PORTFOLIO_DAILY_SELL_BUDGET_RATE / 100, 2)
    if budget <= 0:
        return results

    priority = {
        "extreme_risk_exit": 4,
        "risk_exit": 3,
        "weak_trend_defense": 2,
        "score_exit": 1,
        "position_limit": 0,
    }
    ranked = sorted(
        candidates,
        key=lambda result: (
            priority.get(str(result.metrics.get("decisionReason") or ""), 0),
            abs(float(result.metrics.get("currentDrawdown60d") or 0)),
            -float(result.score.riskScore),
        ),
        reverse=True,
    )
    replacements = {}
    remaining = budget
    for result in ranked:
        item = item_by_request.get(result.requestId)
        requested_amount = float(result.suggestAmount)
        if requested_amount <= remaining + 0.01:
            metrics = dict(result.metrics)
            metrics.update({
                "portfolioSellBudgetRate": PORTFOLIO_DAILY_SELL_BUDGET_RATE,
                "portfolioSellBudgetAmount": budget,
                "portfolioSellBudgetAdjusted": False,
                "portfolioSellBudgetDeferred": False,
            })
            replacements[result.requestId] = result.model_copy(update={"metrics": metrics})
            remaining = max(remaining - requested_amount, 0.0)
            continue

        if item is not None and remaining >= 100 and item.holding.holdingAmount > 0:
            adjusted_ratio = min(float(result.suggestRatio), remaining / float(item.holding.holdingAmount) * 100)
            adjusted_amount = round(float(item.holding.holdingAmount) * adjusted_ratio / 100, 2)
            metrics = dict(result.metrics)
            metrics.update({
                "portfolioSellBudgetRate": PORTFOLIO_DAILY_SELL_BUDGET_RATE,
                "portfolioSellBudgetAmount": budget,
                "portfolioSellBudgetAdjusted": True,
                "portfolioSellBudgetDeferred": False,
                "portfolioOriginalSuggestAmount": requested_amount,
                "portfolioOriginalSuggestRatio": float(result.suggestRatio),
            })
            reasons = list(result.reasons) + [
                f"账户当日减仓建议上限为总资产的 {PORTFOLIO_DAILY_SELL_BUDGET_RATE:.0f}%，本基金减仓比例已按剩余额度下调"
            ]
            replacements[result.requestId] = result.model_copy(update={
                "suggestAmount": adjusted_amount,
                "suggestRatio": round(adjusted_ratio, 2),
                "metrics": metrics,
                "reasons": reasons,
            })
            remaining = max(remaining - adjusted_amount, 0.0)
            continue

        metrics = _restore_deferred_state(dict(result.metrics), item)
        metrics.update({
            "portfolioSellBudgetRate": PORTFOLIO_DAILY_SELL_BUDGET_RATE,
            "portfolioSellBudgetAmount": budget,
            "portfolioSellBudgetAdjusted": False,
            "portfolioSellBudgetDeferred": True,
            "portfolioOriginalAction": result.action,
            "portfolioOriginalDecisionReason": result.metrics.get("decisionReason"),
            "portfolioOriginalSuggestAmount": requested_amount,
            "portfolioOriginalSuggestRatio": float(result.suggestRatio),
            "decisionReason": "portfolio_sell_budget_deferred",
        })
        reasons = list(result.reasons) + [
            "账户当日减仓建议额度已由更高风险持仓占用，本基金今日延后操作并继续观察"
        ]
        replacements[result.requestId] = result.model_copy(update={
            "action": "WATCH",
            "actionText": ACTION_TEXT["WATCH"],
            "suggestAmount": 0.0,
            "suggestRatio": 0.0,
            "metrics": metrics,
            "reasons": reasons,
        })

    return [replacements.get(result.requestId, result) for result in results]


def _restore_deferred_state(metrics: dict, item: QuantAnalyzeRequest | None) -> dict:
    if item is None:
        return metrics
    previous = item.strategyState
    decision_date = item.market.now.date().isoformat() if item.market.now else previous.lastActionDate
    metrics.update({
        "weakTrendCandidateDaysAfter": previous.weakTrendCandidateDays,
        "weakTrendDefenseHandledAfter": previous.weakTrendDefenseHandled,
        "weakTrendCooldownDaysAfter": previous.weakTrendCooldownDays,
        "positionRebalanceCooldownDaysAfter": previous.positionRebalanceCooldownDays,
        "weakRecoveryRequiredAfter": previous.weakRecoveryRequired,
        "extremeRiskSellCountAfter": previous.extremeRiskSellCount,
        "extremeRiskStageAfter": previous.extremeRiskStage,
        "lastExtremeRiskDateAfter": previous.lastExtremeRiskDate,
        "lastExtremeDrawdownAfter": previous.lastExtremeDrawdown,
        "lastActionDateAfter": decision_date,
        "lastDefenseDateAfter": previous.lastDefenseDate,
    })
    return metrics
