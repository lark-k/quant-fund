export const DISCLAIMER = '仅供参考，不构成投资建议，不承诺收益'
export const SIMULATED_TRADE_NOTICE = '仅为模拟操作，并非真实交易'

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type StrategyAction = 'BUY' | 'SELL' | 'HOLD' | 'CONVERT' | 'WATCH'

export interface UserProfile {
  id: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
  riskLevel: RiskLevel
  phone?: string
  email?: string
  avatar?: string
}

export interface LoginResult {
  token: string
  tokenName?: string
  tokenTimeout?: number
  user: UserProfile
}

export type ProfileUpdateRequest = {
  nickname?: string
  phone?: string
  email?: string
  avatar?: string
}

export type PasswordUpdateRequest = {
  oldPassword: string
  newPassword: string
}

export interface PortfolioSummary {
  totalAsset: number
  totalInvestAmount: number
  currentProfit: number
  currentProfitRate: number
  dailyProfit: number
  equityPositionRate: number
  bondPositionRate: number
  cashPositionRate: number
  holdingCount: number
}

export interface FundHolding {
  id: number
  accountId: number
  fundCode: string
  fundName: string
  fundType: string
  activeFund: boolean
  holdingAmount: number
  holdingShare: number
  holdingCost: number
  currentEstimateNav: number | null
  latestOfficialNav: number | null
  holdingProfit: number
  holdingProfitRate: number
  dailyProfit: number
  yesterdayProfit: number
  positionRate: number
  currentEstimateGrowthRate: number
  officialNavUpdated: boolean
  officialNavDate?: string | null
  relatedThemeName: string
  relatedThemeRate: number
  valuationSource: string
  estimateBasis: string
  marketStatus: string
  holdingDays: number
  sourcePlatform: string
  regularInvestment: boolean
  coreHolding: boolean
  watchFocus: boolean
  updateTime: string
  disclaimer: string
}

export interface PortfolioAccount {
  id: number
  accountName: string
  platformType: string
  totalAsset: number
  totalInvestAmount: number
  currentProfit: number
  currentProfitRate: number
  dailyProfit: number
  cashPositionRate: number
  equityPositionRate: number
  bondPositionRate: number
  maxSingleFundPositionRate: number
  status: string
  updateTime: string
}

export type PortfolioAccountRequest = {
  accountName: string
  platformType: 'ALIPAY' | 'EASTMONEY' | 'BROKER' | 'MANUAL'
  maxSingleFundPositionRate: number
}

export type HoldingUpdateRequest = {
  accountId: number
  fundCode: string
  fundName: string
  fundType: string
  activeFund: boolean
  holdingAmount: number
  holdingShare: number
  holdingCost: number
  holdingProfit?: number
  currentEstimateNav?: number
  latestOfficialNav?: number
  sourcePlatform?: string
  regularInvestment?: boolean
  coreHolding?: boolean
  watchFocus?: boolean
}

export type HoldingCreateRequest = HoldingUpdateRequest

export type ClearHoldingRequest = {
  tradeAmount?: number
  tradeFee?: number
  remark?: string
}

export interface FundSearchResult {
  fundCode: string
  fundName: string
  fundType: string
  pinyin?: string
  sourceName: string
}

export type FundSearchMode = 'FUZZY' | 'EXACT'

export interface StrategySignal {
  id: number
  accountId: number
  holdingId: number
  fundCode: string
  fundName?: string
  signalType: string
  action: StrategyAction
  actionText: string
  suggestAmount: number
  suggestRatio: number
  riskLevel: RiskLevel
  confidence: number
  reasons: string[]
  signalTime: string
  disclaimer: string
}

export interface QuantSignal {
  id: number
  accountId: number
  holdingId: number
  fundCode: string
  fundName?: string
  action: StrategyAction
  actionText: string
  suggestAmount: number
  suggestRatio: number
  riskLevel: RiskLevel
  confidence: number
  totalScore: number
  trendScore: number
  opportunityScore: number
  riskScore: number
  positionScore: number
  momentumScore: number
  reasons: string[]
  risks: string[]
  metricsJson: string
  modelName: string
  modelVersion: string
  deadline?: string | null
  signalTime: string
  fallbackUsed: boolean
  disclaimer: string
}

export interface QuantEngineHealth {
  status: string
  service: string
  modelVersion: string
  enabled: boolean
}

