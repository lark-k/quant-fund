import { http, USE_MOCK } from './http'
import { mockApi } from './mock'
import type {
  AiAnalysisReport,
  AiRuntimeConfig,
  BacktestBatchResponse,
  BacktestNavRefreshResponse,
  BacktestRunRequest,
  ClearHoldingRequest,
  DashboardOverview,
  DataSourceConfig,
  DataSourceConfigRequest,
  DataSourceHealth,
  ApiCallLog,
  ApiCallLogQuery,
  ConvertPairTradeRequest,
  FundBasicInfo,
  FundEstimate,
  FundHolding,
  FundNavPoint,
  FundPeerRank,
  FundSearchResult,
  FundSearchMode,
  FundStockHolding,
  FundTheme,
  HoldingCreateRequest,
  HoldingUpdateRequest,
  InvestmentPlan,
  InvestmentPlanRequest,
  MarketIndex,
  MarketSessionStatus,
  OperationLog,
  OperationLogQuery,
  PageResponse,
  PortfolioAccount,
  PortfolioAccountRequest,
  ProfitAnalysis,
  ProfitCalendar,
  QuantEngineHealth,
  QuantSignal,
  RiskProfile,
  RiskProfileRequest,
  StrategyConfig,
  StrategyConfigRequest,
  StrategySignal,
  TradeRecord,
  TradeRecordRequest
} from '@/types/domain'

