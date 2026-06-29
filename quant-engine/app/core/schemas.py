from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


Action = Literal["BUY", "SELL", "HOLD", "WATCH", "CONVERT"]
RiskLevel = Literal["LOW", "MEDIUM", "HIGH"]


class ApiModel(BaseModel):
    model_config = ConfigDict(extra="ignore")


class AccountSnapshot(ApiModel):
    accountId: int | None = None
    totalAsset: float = 0
    totalInvestAmount: float = 0
    currentProfit: float = 0
    currentProfitRate: float = 0
    dailyProfit: float = 0
    equityPositionRate: float = 0
    maxSingleFundPositionRate: float = 0


class RiskProfile(ApiModel):
    riskLevel: RiskLevel = "MEDIUM"
    maxEquityPositionRate: float = 70
    maxSingleFundPositionRate: float = 25
    drawdownAlertRate: float = 8
    dailyRiseAlertRate: float = 2
    dailyFallAlertRate: float = 2


class HoldingSnapshot(ApiModel):
    holdingId: int | None = None
    fundCode: str
    fundName: str = ""
    fundType: str = "UNKNOWN"
    activeFund: bool = False
    holdingAmount: float = 0
    holdingShare: float = 0
    holdingCost: float = 0
    holdingProfit: float = 0
    holdingProfitRate: float = 0
    dailyProfit: float = 0
    positionRate: float = 0
    currentEstimateNav: float | None = None
    latestOfficialNav: float | None = None
    currentEstimateGrowthRate: float = 0
    relatedThemeName: str | None = None
    relatedThemeRate: float = 0
    marketStatus: str | None = None
    holdingDays: int = 0
    coreHolding: bool = False
    watchFocus: bool = False


class NavPoint(ApiModel):
    date: str
    nav: float
    accumulatedNav: float | None = None
    dailyGrowthRate: float | None = None


class TradeRecord(ApiModel):
    tradeType: str
    tradeAmount: float = 0
    tradeShare: float = 0
    tradeNav: float = 0
    tradeTime: str | None = None

    @field_validator("tradeAmount", "tradeShare", "tradeNav", mode="before")
    @classmethod
    def default_missing_number(cls, value: Any) -> Any:
        return 0 if value is None else value


class MarketContext(ApiModel):
    tradingDay: bool = True
    trading: bool = True
    decisionPhase: str = "FINAL_DECISION"
    now: datetime | None = None
    deadline: datetime | None = None

    @field_validator("now", "deadline", mode="before")
    @classmethod
    def parse_datetime(cls, value: Any) -> Any:
        if value is None or isinstance(value, datetime):
            return value
        if isinstance(value, str):
            text = value.strip()
            for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S"):
                try:
                    return datetime.strptime(text, fmt)
                except ValueError:
                    pass
        return value


class QuantAnalyzeRequest(ApiModel):
    requestId: str
    userId: int | None = None
    account: AccountSnapshot = Field(default_factory=AccountSnapshot)
    riskProfile: RiskProfile = Field(default_factory=RiskProfile)
    holding: HoldingSnapshot
    navSeries: list[NavPoint] = Field(default_factory=list)
    tradeRecords: list[TradeRecord] = Field(default_factory=list)
    market: MarketContext = Field(default_factory=MarketContext)


class ScoreBreakdown(ApiModel):
    totalScore: float
    trendScore: float
    opportunityScore: float
    riskScore: float
    positionScore: float
    momentumScore: float


class QuantSignalResponse(ApiModel):
    requestId: str
    fundCode: str
    holdingId: int | None = None
    action: Action
    actionText: str
    suggestAmount: float = 0
    suggestRatio: float = 0
    confidence: float
    riskLevel: RiskLevel
    score: ScoreBreakdown
    metrics: dict[str, float | bool | int | str | None]
    reasons: list[str]
    risks: list[str]
    modelName: str = "QuantRuleEngine"
    modelVersion: str
    deadline: str | None = None
    disclaimer: str = "仅供参考，不构成投资建议，不承诺收益；系统不接真实交易下单接口。"


class QuantBatchAnalyzeRequest(ApiModel):
    requestId: str
    items: list[QuantAnalyzeRequest]


class QuantBatchAnalyzeResponse(ApiModel):
    requestId: str
    results: list[QuantSignalResponse]
    successCount: int
    failedCount: int
    errors: list[dict[str, Any]] = Field(default_factory=list)
