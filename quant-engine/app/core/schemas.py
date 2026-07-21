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
    indexReturnRate: float | None = None
    indexCode: str | None = None
    indexName: str | None = None
    marketSh000001ReturnRate: float | None = None
    marketSz399001ReturnRate: float | None = None
    marketCyb399006ReturnRate: float | None = None
    marketHs300ReturnRate: float | None = None
    marketZz500ReturnRate: float | None = None
    estimated: bool = False
    observedAt: datetime | None = None
    navSource: str | None = None


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


class BacktestStrategyParams(ApiModel):
    buyThreshold: float = 52
    sellThreshold: float = 6
    maxSinglePositionRate: float = 45
    buyStepRatio: float = 20
    sellStepRatio: float = 8
    takeProfitRate: float = 300
    stopLossRate: float = -18
    minNavSamples: int = 40
    warmupDays: int = 180
    trendHoldReturn20d: float = 1.5
    trendHoldMa20Deviation: float = -7


class StrategyExecutionState(ApiModel):
    weakTrendCandidateDays: int = 0
    weakTrendDefenseHandled: bool = False
    weakTrendCooldownDays: int = 0
    positionRebalanceCooldownDays: int = 0
    weakRecoveryRequired: bool = False
    extremeRiskStage: int = 0
    lastExtremeRiskDate: str | None = None
    lastExtremeDrawdown: float | None = None
    lastActionDate: str | None = None
    # Kept during the rule-v1.37 migration window. New decisions use extremeRiskStage.
    extremeRiskSellCount: int = 0
    lastDefenseDate: str | None = None


class QuantAnalyzeRequest(ApiModel):
    requestId: str
    userId: int | None = None
    account: AccountSnapshot = Field(default_factory=AccountSnapshot)
    riskProfile: RiskProfile = Field(default_factory=RiskProfile)
    holding: HoldingSnapshot
    navSeries: list[NavPoint] = Field(default_factory=list)
    tradeRecords: list[TradeRecord] = Field(default_factory=list)
    strategyParams: BacktestStrategyParams = Field(default_factory=BacktestStrategyParams)
    strategyState: StrategyExecutionState = Field(default_factory=StrategyExecutionState)
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


class BacktestOptions(ApiModel):
    workers: int = 6
    saveEquityCurve: bool = True
    saveTrades: bool = True
    enableMl: bool = False


class BacktestFund(ApiModel):
    fundCode: str
    fundName: str = ""
    fundType: str = "UNKNOWN"
    navSeries: list[NavPoint] = Field(default_factory=list)


class BacktestRunRequest(ApiModel):
    fundCode: str
    fundName: str = ""
    fundType: str = "UNKNOWN"
    startDate: str
    endDate: str
    initialCash: float = 10000
    feeRate: float = 0.0015
    navSeries: list[NavPoint] = Field(default_factory=list)
    strategyParams: BacktestStrategyParams = Field(default_factory=BacktestStrategyParams)


class BacktestBatchRunRequest(ApiModel):
    taskName: str = "rule-backtest"
    strategyName: str = "QuantRuleEngine"
    startDate: str
    endDate: str
    initialCash: float = 10000
    feeRate: float = 0.0015
    funds: list[BacktestFund] = Field(default_factory=list)
    strategyParams: BacktestStrategyParams = Field(default_factory=BacktestStrategyParams)
    options: BacktestOptions = Field(default_factory=BacktestOptions)


class BacktestTrade(ApiModel):
    date: str
    signalDate: str | None = None
    action: Literal["BUY", "SELL"]
    amount: float
    share: float
    nav: float
    fee: float
    score: float
    reason: str
    tradeRatio: float = 0
    positionRateBefore: float = 0
    positionRateAfter: float = 0
    return5d: float = 0
    return20d: float = 0
    return60d: float = 0
    ma20Deviation: float = 0
    maxDrawdown60d: float = 0
    trendScore: float = 0
    opportunityScore: float = 0
    riskScore: float = 0


class BacktestEquityPoint(ApiModel):
    date: str
    totalAsset: float
    cash: float
    positionValue: float
    positionRate: float
    nav: float
    signalScore: float
    action: str