export const quantApi = {
  dashboard(): Promise<DashboardOverview> {
    return USE_MOCK ? mockApi.dashboard() : http.get('/dashboard/overview')
  },
  marketReadings(): Promise<MarketIndex[]> {
    return USE_MOCK ? mockApi.marketReadings() : http.get('/dashboard/market-readings')
  },
  marketStatus(): Promise<MarketSessionStatus> {
    return USE_MOCK ? mockApi.marketStatus() : http.get('/dashboard/market-status')
  },
  holdings(): Promise<FundHolding[]> {
    return USE_MOCK ? mockApi.holdings() : http.get('/holdings')
  },
  portfolios(): Promise<PortfolioAccount[]> {
    return USE_MOCK ? mockApi.portfolios() : http.get('/portfolios')
  },
  createPortfolio(request: PortfolioAccountRequest): Promise<PortfolioAccount> {
    return USE_MOCK ? mockApi.createPortfolio(request) : http.post('/portfolios', request)
  },
  createHolding(request: HoldingCreateRequest): Promise<FundHolding> {
    return USE_MOCK ? mockApi.createHolding(request) : http.post('/holdings', request)
  },
  updateHolding(id: number, request: HoldingUpdateRequest): Promise<FundHolding> {
    return USE_MOCK ? mockApi.updateHolding(id, request) : http.put(`/holdings/${id}`, request)
  },
  deleteHolding(id: number): Promise<void> {
    return USE_MOCK ? mockApi.deleteHolding(id) : http.delete(`/holdings/${id}`)
  },
  clearHolding(id: number, request?: ClearHoldingRequest): Promise<FundHolding> {
    return USE_MOCK ? mockApi.clearHolding(id, request) : http.post(`/holdings/${id}/clear`, request || {})
  },
  recalculateHolding(id: number): Promise<FundHolding> {
    return USE_MOCK ? mockApi.recalculateHolding(id) : http.post(`/holdings/${id}/recalculate`)
  },
  syncOfficialNav(): Promise<FundHolding[]> {
    return USE_MOCK ? mockApi.syncOfficialNav() : http.post('/holdings/sync-official-nav', undefined, { suppressErrorMessage: true })
  },
  strategies(): Promise<StrategySignal[]> {
    return USE_MOCK ? mockApi.strategies() : http.get('/strategies/signals')
  },
  quantHealth(): Promise<QuantEngineHealth> {
    return USE_MOCK ? mockApi.quantHealth() : http.get('/quant/health')
  },
  quantSignals(params?: { accountId?: number; holdingId?: number; fundCode?: string; action?: string }): Promise<QuantSignal[]> {
    return USE_MOCK ? mockApi.quantSignals(params) : http.get('/quant/signals', { params, suppressErrorMessage: true })
  },
  analyzeQuantHolding(holdingId: number): Promise<QuantSignal> {
    return USE_MOCK ? mockApi.analyzeQuantHolding(holdingId) : http.post(`/quant/holdings/${holdingId}/analyze`, undefined, { timeout: 60000 })
  },
  analyzeQuantAccount(accountId: number): Promise<QuantSignal[]> {
    return USE_MOCK ? mockApi.analyzeQuantAccount(accountId) : http.post(`/quant/accounts/${accountId}/analyze`, undefined, { timeout: 90000, suppressErrorMessage: true })
  },
  runBacktest(request: BacktestRunRequest): Promise<BacktestBatchResponse> {
    return http.post('/backtests/run', request, { timeout: 360000 })
  },
  refreshBacktestNavCache(request: BacktestRunRequest): Promise<BacktestNavRefreshResponse> {
    return http.post('/backtests/nav-cache/refresh', request, { timeout: 360000 })
  },
  aiHistory(): Promise<AiAnalysisReport[]> {
    return USE_MOCK ? mockApi.aiHistory() : http.get('/ai-analysis/history')
  },
  profit(params?: { startDate?: string; endDate?: string; indexCode?: string }): Promise<ProfitAnalysis> {
    return USE_MOCK ? mockApi.profit() : http.get('/analytics/profit', { params })
  },
  profitIntraday(params?: { indexCode?: string }): Promise<Array<{ time: string; portfolioReturn: number | null; indexReturn: number | null; dailyProfit: number | null }>> {
    return USE_MOCK ? Promise.resolve([]) : http.get('/analytics/profit-intraday', { params })
  },
  calendar(params?: { month?: string }): Promise<ProfitCalendar> {
    return USE_MOCK ? mockApi.calendar() : http.get('/analytics/profit-calendar', { params })
  },
  trades(): Promise<TradeRecord[]> {
    return USE_MOCK ? mockApi.trades() : http.get('/trades')
  },
  dataSources(): Promise<DataSourceConfig[]> {
    return USE_MOCK ? mockApi.dataSources() : http.get('/system/data-sources')
  },
  dataSourceHealth(): Promise<DataSourceHealth[]> {
    return USE_MOCK ? mockApi.dataSourceHealth() : http.get('/system/data-source-health')
  },
  aiRuntimeConfig(): Promise<AiRuntimeConfig> {
    return USE_MOCK ? mockApi.aiRuntimeConfig() : http.get('/system/ai-runtime-config')
  },
  saveDataSource(request: DataSourceConfigRequest): Promise<DataSourceConfig> {
    return USE_MOCK ? mockApi.saveDataSource(request) : http.post('/system/data-sources', request)
  },
  updateDataSource(id: number, request: DataSourceConfigRequest): Promise<DataSourceConfig> {
    return USE_MOCK ? mockApi.updateDataSource(id, request) : http.put(`/system/data-sources/${id}`, request)
  },
  operationLogs(query: OperationLogQuery = {}): Promise<PageResponse<OperationLog>> {
    return USE_MOCK ? mockApi.operationLogs(query) : http.get('/system/operation-logs', { params: query })
  },
  apiCallLogs(query: ApiCallLogQuery = {}): Promise<PageResponse<ApiCallLog>> {
    return USE_MOCK ? mockApi.apiCallLogs(query) : http.get('/system/api-call-logs', { params: query })
  },
  strategyConfigs(): Promise<StrategyConfig[]> {
    return USE_MOCK ? mockApi.strategyConfigs() : http.get('/strategies/configs')
  },
  riskProfile(): Promise<RiskProfile> {
    return USE_MOCK ? mockApi.riskProfile() : http.get('/strategies/risk-profile')
  },
  saveRiskProfile(request: RiskProfileRequest): Promise<RiskProfile> {
    return USE_MOCK ? mockApi.saveRiskProfile(request) : http.put('/strategies/risk-profile', request)
  },
  saveStrategyConfig(request: StrategyConfigRequest): Promise<StrategyConfig> {
    return USE_MOCK ? mockApi.saveStrategyConfig(request) : http.put('/strategies/configs', request)
  },
  fundBasicInfo(fundCode: string): Promise<FundBasicInfo> {
    return USE_MOCK ? mockApi.fundBasicInfo(fundCode) : http.get(`/funds/${fundCode}`)
  },
  searchFunds(keyword: string, mode: FundSearchMode = 'FUZZY'): Promise<FundSearchResult[]> {
    return USE_MOCK ? mockApi.searchFunds(keyword, mode) : http.get('/funds/search', { params: { keyword, mode } })
  },
  async fundNav(fundCode: string, params?: { startDate?: string; endDate?: string; indexCode?: string }): Promise<FundNavPoint[]> {
    if (USE_MOCK) return mockApi.fundNav(fundCode)
    type RawFundNavPoint = FundNavPoint & {
      navDate?: string
      unitNav?: number
      sourceName?: string
    }
    const points = await http.get(`/funds/${fundCode}/nav`, { params }) as unknown as RawFundNavPoint[]
    return points.map((point) => ({
      ...point,
      date: point.date || point.navDate || '',
      nav: point.nav ?? point.unitNav ?? 0,
      accumulatedNav: point.accumulatedNav ?? 0,
      dailyGrowthRate: point.dailyGrowthRate ?? 0,
      indexReturnRate: point.indexReturnRate ?? null,
      indexCode: point.indexCode ?? null,
      indexName: point.indexName ?? null
    })).filter((point) => point.date && point.nav > 0)
  },
  fundEstimate(fundCode: string): Promise<FundEstimate> {
    return USE_MOCK
      ? mockApi.refreshEstimate(fundCode)
      : http.get(`/funds/${fundCode}/estimate`, { validateStatus: (status) => status < 500 })
  },
  heavyStocks(fundCode: string): Promise<FundStockHolding[]> {
    return USE_MOCK ? mockApi.heavyStocks(fundCode) : http.get(`/funds/${fundCode}/heavy-stocks`)
  },
  themes(fundCode: string): Promise<FundTheme[]> {
    return USE_MOCK ? mockApi.themes(fundCode) : http.get(`/funds/${fundCode}/themes`)
  },
  peerRank(fundCode: string): Promise<FundPeerRank> {
    return USE_MOCK ? mockApi.peerRank(fundCode) : http.get(`/funds/${fundCode}/peer-rank`)
  },
  refreshEstimate(fundCode: string): Promise<FundEstimate> {
    return USE_MOCK ? mockApi.refreshEstimate(fundCode) : http.post(`/funds/${fundCode}/refresh-estimate`)
  },
  generateAiAnalysis(holdingId: number): Promise<AiAnalysisReport> {
    return USE_MOCK ? mockApi.generateAiAnalysis(holdingId) : http.post(`/ai-analysis/holdings/${holdingId}`, undefined, { timeout: 90000 })
  },
  generateAiAccountAnalysis(accountId: number): Promise<AiAnalysisReport[]> {
    return USE_MOCK ? mockApi.generateAiAccountAnalysis(accountId) : http.post(`/ai-analysis/accounts/${accountId}`, undefined, { timeout: 120000, suppressErrorMessage: true })
  },
  createTrade(request: TradeRecordRequest): Promise<TradeRecord> {
    return USE_MOCK ? mockApi.createTrade(request) : http.post('/trades', request)
  },
  deleteTrade(id: number): Promise<void> {
    return USE_MOCK ? mockApi.deleteTrade(id) : http.delete(`/trades/${id}`)
  },
  createConvertPair(request: ConvertPairTradeRequest): Promise<TradeRecord[]> {
    return USE_MOCK ? mockApi.createConvertPair(request) : http.post('/trades/convert-pair', request)
  },
  settleDueTrades(): Promise<TradeRecord[]> {
    return USE_MOCK ? mockApi.trades() : http.post('/trades/settle-due')
  },
  investmentPlans(accountId?: number): Promise<InvestmentPlan[]> {
    return USE_MOCK ? Promise.resolve([]) : http.get('/investment-plans', { params: { accountId } })
  },
  createInvestmentPlan(request: InvestmentPlanRequest): Promise<InvestmentPlan> {
    return USE_MOCK ? Promise.resolve({
      id: Date.now(),
      accountId: request.accountId,
      fundCode: request.fundCode,
      fundName: request.fundName,
      planName: request.planName || `${request.fundName}定投`,
      planType: 'REGULAR_INVEST',
      amount: request.amount,
      frequency: request.frequency,
      nextExecuteDate: request.nextExecuteDate,
      status: request.status || 'ENABLED',
      updateTime: new Date().toISOString()
    }) : http.post('/investment-plans', request)
  },
  updateInvestmentPlan(id: number, request: InvestmentPlanRequest): Promise<InvestmentPlan> {
    return USE_MOCK ? Promise.resolve({
      id,
      accountId: request.accountId,
      fundCode: request.fundCode,
      fundName: request.fundName,
      planName: request.planName || `${request.fundName}定投`,
      planType: 'REGULAR_INVEST',
      amount: request.amount,
      frequency: request.frequency,
      nextExecuteDate: request.nextExecuteDate,
      status: request.status || 'ENABLED',
      updateTime: new Date().toISOString()
    }) : http.put(`/investment-plans/${id}`, request)
  },
  updateInvestmentPlanStatus(id: number, status: InvestmentPlan['status']): Promise<InvestmentPlan> {
    return USE_MOCK ? Promise.resolve({
      id,
      accountId: 1,
      fundCode: '',
      fundName: '',
      planName: '定投计划',
      planType: 'REGULAR_INVEST',
      amount: 0,
      frequency: 'WEEKLY',
      nextExecuteDate: new Date().toISOString().slice(0, 10),
      status,
      updateTime: new Date().toISOString()
    }) : http.put(`/investment-plans/${id}/status`, undefined, { params: { status } })
  },
  deleteInvestmentPlan(id: number): Promise<void> {
    return USE_MOCK ? Promise.resolve() : http.delete(`/investment-plans/${id}`)
  }
}