export type BacktestRunRequest = {
  accountId?: number
  fundCodes?: string[]
  startDate: string
  endDate: string
  initialCash: number
  feeRate: number
  strategyParams: {
    buyThreshold: number
    sellThreshold: number
    maxSinglePositionRate: number
    buyStepRatio: number
    sellStepRatio: number
    takeProfitRate: number
    stopLossRate: number
    minNavSamples: number
    warmupDays: number
    trendHoldReturn20d: number
    trendHoldMa20Deviation: number
  }
  options: {
    workers: number
    saveEquityCurve: boolean
    saveTrades: boolean
    enableMl?: boolean
  }
}

export type BacktestEquityPoint = {
  date: string
  totalAsset: number
  cash: number
  positionValue: number
  positionRate: number
  nav: number
  signalScore: number
  action: string
}

export type BacktestTrade = {
  date: string
  action: 'BUY' | 'SELL'
  amount: number
  share: number
  nav: number
  fee: number
  score: number
  reason: string
  tradeRatio: number
  positionRateBefore: number
  positionRateAfter: number
  return5d: number
  return20d: number
  return60d: number
  ma20Deviation: number
  maxDrawdown60d: number
  trendScore: number
  opportunityScore: number
  riskScore: number
}

export type BacktestResult = {
  strategyName: string
  modelVersion: string
  fundCode: string
  fundName: string
  fundType: string
  startDate: string
  endDate: string
  initialCash: number
  finalAsset: number
  benchmarkFinalAsset: number
  positionBenchmarkFinalAsset: number
  totalReturnRate: number
  annualReturnRate: number
  benchmarkReturnRate: number
  positionBenchmarkReturnRate: number
  excessReturnRate: number
  positionExcessReturnRate: number
  maxDrawdownRate: number
  benchmarkMaxDrawdownRate: number
  positionBenchmarkMaxDrawdownRate: number
  positionBenchmarkRate: number
  winRate: number
  sharpeRatio: number | null
  calmarRatio: number | null
  tradeCount: number
  turnoverRate: number
  navSampleSize: number
  dataCoverageRate: number
  mlApplied?: boolean
  mlAppliedDays?: number
  mlScoreAdjustmentAvg?: number
  mlScoreAdjustmentAbsAvg?: number
  mlScoreAdjustmentMaxAbs?: number
  mlExpectedReturnAvg?: number
  mlExpectedReturnPositiveDays?: number
  mlExpectedReturnPositiveDayRate?: number
  mlProbabilityAvg?: number
  mlBullishDays?: number
  mlBullishDayRate?: number
  mlSignalStrengthAvg?: number
  mlConfidenceScoreAvg?: number
  mlConfidenceMediumHighDayRate?: number
  passed: boolean
  diagnosis: string
  equityCurve: BacktestEquityPoint[]
  trades: BacktestTrade[]
}

export type BacktestNavRefreshItem = {
  fundCode: string
  fundName: string
  requestedStartDate: string
  requestedEndDate: string
  navCount: number
  firstNavDate: string | null
  lastNavDate: string | null
  status: string
  message: string
}

export type BacktestNavRefreshResponse = {
  fundCount: number
  successCount: number
  failedCount: number
  requestedStartDate: string
  requestedEndDate: string
  results: BacktestNavRefreshItem[]
}

export type BacktestSummary = {
  avgAnnualReturnRate: number
  medianAnnualReturnRate: number
  p10AnnualReturnRate: number
  avgMaxDrawdownRate: number
  medianMaxDrawdownRate: number
  worstMaxDrawdownRate: number
  winFundRate: number
  outperformBuyHoldRate: number
  outperformPositionBenchmarkRate: number
  avgPositionExcessReturnRate: number
  avgTradeCount: number
  avgAnnualTradeCount?: number
  avgSharpeRatio: number
  avgCalmarRatio: number
  passRate: number
  mlAppliedFundRate?: number
  avgMlScoreAdjustmentAbs?: number
  maxMlScoreAdjustmentAbs?: number
  avgMlExpectedReturn?: number
  avgMlExpectedReturnPositiveDays?: number
  avgMlExpectedReturnPositiveDayRate?: number
  avgMlProbability?: number
  avgMlBullishDays?: number
  avgMlBullishDayRate?: number
  avgMlSignalStrength?: number
  avgMlConfidenceScore?: number
  avgMlConfidenceMediumHighDayRate?: number
  diagnosis: string
}

export type BacktestBatchResponse = {
  taskId: string
  taskName: string
  status: string
  strategyName: string
  modelVersion: string
  fundCount: number
  successCount: number
  failedCount: number
  summary: BacktestSummary
  results: BacktestResult[]
  errors: Array<Record<string, unknown>>
}

