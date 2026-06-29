from __future__ import annotations

from datetime import date, timedelta

from app.core.schemas import (
    AccountSnapshot,
    HoldingSnapshot,
    MarketContext,
    NavPoint,
    QuantAnalyzeRequest,
    RiskProfile,
)


def make_nav_series(length: int = 80, start: float = 1.0, daily_step: float = 0.002) -> list[NavPoint]:
    value = start
    start_date = date(2026, 3, 1)
    points: list[NavPoint] = []
    for day in range(length):
        value = value * (1 + daily_step)
        points.append(
            NavPoint(
                date=(start_date + timedelta(days=day)).isoformat(),
                nav=round(value, 6),
                dailyGrowthRate=round(daily_step * 100, 4),
            )
        )
    return points


def make_request(**overrides) -> QuantAnalyzeRequest:
    holding = overrides.pop(
        "holding",
        HoldingSnapshot(
            holdingId=1001,
            fundCode="025833",
            fundName="Example Index Fund",
            fundType="INDEX",
            holdingAmount=1200,
            holdingProfitRate=6.2,
            positionRate=12,
            currentEstimateGrowthRate=0.6,
            relatedThemeRate=0.8,
            holdingDays=35,
        ),
    )
    market = overrides.pop(
        "market",
        MarketContext(
            tradingDay=True,
            trading=True,
            decisionPhase="FINAL_DECISION",
            now="2026-06-28 14:50:00",
            deadline="2026-06-28 15:00:00",
        ),
    )
    data = {
        "requestId": "qf-test-1001",
        "userId": 1,
        "account": AccountSnapshot(accountId=1, totalAsset=10000, equityPositionRate=45),
        "riskProfile": RiskProfile(riskLevel="MEDIUM", maxEquityPositionRate=70, maxSingleFundPositionRate=25),
        "holding": holding,
        "navSeries": make_nav_series(),
        "tradeRecords": [],
        "market": market,
    }
    data.update(overrides)
    return QuantAnalyzeRequest(**data)
