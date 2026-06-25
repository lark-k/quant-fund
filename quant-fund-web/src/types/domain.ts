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
  tradeShare: number
  tradeNav: number
  tradeFee: number
  tradeTime: string
  remark?: string
  simulatedTradeNotice: string
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