export interface DashboardRiskAlert {
  id: string
  sourceType: string
  alertType: string
  riskLevel: RiskLevel | string
  title: string
  fundCode: string
  fundName?: string
  content: string
  alertTime: string
}

export interface StrategyConfig {
  id: number
  configName: string
  strategyType: string
  fundType?: string | null
  paramsJson: string
  enabled: boolean
  updateTime: string
}

export interface RiskProfile {
  id: number
  riskLevel: RiskLevel
  maxEquityPositionRate: number
  maxSingleFundPositionRate: number
  drawdownAlertRate: number
  dailyRiseAlertRate: number
  dailyFallAlertRate: number
  configJson: string
  updateTime: string
}

export type RiskProfileRequest = {
  riskLevel: RiskLevel
  maxEquityPositionRate: number
  maxSingleFundPositionRate: number
  drawdownAlertRate: number
  dailyRiseAlertRate: number
  dailyFallAlertRate: number
  configJson: string
}

export interface StrategyConfigRequest {
  configName: string
  strategyType: string
  fundType?: string | null
  paramsJson: string
  enabled: boolean
}

export interface AiAnalysisReport {
  id: number
  accountId: number
  holdingId: number
  fundCode: string
  fundName?: string
  modelName: string
  action: StrategyAction
  actionText: string
  suggestAmount: number
  suggestRatio: number
  confidence: number
  riskLevel: RiskLevel
  deadline: string
  strategy: string
  reasons: string[]
  risks: string[]
  dataSummary: string
  finalConclusion: string
  fallbackUsed: boolean
  analysisTime: string
  disclaimer: string
}

export interface DashboardOverview {
  summary: PortfolioSummary
  topHoldings: FundHolding[]
  positionDistribution: Array<{ name: string; rate: number }>
  profitTrend: Array<{ date: string; totalAsset: number; holdingProfit: number; dailyProfit: number; indexReturnRate?: number | null; profitStatus?: string; profitStatusText?: string }>
  latestStrategySignals: StrategySignal[]
  todayAiSuggestions: AiAnalysisReport[]
  estimateStatus: {
    trackedFundCount: number
    refreshedTodayCount: number
    delayedCount: number
    latestEstimateTime: string | null
    statusText: string
  }
  riskAlerts: DashboardRiskAlert[]
  riskAlertCount: number
  aiSuggestionCount: number
  disclaimer: string
}

export interface MarketIndex {
  code: string
  name: string
  latestPrice: number
  changeValue: number
  changeRate: number
  turnover: number
  updateTime: string
  sourceName: string
}

export interface MarketSessionStatus {
  primaryStatusText: string
  trading: boolean
  updateTime: string
  markets: Array<{
    market: string
    statusText: string
    trading: boolean
  }>
}

export interface ProfitAnalysis {
  startDate: string
  endDate: string
  todayProfit: number
  weekProfit: number
  monthProfit: number
  yearProfit: number
  totalProfit: number
  selectedRangeProfit: number
  selectedRangeProfitRate: number
  periodStats: Array<{ period: string; profit: number; profitRate: number }>
  trend: Array<{ date: string; totalAsset: number; dailyProfit: number; cumulativeProfit: number; dailyProfitRate: number; indexReturnRate?: number | null; profitStatus?: string; profitStatusText?: string }>
  profitTop5: FundProfitRank[]
  lossTop5: FundProfitRank[]
  indexCompare?: {
    indexCode: string
    indexName: string
    indexChangeRate: number
    selectedRangeProfitRate: number
    excessReturn: number
    updateTime: string | null
    sourceName: string
    statusText: string
    available: boolean
  }
  indexCompareStatus: string
  disclaimer: string
}

export interface ProfitCalendar {
  month: string
  monthlyProfit: number
  monthlyProfitRate: number
  days: Array<{
    date: string
    dailyProfit: number
    dailyProfitRate: number
    cumulativeProfit: number
    heatLevel: string
    tradingDay: boolean
    tradingDayLabel: string
    profitStatus?: string
    profitStatusText?: string
  }>
  profitTop5: FundProfitRank[]
  lossTop5: FundProfitRank[]
  disclaimer: string
}

export interface FundProfitRank {
  holdingId: number
  fundCode: string
  fundName: string
  holdingAmount: number
  holdingProfit: number
  holdingProfitRate: number
}

