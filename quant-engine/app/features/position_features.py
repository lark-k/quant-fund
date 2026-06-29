from __future__ import annotations

from app.core.schemas import AccountSnapshot, HoldingSnapshot, RiskProfile


def calculate_position_features(
    account: AccountSnapshot,
    risk_profile: RiskProfile,
    holding: HoldingSnapshot,
) -> dict[str, float | bool]:
    single_limit = risk_profile.maxSingleFundPositionRate or 0
    equity_limit = risk_profile.maxEquityPositionRate or 0
    position_to_limit = holding.positionRate / single_limit if single_limit > 0 else 1.0
    equity_to_limit = account.equityPositionRate / equity_limit if equity_limit > 0 else 1.0

    can_buy_more = holding.positionRate < single_limit and account.equityPositionRate < equity_limit
    should_reduce = holding.positionRate > single_limit or account.equityPositionRate > equity_limit

    return {
        "positionRate": round(holding.positionRate, 4),
        "positionToSingleLimit": round(position_to_limit, 4),
        "equityPositionToLimit": round(equity_to_limit, 4),
        "profitBuffer": round(max(holding.holdingProfitRate, 0), 4),
        "lossPressure": round(abs(min(holding.holdingProfitRate, 0)), 4),
        "canBuyMore": can_buy_more,
        "shouldReduceByPosition": should_reduce,
    }