class BacktestResult(ApiModel):
    strategyName: str
    modelVersion: str
    fundCode: str
    fundName: str = ""
    fundType: str = "UNKNOWN"
    startDate: str
    endDate: str
    initialCash: float
    finalAsset: float
    benchmarkFinalAsset: float
    positionBenchmarkFinalAsset: float
    totalReturnRate: float
    annualReturnRate: float
    benchmarkReturnRate: float
    positionBenchmarkReturnRate: float
    excessReturnRate: float
    positionExcessReturnRate: float
    maxDrawdownRate: float
    benchmarkMaxDrawdownRate: float
    positionBenchmarkMaxDrawdownRate: float
    positionBenchmarkRate: float
    winRate: float
    sharpeRatio: float | None = None
    calmarRatio: float | None = None
    tradeCount: int
    turnoverRate: float
    navSampleSize: int
    dataCoverageRate: float = 0
    mlApplied: bool = False
    mlAppliedDays: int = 0
    mlScoreAdjustmentAvg: float = 0
    mlScoreAdjustmentAbsAvg: float = 0
    mlScoreAdjustmentMaxAbs: float = 0
    mlExpectedReturnAvg: float = 0
    mlExpectedReturnPositiveDays: int = 0
    mlExpectedReturnPositiveDayRate: float = 0
    mlProbabilityAvg: float = 0
    mlBullishDays: int = 0
    mlBullishDayRate: float = 0
    mlSignalStrengthAvg: float = 0
    mlConfidenceScoreAvg: float = 0
    mlConfidenceMediumHighDayRate: float = 0
    passed: bool
    diagnosis: str
    equityCurve: list[BacktestEquityPoint] = Field(default_factory=list)
    trades: list[BacktestTrade] = Field(default_factory=list)


class BacktestSummary(ApiModel):
    avgAnnualReturnRate: float = 0
    medianAnnualReturnRate: float = 0
    p10AnnualReturnRate: float = 0
    avgMaxDrawdownRate: float = 0
    medianMaxDrawdownRate: float = 0
    worstMaxDrawdownRate: float = 0
    winFundRate: float = 0
    outperformBuyHoldRate: float = 0
    outperformPositionBenchmarkRate: float = 0
    avgPositionExcessReturnRate: float = 0
    avgTradeCount: float = 0
    avgAnnualTradeCount: float = 0
    avgSharpeRatio: float = 0
    avgCalmarRatio: float = 0
    passRate: float = 0
    mlAppliedFundRate: float = 0
    avgMlScoreAdjustmentAbs: float = 0
    maxMlScoreAdjustmentAbs: float = 0
    avgMlExpectedReturn: float = 0
    avgMlExpectedReturnPositiveDays: float = 0
    avgMlExpectedReturnPositiveDayRate: float = 0
    avgMlProbability: float = 0
    avgMlBullishDays: float = 0
    avgMlBullishDayRate: float = 0
    avgMlSignalStrength: float = 0
    avgMlConfidenceScore: float = 0
    avgMlConfidenceMediumHighDayRate: float = 0
    diagnosis: str = "NO_DATA"


class BacktestBatchRunResponse(ApiModel):
    taskId: str
    taskName: str
    status: Literal["COMPLETED", "FAILED"]
    strategyName: str
    modelVersion: str
    fundCount: int
    successCount: int
    failedCount: int
    summary: BacktestSummary
    results: list[BacktestResult] = Field(default_factory=list)
    errors: list[dict[str, Any]] = Field(default_factory=list)


class BacktestGridRunRequest(BacktestBatchRunRequest):
    paramGrid: dict[str, list[float]] = Field(default_factory=dict)


class BacktestGridResult(ApiModel):
    rank: int
    annualReturnRate: float
    maxDrawdownRate: float
    calmarRatio: float
    outperformBuyHoldRate: float
    passRate: float
    params: dict[str, float]


class BacktestGridRunResponse(ApiModel):
    taskId: str
    status: Literal["COMPLETED", "FAILED"]
    combinationCount: int
    bestParams: dict[str, float]
    topResults: list[BacktestGridResult]


class MlTrainingLabelConfig(ApiModel):
    horizonDays: int = 20
    minForwardReturn: float = 2
    maxForwardDrawdown: float = -8


class MlTrainingSampleExportRequest(BacktestBatchRunRequest):
    labelConfig: MlTrainingLabelConfig = Field(default_factory=MlTrainingLabelConfig)


class MlTrainingSampleExportResponse(ApiModel):
    fileName: str
    rowCount: int
    fundCount: int
    positiveCount: int
    negativeCount: int
    featureColumns: list[str]
    labelConfig: MlTrainingLabelConfig
    csvContent: str