export interface TradeRecord {
  id: number
  accountId: number
  holdingId?: number
  fundCode: string
  fundName: string
  tradeType: 'BUY' | 'SELL' | 'REGULAR_INVEST' | 'CONVERT_IN' | 'CONVERT_OUT'
  tradeStatus: 'PROCESSING' | 'COMPLETED' | 'CANCELLED' | 'FAILED'
  tradeAmount: number
  tradeShare: number | null
  tradeNav: number | null
  tradeFee: number
  tradeTime: string
  remark?: string
  simulatedTradeNotice: string
}

export interface InvestmentPlan {
  id: number
  accountId: number
  fundCode: string
  fundName: string
  planName: string
  planType: string
  amount: number
  frequency: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'EVERY_TWO_WEEKS' | 'MONTHLY'
  nextExecuteDate: string
  status: 'ENABLED' | 'PAUSED'
  updateTime: string
}

export interface SchedulerTaskResult {
  successCount: number
  failureCount: number
  errorSummary: string
}

export type TradeRecordRequest = {
  accountId: number
  holdingId?: number
  fundCode: string
  fundName: string
  tradeType: TradeRecord['tradeType']
  tradeStatus?: TradeRecord['tradeStatus']
  tradeAmount: number
  tradeShare?: number
  tradeNav?: number
  tradeFee?: number
  tradeTime?: string
  relatedTradeId?: number
  remark?: string
}

export type InvestmentPlanRequest = {
  accountId: number
  fundCode: string
  fundName: string
  planName?: string
  amount: number
  frequency: InvestmentPlan['frequency']
  nextExecuteDate: string
  status?: InvestmentPlan['status']
}

export type ConvertPairTradeRequest = {
  accountId: number
  outHoldingId: number
  outTradeAmount: number
  outTradeShare?: number
  outTradeNav?: number
  outTradeFee?: number
  inHoldingId?: number
  inFundCode: string
  inFundName: string
  inTradeAmount: number
  inTradeShare?: number
  inTradeNav?: number
  inTradeFee?: number
  tradeStatus?: TradeRecord['tradeStatus']
  tradeTime?: string
  remark?: string
}

export interface FundEstimate {
  fundCode: string
  estimateDate: string
  estimateNav: number | null
  estimateGrowthRate: number | null
  estimateTime: string
  sourceName: string
  delayed: boolean
  rawPayload?: string
}

export interface FundBasicInfo {
  fundCode: string
  fundName: string
  fundType: string
  managerName?: string
  establishDate?: string
  riskLevel?: RiskLevel | string
  trackingIndex?: string
  activeFund?: boolean
}

export interface FundNavPoint {
  date: string
  nav: number
  accumulatedNav: number
  dailyGrowthRate: number
  indexReturnRate?: number | null
  indexCode?: string | null
  indexName?: string | null
}

export interface FundStockHolding {
  fundCode?: string
  stockCode: string
  stockName: string
  positionRate?: number
  holdingRatio?: number
  industry: string
  latestPrice?: number | null
  changeRate?: number | null
  marketSecId?: string
  reportDate?: string | null
  sourceName?: string
}

export interface FundTheme {
  fundCode?: string
  themeName: string
  themeType?: string
  weight: number
  estimatedRate?: number | null
  source?: string
  sourceName?: string
}

export interface FundPeerRank {
  fundCode?: string
  rankText?: string
  category?: string
  rank?: number
  total?: number
  percentile?: number
  period?: string
  sourceName?: string
}

export type FundScreenerRecommendLevel = 'STRONG' | 'WATCH' | 'NEUTRAL' | 'AVOID'

export type FundScreenerRankQuery = {
  fundType?: string
  period?: '60d' | '120d' | '250d'
  riskLevel?: string
  minScore?: number
  minFundSize?: number
  excludeShareClassC?: boolean
  onlyActiveFund?: boolean
  recommendLevel?: FundScreenerRecommendLevel | ''
  pageNo?: number
  pageSize?: number
  sortBy?: string
}

export interface FundScreenerRankItem {
  fundCode: string
  fundName: string
  fundType: string
  companyName?: string | null
  managerName?: string | null
  qualityScore: number
  returnScore: number
  riskScore: number
  stabilityScore: number
  excessScore: number
  peerScore: number
  liquidityScore?: number
  dataScore: number
  returnQualityScore?: number
  drawdownControlScore?: number
  consistencyScore?: number
  investabilityScore?: number
  rankNo?: number | null
  rankPercentile?: number | null
  recommendLevel: FundScreenerRecommendLevel
  return60d?: number | null
  return120d?: number | null
  return250d?: number | null
  maxDrawdown120d?: number | null
  volatility120d?: number | null
  peerPercentile?: number | null
  returnDrawdownRatio120d?: number | null
  returnConsistencyScore?: number | null
  benchmarkCode?: string | null
  scoreDate: string
  reasons: string[]
  risks: string[]
  disclaimer: string
}

export interface FundScreenerScoreBreakdown {
  returnScore: number
  riskScore: number
  stabilityScore: number
  excessScore: number
  peerScore: number
  liquidityScore: number
  dataScore: number
  returnQualityScore?: number
  drawdownControlScore?: number
  consistencyScore?: number
  investabilityScore?: number
}

export interface FundScreenerExplain {
  fundCode: string
  fundName?: string | null
  fundType?: string | null
  qualityScore?: number | null
  recommendLevel: FundScreenerRecommendLevel
  scoreBreakdown: FundScreenerScoreBreakdown
  factors: Record<string, unknown>
  reasons: string[]
  risks: string[]
  scoreDate?: string | null
  modelVersion: string
  disclaimer: string
}

export interface FundScreenerTaskResult {
  taskName: string
  status: string
  successCount: number
  failureCount: number
  skippedCount: number
  costTimeMs: number
  errorSummaries: string[]
  message: string
  finishTime: string
}

export type FundScreenerValidationStatus = 'EFFECTIVE' | 'NEUTRAL' | 'FAILED' | 'INSUFFICIENT'

export interface FundScreenerBacktestMetric {
  bucketName: 'TOP_5' | 'TOP_10' | 'WATCH' | 'NEUTRAL' | 'AVOID'
  horizonDays: 20 | 60 | 120
  sampleCount: number
  scoreDateCount: number
  avgForwardReturn: number
  winRate: number
  avgExcessReturn: number
  maxDrawdown: number
  statisticallySignificant: boolean
}

export interface FundScreenerStrategyPolicy {
  strongMinScore: number
  strongTopPercent: number
  watchMinScore: number
  watchTopPercent: number
  neutralMinScore: number
  minValidationSamples: number
  minValidationScoreDates: number
}

export interface FundScreenerValidation {
  latestRunDate: string | null
  earliestScoreDate: string | null
  latestScoreDate: string | null
  status: FundScreenerValidationStatus
  conclusion: string
  calibrationAdvice: string[]
  policy: FundScreenerStrategyPolicy
  metrics: FundScreenerBacktestMetric[]
}

export interface DataSourceConfig {
  id: number
  userId?: number
  sourceName: string
  baseUrl: string
  timeoutMs: number
  refreshIntervalSeconds: number
  rateLimitPerMinute: number
  enabled: boolean
  priority: number
  configJson: string
  userOverride: boolean
  updateTime: string
}

export interface DataSourceHealth {
  provider: string
  apiName: string
  healthy: boolean
  delayed: boolean
  lastCallTime: string | null
  lastSuccessTime: string | null
  lastFailureTime: string | null
  lastFailureReason: string | null
  lastCostTimeMs: number | null
  statusText: string
}

export interface AiRuntimeConfig {
  enabled: boolean
  provider: string
  model: string
  baseUrl: string
  keyPresent: boolean
  mockEnabled: boolean
  ready: boolean
  diagnosis: string
}

export type DataSourceConfigRequest = {
  sourceName: string
  baseUrl: string
  timeoutMs: number
  refreshIntervalSeconds: number
  rateLimitPerMinute: number
  enabled: boolean
  priority: number
  configJson: string
}

export interface PageResponse<T> {
  pageNo: number
  pageSize: number
  total: number
  records: T[]
}

export interface OperationLog {
  id: number
  userId: number
  module: string
  action: string
  bizType: string
  requestMethod: string
  requestUri: string
  requestParams?: string
  responseResult?: string
  ip: string
  userAgent: string
  success: boolean
  errorMessage?: string
  costTimeMs: number
  createTime: string
}

export interface ApiCallLog {
  id: number
  userId?: number
  provider: string
  apiName: string
  requestUrl: string
  requestMethod: string
  success: boolean
  statusCode?: number
  errorMessage?: string
  costTimeMs: number
  fallbackUsed: boolean
  callTime: string
}

export type OperationLogQuery = {
  pageNo?: number
  pageSize?: number
  module?: string
  success?: boolean
}

export type ApiCallLogQuery = {
  pageNo?: number
  pageSize?: number
  provider?: string
  success?: boolean
}
