import type {
  AiAnalysisReport,
  AiRuntimeConfig,
  ApiCallLog,
  ApiCallLogQuery,
  ClearHoldingRequest,
  ConvertPairTradeRequest,
  DashboardRiskAlert,
  DashboardOverview,
  DataSourceConfig,
  DataSourceConfigRequest,
  DataSourceHealth,
  FundBasicInfo,
  FundHolding,
  FundNavPoint,
  FundPeerRank,
  FundScreenerExplain,
  FundScreenerRankItem,
  FundScreenerRankQuery,
  FundScreenerTaskResult,
  FundScreenerValidation,
  FundSearchResult,
  FundSearchMode,
  FundStockHolding,
  FundTheme,
  HoldingCreateRequest,
  HoldingUpdateRequest,
  LoginResult,
  MarketSessionStatus,
  OperationLog,
  OperationLogQuery,
  PageResponse,
  PasswordUpdateRequest,
  PortfolioAccount,
  PortfolioAccountRequest,
  ProfitAnalysis,
  ProfitCalendar,
  ProfileUpdateRequest,
  QuantEngineHealth,
  QuantSignal,
  RiskProfile,
  RiskProfileRequest,
  StrategyConfig,
  StrategyConfigRequest,
  StrategySignal,
  TradeRecord,
  TradeRecordRequest,
  UserProfile
} from '@/types/domain'
import { DISCLAIMER, SIMULATED_TRADE_NOTICE } from '@/types/domain'

type HoldingSeed = {
  fundCode: string
  fundName: string
  fundType: string
  holdingAmount: number
  estimateRate: number
  dailyRate: number
  watchFocus: boolean
}

type SignalSeed = {
  fundCode: string
  action: StrategySignal['action']
  actionText: string
  riskLevel: StrategySignal['riskLevel']
  reason: string
}

type TradeSeed = {
  tradeType: TradeRecord['tradeType']
  fundCode: string
  fundName: string
  tradeAmount: number
  tradeStatus: TradeRecord['tradeStatus']
}

export const mockUser: UserProfile = {
  id: 1,
  username: 'zhangming',
  nickname: '张明',
  role: 'USER',
  riskLevel: 'MEDIUM'
}

const padTime = (value: number) => String(value).padStart(2, '0')
const formatDate = (date: Date) => {
  return `${date.getFullYear()}-${padTime(date.getMonth() + 1)}-${padTime(date.getDate())}`
}
const today = formatDate(new Date())
const atTime = (hour: number, minute: number, second = 0) => `${today} ${padTime(hour)}:${padTime(minute)}:${padTime(second)}`
const now = atTime(14, 42, 15)
const dailyProfitByRate = (currentAmount: number, rate: number, baseAmount?: number) => {
  const originalAmount = baseAmount && baseAmount > 0 ? baseAmount : currentAmount / (1 + rate / 100)
  return originalAmount * rate / 100
}
const simulatedRemark = (value?: string) => {
  const remark = value?.trim()
  if (!remark) return SIMULATED_TRADE_NOTICE
  return remark.includes(SIMULATED_TRADE_NOTICE) ? remark : `${remark}，${SIMULATED_TRADE_NOTICE}`
}

export const marketSessionStatus: MarketSessionStatus = {
  primaryStatusText: 'A股交易中',
  trading: true,
  updateTime: now,
  markets: [
    { market: 'A股', statusText: 'A股交易中', trading: true },
    { market: '港股', statusText: '港股交易中', trading: true },
    { market: '美股', statusText: '海外市场参考/待海外收盘', trading: false }
  ]
}

const holdingSeeds: HoldingSeed[] = [
  { fundCode: '161725', fundName: '招商中证白酒指数A', fundType: '指数基金', holdingAmount: 872134.21, estimateRate: 1.12, dailyRate: 1.08, watchFocus: true },
  { fundCode: '110011', fundName: '易方达中小盘混合', fundType: '主动权益', holdingAmount: 856321.45, estimateRate: 0.86, dailyRate: 0.66, watchFocus: true },
  { fundCode: '007689', fundName: '国投瑞银新能源混合A', fundType: '主动权益', holdingAmount: 845231.76, estimateRate: 1.35, dailyRate: 1.32, watchFocus: true },
  { fundCode: '005827', fundName: '易方达蓝筹精选混合', fundType: '主动权益', holdingAmount: 768432.1, estimateRate: -0.27, dailyRate: -0.3, watchFocus: false },
  { fundCode: '161903', fundName: '万家行业优选混合(LOF)', fundType: '主动权益', holdingAmount: 654231.88, estimateRate: 0.42, dailyRate: 0.4, watchFocus: false },
  { fundCode: '004997', fundName: '广发高端制造股票A', fundType: '主动权益', holdingAmount: 612331.45, estimateRate: -0.58, dailyRate: -0.61, watchFocus: false },
  { fundCode: '006751', fundName: '富国科技创新混合A', fundType: '主动权益', holdingAmount: 598120.54, estimateRate: 0.91, dailyRate: 0.88, watchFocus: false },
  { fundCode: '000083', fundName: '汇添富消费行业混合', fundType: '主动权益', holdingAmount: 582134.67, estimateRate: 0.66, dailyRate: 0.63, watchFocus: false },
  { fundCode: '510300', fundName: '华泰柏瑞沪深300ETF', fundType: 'ETF', holdingAmount: 551234.12, estimateRate: -0.09, dailyRate: -0.1, watchFocus: false },
  { fundCode: '012345', fundName: '景顺长城量化精选', fundType: '指数增强', holdingAmount: 501938.45, estimateRate: 0.18, dailyRate: 0.16, watchFocus: false }
]

export const holdings: FundHolding[] = holdingSeeds.map((item, index) => ({
  id: index + 1,
  accountId: 1,
  fundCode: item.fundCode,
  fundName: item.fundName,
  fundType: item.fundType,
  activeFund: item.fundType === '主动权益',
  holdingAmount: item.holdingAmount,
  holdingShare: Math.round(item.holdingAmount / 1.27),
  holdingCost: item.holdingAmount * (1 - item.dailyRate / 100),
  currentEstimateNav: 0.8721 + index * 0.238,
  latestOfficialNav: 0.8668 + index * 0.236,
  holdingProfit: item.holdingAmount * (item.dailyRate / 9),
  holdingProfitRate: item.dailyRate * 12.5,
  dailyProfit: dailyProfitByRate(item.holdingAmount, item.estimateRate),
  yesterdayProfit: dailyProfitByRate(item.holdingAmount, item.dailyRate),
  positionRate: 0,
  currentEstimateGrowthRate: item.estimateRate,
  officialNavUpdated: false,
  officialNavDate: null,
  relatedThemeName: item.fundName.includes('白酒') ? '白酒' : item.fundName.includes('新能源') ? '新能源' : item.fundName.includes('全球') ? '海外基金' : '重仓板块待同步',
  relatedThemeRate: item.estimateRate,
  valuationSource: 'MOCK',
  estimateBasis: '重仓股占比加权估算',
  marketStatus: 'A股交易中',
  holdingDays: 180 + index * 21,
  sourcePlatform: index % 2 === 0 ? '支付宝' : '天天基金',
  regularInvestment: index % 3 === 0,
  coreHolding: index < 4,
  watchFocus: item.watchFocus,
  updateTime: now,
  disclaimer: DISCLAIMER
}))

holdings.forEach((holding) => {
  const total = holdings.reduce((sum, item) => sum + item.holdingAmount, 0)
  holding.positionRate = total > 0 ? holding.holdingAmount / total * 100 : 0
})

export const portfolios: PortfolioAccount[] = [
  {
    id: 1,
    accountName: '手动基金账户',
    platformType: 'MANUAL',
    totalAsset: holdings.reduce((total, item) => total + item.holdingAmount, 0),
    totalInvestAmount: holdings.reduce((total, item) => total + item.holdingCost, 0),
    currentProfit: holdings.reduce((total, item) => total + item.holdingProfit, 0),
    currentProfitRate: 8.64,
    dailyProfit: holdings.reduce((total, item) => total + item.dailyProfit, 0),
    cashPositionRate: 8.2,
    equityPositionRate: 76.4,
    bondPositionRate: 15.4,
    maxSingleFundPositionRate: 25,
    status: 'ACTIVE',
    updateTime: now
  }
]

const fundSearchPool: FundSearchResult[] = [
  ...holdingSeeds.map((item) => ({
    fundCode: item.fundCode,
    fundName: item.fundName,
    fundType: item.fundType,
    pinyin: item.fundName,
    sourceName: 'MOCK_FALLBACK'
  })),
  { fundCode: '000001', fundName: '华夏成长混合', fundType: 'MIXED', pinyin: 'HXCC', sourceName: 'MOCK_FALLBACK' },
  { fundCode: '110022', fundName: '易方达消费行业股票', fundType: 'ACTIVE_EQUITY', pinyin: 'YFDXFHYGP', sourceName: 'MOCK_FALLBACK' },
  { fundCode: '000300', fundName: '沪深300ETF联接A', fundType: 'ETF_LINK', pinyin: 'HS300ETFLJA', sourceName: 'MOCK_FALLBACK' },
  { fundCode: '040008', fundName: '华安策略优选混合A', fundType: 'MIXED', pinyin: 'HACLYXHHA', sourceName: 'MOCK_FALLBACK' }
]

const signalSeeds: SignalSeed[] = [
  { fundCode: '007689', action: 'BUY', actionText: '建议小额加仓', riskLevel: 'HIGH', reason: '行业轮动强度提升，近 5 日估值走强。' },
  { fundCode: '005827', action: 'SELL', actionText: '建议轻度减仓', riskLevel: 'MEDIUM', reason: '回撤接近预警阈值，建议降低单基金波动贡献。' },
  { fundCode: '161725', action: 'WATCH', actionText: '建议观察', riskLevel: 'LOW', reason: '波动放大但长期趋势未破坏，等待正式净值确认。' },
  { fundCode: '510300', action: 'HOLD', actionText: '建议持有', riskLevel: 'LOW', reason: '权益仓位接近上限，暂不追涨。' },
  { fundCode: '006751', action: 'BUY', actionText: '建议分批低吸', riskLevel: 'MEDIUM', reason: '估值回落且趋势仍保持，适合小额分批。' }
]

export const strategySignals: StrategySignal[] = signalSeeds.map((item, index) => ({
  id: index + 1,
  accountId: 1,
  holdingId: index + 1,
  fundCode: item.fundCode,
  signalType: 'POSITION_MONITOR',
  action: item.action,
  actionText: item.actionText,
  suggestAmount: item.action === 'BUY' ? 12000 : item.action === 'SELL' ? 28000 : 0,
  suggestRatio: item.action === 'WATCH' ? 0 : 5 + index,
  riskLevel: item.riskLevel,
  confidence: 0.62 + index * 0.06,
  reasons: [item.reason, '估值数据仅供盘中参考，正式净值以晚间更新为准。'],
  signalTime: atTime(9, 41 - index * 4),
  disclaimer: DISCLAIMER
}))

function toQuantSignal(holding: FundHolding, index = 0): QuantSignal {
  const strongOpportunity = holding.currentEstimateGrowthRate > 0.8 && holding.positionRate < 18
  const weakRisk = holding.currentEstimateGrowthRate < -0.5 || holding.positionRate > 20
  const action: QuantSignal['action'] = weakRisk ? 'WATCH' : strongOpportunity ? 'BUY' : 'HOLD'
  const totalScore = weakRisk ? 52 - index : strongOpportunity ? 82 - index : 66 - index
  const suggestRatio = action === 'BUY' ? Math.min(5, Math.max(0, 25 - holding.positionRate)) : 0
  return {
    id: 9000 + holding.id,
    accountId: holding.accountId,
    holdingId: holding.id,
    fundCode: holding.fundCode,
    fundName: holding.fundName,
    action,
    actionText: action === 'BUY' ? '建议小额加仓' : action === 'WATCH' ? '建议重点观察' : '建议持有观察',
    suggestAmount: action === 'BUY' ? holding.holdingAmount * suggestRatio / 100 : 0,
    suggestRatio,
    riskLevel: weakRisk ? 'MEDIUM' : strongOpportunity ? 'HIGH' : 'LOW',
    confidence: action === 'BUY' ? 0.78 : 0.62,
    totalScore,
    trendScore: totalScore + 3,
    opportunityScore: action === 'BUY' ? 84 : 58,
    riskScore: weakRisk ? 48 : 76,
    positionScore: Math.max(35, 92 - holding.positionRate * 2),
    momentumScore: 50 + holding.currentEstimateGrowthRate * 8,
    reasons: [
      `规则多因子评分 ${totalScore.toFixed(2)}，结合趋势、机会、风险、仓位和动量生成。`,
      `当前估值 ${holding.currentEstimateGrowthRate.toFixed(2)}%，单基金仓位 ${holding.positionRate.toFixed(2)}%。`
    ],
    risks: ['盘中估值不是最终净值，建议结合 15:00 前平台规则自行确认。'],
    metricsJson: JSON.stringify({
      estimateGrowthRate: holding.currentEstimateGrowthRate,
      positionRate: holding.positionRate,
      holdingProfitRate: holding.holdingProfitRate
    }),
    modelName: 'QuantRuleEngine',
    modelVersion: 'rule-v1.39.0',
    deadline: atTime(15, 0),
    signalTime: atTime(14, 50 - index),
    fallbackUsed: false,
    disclaimer: DISCLAIMER
  }
}

export const quantSignals: QuantSignal[] = holdings.slice(0, 6).map(toQuantSignal)

export const aiSuggestions: AiAnalysisReport[] = [
  {
    id: 1,
    accountId: 1,
    holdingId: 3,
    fundCode: '007689',
    modelName: 'deepseek-v4-flash',
    action: 'BUY',
    actionText: '建议小额加仓',
    suggestAmount: 15000,
    suggestRatio: 3,
    confidence: 0.78,
    riskLevel: 'HIGH',
    deadline: '15:00前',
    strategy: '回撤低吸 + 行业动量',
    reasons: ['新能源板块估值修复，资金流入增强。', '账户权益仓位仍在可控区间。'],
    risks: ['近 5 日波动较快，不适合一次性大额加仓。'],
    dataSummary: '当前估值上涨 1.35%，持仓占比 12.34%。',
    finalConclusion: '可小额分批，严格控制仓位。',
    fallbackUsed: false,
    analysisTime: now,
    disclaimer: DISCLAIMER
  },
  {
    id: 2,
    accountId: 1,
    holdingId: 4,
    fundCode: '005827',
    modelName: 'deepseek-v4-flash',
    action: 'SELL',
    actionText: '建议轻度减仓',
    suggestAmount: 25000,
    suggestRatio: 4,
    confidence: 0.65,
    riskLevel: 'MEDIUM',
    deadline: '15:00前',
    strategy: '回撤止盈法',
    reasons: ['组合回撤扩大，估值偏弱。', '建议降低单基金波动贡献。'],
    risks: ['若正式净值反转，减仓可能降低后续收益。'],
    dataSummary: '今日估值 -0.27%，阶段回撤接近预警。',
    finalConclusion: '轻度减仓或继续观察均可。',
    fallbackUsed: false,
    analysisTime: atTime(9, 18),
    disclaimer: DISCLAIMER
  }
]

const AI_HISTORY_LIMIT = 5

function pruneAiHistory(holdingId?: number) {
  const scopedReports = holdingId == null ? aiSuggestions : aiSuggestions.filter((report) => report.holdingId === holdingId)
  scopedReports.sort((left, right) => {
    const timeDiff = new Date(right.analysisTime).getTime() - new Date(left.analysisTime).getTime()
    if (timeDiff !== 0) return timeDiff
    return right.id - left.id
  })
  const expiredIds = new Set(scopedReports.slice(AI_HISTORY_LIMIT).map((report) => report.id))
  if (!expiredIds.size) {
    return
  }
  for (let index = aiSuggestions.length - 1; index >= 0; index -= 1) {
    if (expiredIds.has(aiSuggestions[index].id)) {
      aiSuggestions.splice(index, 1)
    }
  }
}

function latestAiSuggestions(items: AiAnalysisReport[]) {
  const latestByHolding = new Map<number, AiAnalysisReport>()
  for (const item of [...items].sort((left, right) => {
    const timeDiff = new Date(right.analysisTime).getTime() - new Date(left.analysisTime).getTime()
    if (timeDiff !== 0) return timeDiff
    return right.id - left.id
  })) {
    if (!latestByHolding.has(item.holdingId)) {
      latestByHolding.set(item.holdingId, item)
    }
  }
  return Array.from(latestByHolding.values())
}

const rawRiskAlerts: DashboardRiskAlert[] = [
  ...strategySignals
    .filter((item) => item.riskLevel === 'HIGH' || item.action === 'SELL' || item.action === 'CONVERT')
    .slice(0, 4)
    .map((item) => {
      const holding = holdings.find((holdingItem) => holdingItem.fundCode === item.fundCode)
      return {
        id: `SIGNAL_${item.id}`,
        sourceType: 'STRATEGY',
        alertType: item.signalType,
        riskLevel: item.riskLevel,
        title: `${item.riskLevel === 'HIGH' ? '高' : item.riskLevel === 'MEDIUM' ? '中' : '低'} · 策略预警`,
        fundCode: item.fundCode,
        fundName: holding?.fundName || item.fundCode,
        content: item.reasons[0],
        alertTime: item.signalTime
      }
    }),
  ...aiSuggestions
    .filter((item) => item.risks.length && item.riskLevel !== 'LOW')
    .slice(0, 3)
    .map((item) => {
      const holding = holdings.find((holdingItem) => holdingItem.fundCode === item.fundCode)
      return {
        id: `AI_${item.id}`,
        sourceType: 'AI',
        alertType: 'AI 风险',
        riskLevel: item.riskLevel,
        title: `${item.riskLevel === 'HIGH' ? '高' : '中'} · AI 风险`,
        fundCode: item.fundCode,
        fundName: holding?.fundName || item.fundCode,
        content: item.risks[0],
        alertTime: item.analysisTime
      }
    })
].sort((left, right) => Date.parse(right.alertTime) - Date.parse(left.alertTime))

function aggregateRiskAlerts(alerts: DashboardRiskAlert[]): DashboardRiskAlert[] {
  const groups = new Map<string, DashboardRiskAlert[]>()
  for (const alert of alerts) {
    const key = alert.fundCode || alert.id
    groups.set(key, [...(groups.get(key) || []), alert])
  }
  return Array.from(groups.values())
    .map((items) => {
      const latest = [...items].sort((left, right) => Date.parse(right.alertTime) - Date.parse(left.alertTime))[0]
      const riskLevel = [...items].sort((left, right) => riskRank(right.riskLevel) - riskRank(left.riskLevel))[0].riskLevel
      const alertTypes = Array.from(new Set(items.map((item) => item.alertType).filter(Boolean)))
      const contents = Array.from(new Set(items.map((item) => `${item.alertType}：${item.content}`)))
      return {
        ...latest,
        id: `RISK_${latest.fundCode}`,
        sourceType: new Set(items.map((item) => item.sourceType)).size > 1 ? 'MIXED' : latest.sourceType,
        alertType: joinLimited(alertTypes, 3),
        riskLevel,
        title: `${riskLevel === 'HIGH' ? '高' : riskLevel === 'MEDIUM' ? '中' : '低'} · ${alertTypes.length > 1 ? '综合风险' : joinLimited(alertTypes, 3)}`,
        content: `${joinLimited(contents, 3)}${contents.length > 3 ? `；等 ${contents.length} 项风险` : ''}`
      }
    })
    .sort((left, right) => Date.parse(right.alertTime) - Date.parse(left.alertTime))
}

function joinLimited(values: string[], limit: number) {
  const head = values.slice(0, limit).join(' / ')
  return values.length > limit ? `${head} / +${values.length - limit}` : head
}

function riskRank(level: string) {
  return level === 'HIGH' ? 3 : level === 'MEDIUM' ? 2 : level === 'LOW' ? 1 : 0
}

export const riskAlerts: DashboardRiskAlert[] = aggregateRiskAlerts(rawRiskAlerts)

const trend = Array.from({ length: 18 }, (_, index) => {
  const monthIndex = index + 1
  const year = monthIndex > 12 ? 2026 : 2025
  const month = monthIndex > 12 ? monthIndex - 12 : monthIndex
  return {
    date: `${year}-${String(month).padStart(2, '0')}-23`,
    totalAsset: 5700000 + index * 93000,
    holdingProfit: 300000 + Math.sin(index / 1.4) * 130000 + Math.cos(index / 2.3) * 42000 + index * 46000,
    dailyProfit: (Math.sin(index * 1.9) + 0.4) * 18000,
    profitStatus: index === 17 ? 'ESTIMATED' : 'CONFIRMED',
    profitStatusText: index === 17 ? '盘中预估，待正式净值确认' : '正式净值已确认'
  }
})

export const dashboard: DashboardOverview = {
  summary: {
    totalAsset: 6842713.23,
    totalInvestAmount: 6000000,
    currentProfit: 842713.23,
    currentProfitRate: 14.03,
    dailyProfit: 25864.32,
    equityPositionRate: 78.65,
    bondPositionRate: 9.72,
    cashPositionRate: 11.63,
    holdingCount: 10
  },
  topHoldings: holdings,
  positionDistribution: [
    { name: '股票型基金', rate: 48.21 },
    { name: '混合型基金', rate: 30.44 },
    { name: '指数型基金', rate: 12.36 },
    { name: '债券/现金', rate: 8.99 }
  ],
  profitTrend: trend,
  latestStrategySignals: strategySignals,
  todayAiSuggestions: latestAiSuggestions(aiSuggestions),
  estimateStatus: {
    trackedFundCount: 10,
    refreshedTodayCount: 10,
    delayedCount: 0,
    latestEstimateTime: now,
    statusText: '估值正常'
  },
  riskAlerts,
  riskAlertCount: riskAlerts.length,
  aiSuggestionCount: latestAiSuggestions(aiSuggestions).length,
  disclaimer: DISCLAIMER
}

function buildDashboard(): DashboardOverview {
  const totalAsset = holdings.reduce((total, item) => total + item.holdingAmount, 0)
  const totalInvestAmount = holdings.reduce((total, item) => total + item.holdingCost, 0)
  const currentProfit = holdings.reduce((total, item) => total + item.holdingProfit, 0)
  const dailyProfit = holdings.reduce((total, item) => total + item.dailyProfit, 0)
  const officialUpdatedCount = holdings.filter((item) => item.officialNavUpdated).length
  const visibleAiSuggestions = latestAiSuggestions(aiSuggestions)
  return {
    ...dashboard,
    summary: {
      ...dashboard.summary,
      totalAsset,
      totalInvestAmount,
      currentProfit,
      currentProfitRate: totalInvestAmount ? currentProfit / totalInvestAmount * 100 : 0,
      dailyProfit,
      holdingCount: holdings.length
    },
    topHoldings: holdings,
    estimateStatus: {
      ...dashboard.estimateStatus,
      trackedFundCount: holdings.length,
      refreshedTodayCount: officialUpdatedCount || dashboard.estimateStatus.refreshedTodayCount,
      latestEstimateTime: now,
      statusText: officialUpdatedCount === holdings.length ? '今日正式净值已同步' : '估值正常'
    },
    todayAiSuggestions: visibleAiSuggestions,
    aiSuggestionCount: visibleAiSuggestions.length
  }
}

export const profitAnalysis: ProfitAnalysis = {
  startDate: '2025-06-23',
  endDate: today,
  todayProfit: 25864.32,
  weekProfit: 78231.16,
  monthProfit: 154892.43,
  yearProfit: 461882.28,
  totalProfit: 842713.23,
  selectedRangeProfit: 486900.12,
  selectedRangeProfitRate: 8.12,
  periodStats: [
    { period: 'TODAY', profit: 25864.32, profitRate: 0.38 },
    { period: 'THIS_WEEK', profit: 78231.16, profitRate: 1.12 },
    { period: 'THIS_MONTH', profit: 154892.43, profitRate: 2.41 },
    { period: 'THIS_YEAR', profit: 461882.28, profitRate: 7.86 },
    { period: 'ALL', profit: 842713.23, profitRate: 14.03 }
  ],
  trend: trend.map((point, index) => ({
    date: point.date,
    totalAsset: point.totalAsset,
    dailyProfit: point.dailyProfit,
    cumulativeProfit: 300000 + index * 42000,
    dailyProfitRate: point.dailyProfit / point.totalAsset * 100,
    profitStatus: index === trend.length - 1 ? 'ESTIMATED' : 'CONFIRMED',
    profitStatusText: index === trend.length - 1 ? '盘中预估，待正式净值确认' : '正式净值已确认'
  })),
  profitTop5: holdings.map(toRank).filter((item) => item.holdingProfit > 0).slice(0, 5),
  lossTop5: holdings.map(toRank).filter((item) => item.holdingProfit < 0).slice(0, 5),
  indexCompare: {
    indexCode: '000300',
    indexName: '沪深300',
    indexChangeRate: 1.68,
    selectedRangeProfitRate: 8.12,
    excessReturn: 6.44,
    updateTime: atTime(15, 0),
    sourceName: 'MOCK_FALLBACK',
    statusText: '组合区间收益率 8.12%，沪深300实时涨跌幅 1.68%，跑赢 6.44%',
    available: true
  },
  indexCompareStatus: '组合区间收益率 8.12%，沪深300实时涨跌幅 1.68%，跑赢 6.44%',
  disclaimer: DISCLAIMER
}

function buildProfitAnalysis(): ProfitAnalysis {
  const summary = buildDashboard().summary
  const totalAsset = summary.totalAsset || 0
  const dailyProfit = summary.dailyProfit || 0
  const periodRate = (profit: number) => totalAsset > 0 ? profit / totalAsset * 100 : 0
  const totalProfit = summary.currentProfit || 0
  const totalProfitRate = summary.currentProfitRate || 0
  const weekProfit = dailyProfit
  const monthProfit = dailyProfit
  const yearProfit = dailyProfit
  return {
    ...profitAnalysis,
    todayProfit: dailyProfit,
    weekProfit,
    monthProfit,
    yearProfit,
    totalProfit,
    selectedRangeProfit: monthProfit,
    selectedRangeProfitRate: periodRate(monthProfit),
    periodStats: [
      { period: 'TODAY', profit: dailyProfit, profitRate: periodRate(dailyProfit) },
      { period: 'THIS_WEEK', profit: weekProfit, profitRate: periodRate(weekProfit) },
      { period: 'THIS_MONTH', profit: monthProfit, profitRate: periodRate(monthProfit) },
      { period: 'THIS_YEAR', profit: yearProfit, profitRate: periodRate(yearProfit) },
      { period: 'ALL', profit: totalProfit, profitRate: totalProfitRate }
    ],
    profitTop5: holdings.map(toRank).filter((item) => item.holdingProfit > 0).slice(0, 5),
    lossTop5: holdings.map(toRank).filter((item) => item.holdingProfit < 0).slice(0, 5)
  }
}

export const profitCalendar: ProfitCalendar = {
  month: '2026-06',
  monthlyProfit: 154892.43,
  monthlyProfitRate: 2.41,
  days: Array.from({ length: 30 }, (_, index) => {
    const date = `2026-06-${String(index + 1).padStart(2, '0')}`
    const weekday = new Date(`${date}T00:00:00`).getDay()
    const tradingDay = weekday !== 0 && weekday !== 6 && date !== '2026-06-19'
    const profit = Math.round((Math.sin(index * 1.73) + 0.2) * 6200)
    return {
      date,
      dailyProfit: tradingDay ? profit : 0,
      dailyProfitRate: tradingDay ? profit / 6800000 * 100 : 0,
      cumulativeProfit: 400000 + index * 5700 + (tradingDay ? profit : 0),
      heatLevel: tradingDay ? (profit > 6500 ? 'STRONG_PROFIT' : profit > 0 ? 'PROFIT' : profit < -6500 ? 'STRONG_LOSS' : profit < 0 ? 'LOSS' : 'FLAT') : 'NON_TRADING',
      tradingDay,
      tradingDayLabel: tradingDay ? '交易日' : '非交易日',
      profitStatus: tradingDay ? (index === 29 ? 'ESTIMATED' : 'CONFIRMED') : 'NON_TRADING',
      profitStatusText: tradingDay ? (index === 29 ? '盘中预估，待正式净值确认' : '正式净值已确认') : '休市/非交易日'
    }
  }),
  profitTop5: holdings.map(toRank).filter((item) => item.holdingProfit > 0).slice(0, 5),
  lossTop5: holdings.map(toRank).filter((item) => item.holdingProfit < 0).slice(0, 5),
  disclaimer: DISCLAIMER
}

function buildProfitCalendar(): ProfitCalendar {
  const summary = buildDashboard().summary
  const monthlyProfit = summary.dailyProfit || 0
  const monthlyProfitRate = summary.totalAsset > 0 ? monthlyProfit / summary.totalAsset * 100 : 0
  return {
    ...profitCalendar,
    monthlyProfit,
    monthlyProfitRate
  }
}

const tradeSeeds: TradeSeed[] = [
  { tradeType: 'BUY', fundCode: '007689', fundName: '国投瑞银新能源混合A', tradeAmount: 15000, tradeStatus: 'COMPLETED' },
  { tradeType: 'SELL', fundCode: '005827', fundName: '易方达蓝筹精选混合', tradeAmount: 25000, tradeStatus: 'PROCESSING' },
  { tradeType: 'REGULAR_INVEST', fundCode: '161725', fundName: '招商中证白酒指数A', tradeAmount: 3000, tradeStatus: 'COMPLETED' },
  { tradeType: 'CONVERT_OUT', fundCode: '510300', fundName: '华泰柏瑞沪深300ETF', tradeAmount: 12000, tradeStatus: 'COMPLETED' }
]

export const trades: TradeRecord[] = tradeSeeds.map((item, index) => ({
  id: index + 1,
  accountId: 1,
  holdingId: index + 1,
  fundCode: item.fundCode,
  fundName: item.fundName,
  tradeType: item.tradeType,
  tradeStatus: item.tradeStatus,
  tradeAmount: item.tradeAmount,
  tradeShare: Math.round(item.tradeAmount / 1.2),
  tradeNav: 1.2134 + index * 0.2,
  tradeFee: 1.5,
  tradeTime: atTime(9 + index, 30),
  remark: '由策略和 AI 建议辅助生成，用户自行确认后记录。',
  simulatedTradeNotice: SIMULATED_TRADE_NOTICE
}))

export const dataSources: DataSourceConfig[] = [
  {
    id: 1,
    sourceName: 'EAST_MONEY',
    baseUrl: 'https://api.fund.eastmoney.com',
    timeoutMs: 5000,
    refreshIntervalSeconds: 120,
    rateLimitPerMinute: 60,
    enabled: true,
    priority: 10,
    configJson: '{}',
    userOverride: false,
    updateTime: now
  },
  {
    id: 2,
    userId: 1,
    sourceName: 'MOCK_FALLBACK',
    baseUrl: 'local://mock',
    timeoutMs: 3000,
    refreshIntervalSeconds: 120,
    rateLimitPerMinute: 600,
    enabled: true,
    priority: 99,
    configJson: '{"fallback":true}',
    userOverride: true,
    updateTime: now
  }
]

export const dataSourceHealth: DataSourceHealth[] = [
  {
    provider: 'EAST_MONEY',
    apiName: 'FUND_ESTIMATE',
    healthy: true,
    delayed: false,
    lastCallTime: now,
    lastSuccessTime: now,
    lastFailureTime: null,
    lastFailureReason: null,
    lastCostTimeMs: 138,
    statusText: '最近调用成功'
  },
  {
    provider: 'MOCK_FALLBACK',
    apiName: 'FUND_NAV_DAILY',
    healthy: false,
    delayed: true,
    lastCallTime: atTime(14, 31, 44),
    lastSuccessTime: atTime(14, 31, 44),
    lastFailureTime: null,
    lastFailureReason: null,
    lastCostTimeMs: 8,
    statusText: '最近调用使用降级数据'
  }
]

export const aiRuntimeConfig: AiRuntimeConfig = {
  enabled: true,
  provider: 'deepseek',
  model: 'deepseek-v4-flash',
  baseUrl: 'local://mock',
  keyPresent: false,
  mockEnabled: true,
  ready: false,
  diagnosis: 'DEEPSEEK_MOCK_ENABLED=true，当前前端使用本地 mock 数据'
}

export const operationLogs: OperationLog[] = [
  {
    id: 1,
    userId: 1,
    module: 'auth',
    action: 'login',
    bizType: 'USER_AUTH',
    requestMethod: 'POST',
    requestUri: '/api/auth/login',
    requestParams: '{"username":"zhangming","password":"***"}',
    ip: '127.0.0.1',
    userAgent: 'QuantFund mock browser',
    success: true,
    costTimeMs: 86,
    createTime: now
  },
  {
    id: 2,
    userId: 1,
    module: 'system',
    action: 'update_data_source',
    bizType: 'DATA_SOURCE_CONFIG',
    requestMethod: 'PUT',
    requestUri: '/api/system/data-sources/1',
    requestParams: '{"sourceName":"EAST_MONEY","timeoutMs":5000}',
    ip: '127.0.0.1',
    userAgent: 'QuantFund mock browser',
    success: true,
    costTimeMs: 42,
    createTime: atTime(14, 35, 18)
  },
  {
    id: 3,
    userId: 1,
    module: 'strategy',
    action: 'save_config',
    bizType: 'STRATEGY_CONFIG',
    requestMethod: 'PUT',
    requestUri: '/api/strategies/configs',
    requestParams: '{"strategyType":"POSITION_MONITOR"}',
    ip: '127.0.0.1',
    userAgent: 'QuantFund mock browser',
    success: true,
    costTimeMs: 55,
    createTime: atTime(14, 18, 6)
  }
]

export const apiCallLogs: ApiCallLog[] = [
  {
    id: 1,
    userId: 1,
    provider: 'EAST_MONEY',
    apiName: 'FUND_ESTIMATE',
    requestUrl: 'https://fundgz.1234567.com.cn/js/161725.js',
    requestMethod: 'GET',
    success: true,
    statusCode: 200,
    costTimeMs: 138,
    fallbackUsed: false,
    callTime: now
  },
  {
    id: 2,
    provider: 'MOCK_FALLBACK',
    apiName: 'FUND_NAV_DAILY',
    requestUrl: 'local://mock/nav/007689',
    requestMethod: 'GET',
    success: true,
    statusCode: 200,
    costTimeMs: 8,
    fallbackUsed: true,
    callTime: atTime(14, 31, 44)
  },
  {
    id: 3,
    userId: 1,
    provider: 'DEEPSEEK',
    apiName: 'AI_ANALYSIS',
    requestUrl: 'mock://deepseek/analysis',
    requestMethod: 'POST',
    success: false,
    statusCode: 0,
    errorMessage: 'AI 未启用，使用本地兜底建议',
    costTimeMs: 4,
    fallbackUsed: true,
    callTime: atTime(14, 30, 2)
  }
]

export const strategyConfigs: StrategyConfig[] = [
  {
    id: 1,
    configName: '主动基金回撤止盈',
    strategyType: 'DRAWDOWN_STOP_PROFIT',
    fundType: 'ACTIVE_EQUITY',
    paramsJson: '{"lightDrawdownPct":3,"mediumDrawdownPct":5,"heavyDrawdownPct":8,"profitActivationPct":15}',
    enabled: true,
    updateTime: now
  },
  {
    id: 2,
    configName: '动态梯度止盈',
    strategyType: 'DYNAMIC_LADDER_STOP_PROFIT',
    fundType: 'ACTIVE_EQUITY',
    paramsJson: '{"ladder":[{"profitPct":10,"sellRatio":10},{"profitPct":20,"sellRatio":20},{"profitPct":30,"sellRatio":30},{"profitPct":50,"sellRatio":40}]}',
    enabled: true,
    updateTime: now
  },
  {
    id: 3,
    configName: '权益仓位监控',
    strategyType: 'POSITION_MONITOR',
    fundType: null,
    paramsJson: '{"equityLimitPct":70,"singleFundLimitPct":25,"largeRisePct":2}',
    enabled: true,
    updateTime: now
  }
]

export const fundScreenerRankItems: FundScreenerRankItem[] = [
  {
    fundCode: '110011',
    fundName: '易方达中小盘混合',
    fundType: 'MIXED',
    companyName: '易方达基金',
    managerName: '张坤',
    qualityScore: 88.6,
    returnScore: 91.2,
    riskScore: 80.5,
    stabilityScore: 84.1,
    excessScore: 86.4,
    peerScore: 78.8,
    liquidityScore: 82.0,
    dataScore: 96.0,
    rankNo: 1,
    rankPercentile: 3.2,
    recommendLevel: 'STRONG',
    return60d: 8.2,
    return120d: 14.8,
    return250d: 27.6,
    maxDrawdown120d: -8.9,
    volatility120d: 17.2,
    peerPercentile: 3.2,
    scoreDate: today,
    reasons: ['近120日收益处于同类前列', '回撤控制优于同类中位数', '净值样本充足，评分可信度较高'],
    risks: ['近期涨幅较高，短线追高风险上升', '基金优选结果仅供参考，不构成投资建议'],
    disclaimer: DISCLAIMER
  },
  {
    fundCode: '510300',
    fundName: '沪深300ETF',
    fundType: 'INDEX',
    companyName: '华泰柏瑞基金',
    managerName: '柳军',
    qualityScore: 81.4,
    returnScore: 79.6,
    riskScore: 76.8,
    stabilityScore: 80.2,
    excessScore: 74.5,
    peerScore: 82.1,
    liquidityScore: 93.0,
    dataScore: 98.0,
    rankNo: 4,
    rankPercentile: 12.5,
    recommendLevel: 'WATCH',
    return60d: 5.4,
    return120d: 11.9,
    return250d: 19.7,
    maxDrawdown120d: -7.4,
    volatility120d: 15.8,
    peerPercentile: 12.5,
    scoreDate: today,
    reasons: ['规模和流动性较好', '跟踪标的样本稳定', '数据完整度较高'],
    risks: ['指数基金收益取决于标的指数表现', '历史波动不代表未来风险上限'],
    disclaimer: DISCLAIMER
  },
  {
    fundCode: '007689',
    fundName: '国投瑞银新能源混合A',
    fundType: 'ACTIVE_EQUITY',
    companyName: '国投瑞银基金',
    managerName: '施成',
    qualityScore: 73.5,
    returnScore: 82.0,
    riskScore: 61.5,
    stabilityScore: 66.2,
    excessScore: 78.1,
    peerScore: 68.0,
    liquidityScore: 70.0,
    dataScore: 92.0,
    rankNo: 18,
    rankPercentile: 35.4,
    recommendLevel: 'NEUTRAL',
    return60d: 10.1,
    return120d: 6.8,
    return250d: 12.4,
    maxDrawdown120d: -18.7,
    volatility120d: 29.4,
    peerPercentile: 35.4,
    scoreDate: today,
    reasons: ['近60日收益表现较强', '数据样本满足 MVP 评分要求'],
    risks: ['行业集中度较高，净值波动可能放大', '不构成投资建议，不承诺收益'],
    disclaimer: DISCLAIMER
  }
]

export const riskProfile: RiskProfile = {
  id: 1,
  riskLevel: 'MEDIUM',
  maxEquityPositionRate: 70,
  maxSingleFundPositionRate: 25,
  drawdownAlertRate: 8,
  dailyRiseAlertRate: 2,
  dailyFallAlertRate: 2.5,
  configJson: '{"aiModel":"deepseek-v4-flash"}',
  updateTime: now
}

const fundManagers: Record<string, string> = {
  '161725': '侯昊',
  '110011': '张坤',
  '007689': '施成',
  '005827': '张坤',
  '161903': '黄兴亮',
  '004997': '孙迪',
  '006751': '李元博',
  '000083': '胡昕炜',
  '510300': '柳军',
  '012345': '黎海威'
}

const fundThemes: Record<string, FundTheme[]> = {
  '007689': [
    { themeName: '新能源车', weight: 28.6, source: 'MOCK_FALLBACK' },
    { themeName: '储能', weight: 19.4, source: 'MOCK_FALLBACK' },
    { themeName: '电力设备', weight: 17.8, source: 'MOCK_FALLBACK' },
    { themeName: '半导体材料', weight: 9.2, source: 'MOCK_FALLBACK' }
  ],
  '161725': [
    { themeName: '白酒', weight: 62.8, source: 'MOCK_FALLBACK' },
    { themeName: '食品饮料', weight: 21.6, source: 'MOCK_FALLBACK' },
    { themeName: '消费升级', weight: 8.4, source: 'MOCK_FALLBACK' }
  ]
}

const defaultThemes: FundTheme[] = [
  { themeName: '核心资产', weight: 32.1, source: 'MOCK_FALLBACK' },
  { themeName: '成长风格', weight: 25.3, source: 'MOCK_FALLBACK' },
  { themeName: '均衡配置', weight: 18.7, source: 'MOCK_FALLBACK' }
]

const fundStocks: Record<string, FundStockHolding[]> = {
  '007689': [
    { stockCode: '300750', stockName: '宁德时代', holdingRatio: 9.86, industry: '电力设备' },
    { stockCode: '300274', stockName: '阳光电源', holdingRatio: 7.42, industry: '新能源' },
    { stockCode: '002594', stockName: '比亚迪', holdingRatio: 6.88, industry: '汽车' },
    { stockCode: '300014', stockName: '亿纬锂能', holdingRatio: 5.76, industry: '电池' },
    { stockCode: '300124', stockName: '汇川技术', holdingRatio: 4.92, industry: '自动化' }
  ],
  '161725': [
    { stockCode: '600519', stockName: '贵州茅台', holdingRatio: 15.24, industry: '白酒' },
    { stockCode: '000858', stockName: '五粮液', holdingRatio: 12.62, industry: '白酒' },
    { stockCode: '000568', stockName: '泸州老窖', holdingRatio: 8.18, industry: '白酒' },
    { stockCode: '600809', stockName: '山西汾酒', holdingRatio: 7.64, industry: '白酒' },
    { stockCode: '002304', stockName: '洋河股份', holdingRatio: 5.36, industry: '白酒' }
  ]
}

const defaultStocks: FundStockHolding[] = [
  { stockCode: '600036', stockName: '招商银行', holdingRatio: 7.2, industry: '银行' },
  { stockCode: '000333', stockName: '美的集团', holdingRatio: 5.9, industry: '家电' },
  { stockCode: '600276', stockName: '恒瑞医药', holdingRatio: 4.8, industry: '医药' },
  { stockCode: '601318', stockName: '中国平安', holdingRatio: 4.1, industry: '保险' },
  { stockCode: '000651', stockName: '格力电器', holdingRatio: 3.6, industry: '家电' }
]

function getHoldingByCode(fundCode: string) {
  return holdings.find((item) => item.fundCode === fundCode) || holdings[0]
}

function buildFundNav(fundCode: string): FundNavPoint[] {
  const base = getHoldingByCode(fundCode).latestOfficialNav || 1
  return Array.from({ length: 52 }, (_, index) => {
    const wave = Math.sin(index / 3.2) * 0.08 + Math.cos(index / 5.4) * 0.04 + index * 0.006
    const nav = Number((base + wave).toFixed(4))
    return {
      date: `2025-${String((index % 12) + 1).padStart(2, '0')}-${String((index % 24) + 1).padStart(2, '0')}`,
      nav,
      accumulatedNav: Number((nav + 0.42).toFixed(4)),
      dailyGrowthRate: Number(((Math.sin(index / 2.1) + 0.2) * 0.85).toFixed(2)),
      indexReturnRate: Number((Math.sin(index / 6) * 2.4 + index * 0.12 - 0.8).toFixed(2)),
      indexCode: fundCode === '161725' ? '399997' : '000300',
      indexName: fundCode === '161725' ? '中证白酒' : '沪深300'
    }
  })
}

function toRank(holding: FundHolding) {
  return {
    holdingId: holding.id,
    fundCode: holding.fundCode,
    fundName: holding.fundName,
    holdingAmount: holding.holdingAmount,
    holdingProfit: holding.holdingProfit,
    holdingProfitRate: holding.holdingProfitRate
  }
}

function page<T>(records: T[], pageNo = 1, pageSize = 20): PageResponse<T> {
  const start = (pageNo - 1) * pageSize
  return {
    pageNo,
    pageSize,
    total: records.length,
    records: records.slice(start, start + pageSize)
  }
}

export const mockApi = {
  async login(username: string, password: string): Promise<LoginResult> {
    if (!username || !password) throw new Error('请输入用户名和密码')
    return { token: 'mock-quantfund-token', user: mockUser }
  },
  async register(username: string, password: string, nickname: string): Promise<LoginResult> {
    if (!username || !password || !nickname) throw new Error('请填写完整注册信息')
    return { token: 'mock-quantfund-token', user: { ...mockUser, username, nickname } }
  },
  async me() {
    return mockUser
  },
  async updateProfile(request: ProfileUpdateRequest) {
    mockUser.nickname = request.nickname || mockUser.nickname
    mockUser.phone = request.phone || mockUser.phone
    mockUser.email = request.email || mockUser.email
    mockUser.avatar = request.avatar || mockUser.avatar
    return mockUser
  },
  async updatePassword(request: PasswordUpdateRequest) {
    if (!request.oldPassword || !request.newPassword) throw new Error('请填写旧密码和新密码')
  },
  async dashboard() {
    return buildDashboard()
  },
  async marketReadings() {
    return [
      { code: '000001', name: '上证指数', latestPrice: 4110.81, changeValue: 4.56, changeRate: 0.11, turnover: 1514193285000.4, updateTime: new Date().toISOString(), sourceName: 'MOCK' },
      { code: '399001', name: '深证成指', latestPrice: 16051.32, changeValue: 197.12, changeRate: 1.24, turnover: 1770135015355.8, updateTime: new Date().toISOString(), sourceName: 'MOCK' },
      { code: '399006', name: '创业板指', latestPrice: 4251.42, changeValue: 59.23, changeRate: 1.41, turnover: 853839664922.36, updateTime: new Date().toISOString(), sourceName: 'MOCK' },
      { code: '000905', name: '中证500', latestPrice: 8842.94, changeValue: 154.35, changeRate: 1.78, turnover: 684019604157.1, updateTime: new Date().toISOString(), sourceName: 'MOCK' }
    ]
  },
  async marketStatus() {
    return marketSessionStatus
  },
  async holdings() {
    return holdings
  },
  async portfolios() {
    return portfolios
  },
  async createPortfolio(request: PortfolioAccountRequest) {
    const saved: PortfolioAccount = {
      id: Date.now(),
      accountName: request.accountName,
      platformType: request.platformType,
      totalAsset: 0,
      totalInvestAmount: 0,
      currentProfit: 0,
      currentProfitRate: 0,
      dailyProfit: 0,
      cashPositionRate: 100,
      equityPositionRate: 0,
      bondPositionRate: 0,
      maxSingleFundPositionRate: request.maxSingleFundPositionRate,
      status: 'ACTIVE',
      updateTime: now
    }
    portfolios.unshift(saved)
    return saved
  },
  async createHolding(request: HoldingCreateRequest) {
    const latestNav = request.latestOfficialNav ?? 1
    const estimateNav = request.currentEstimateNav ?? latestNav
    const holdingAmount = request.holdingShare > 0 ? request.holdingShare * estimateNav : request.holdingAmount
    const holdingCost = request.holdingProfit !== undefined ? Math.max(holdingAmount - request.holdingProfit, 0) : request.holdingCost
    const holdingProfit = holdingAmount - holdingCost
    const estimateRate = latestNav > 0 ? (estimateNav - latestNav) / latestNav * 100 : 0
    const saved: FundHolding = {
      id: Date.now(),
      accountId: request.accountId,
      fundCode: request.fundCode,
      fundName: request.fundName,
      fundType: request.fundType,
      activeFund: request.activeFund,
      holdingAmount,
      holdingShare: request.holdingShare,
      holdingCost,
      currentEstimateNav: estimateNav,
      latestOfficialNav: latestNav,
      holdingProfit,
      holdingProfitRate: holdingCost ? holdingProfit / holdingCost * 100 : 0,
      dailyProfit: request.holdingShare * (estimateNav - latestNav),
      yesterdayProfit: 0,
      positionRate: 0,
      currentEstimateGrowthRate: estimateRate,
      officialNavUpdated: false,
      officialNavDate: null,
      relatedThemeName: request.fundName.includes('白酒') ? '白酒' : '重仓板块待同步',
      relatedThemeRate: estimateRate,
      valuationSource: 'MOCK',
      estimateBasis: '重仓股占比加权估算',
      marketStatus: 'A股交易中',
      holdingDays: 0,
      sourcePlatform: request.sourcePlatform || '手动添加',
      regularInvestment: Boolean(request.regularInvestment),
      coreHolding: Boolean(request.coreHolding),
      watchFocus: Boolean(request.watchFocus),
      updateTime: now,
      disclaimer: DISCLAIMER
    }
    holdings.unshift(saved)
    return saved
  },
  async updateHolding(id: number, request: HoldingUpdateRequest) {
    const existing = holdings.find((item) => item.id === id)
    if (!existing) throw new Error('未找到持仓记录')
    const latestNav = request.latestOfficialNav ?? existing.latestOfficialNav ?? 1
    const estimateNav = request.currentEstimateNav ?? existing.currentEstimateNav ?? latestNav
    const holdingAmount = request.holdingShare > 0 ? request.holdingShare * estimateNav : request.holdingAmount
    const holdingCost = request.holdingProfit !== undefined ? Math.max(holdingAmount - request.holdingProfit, 0) : request.holdingCost
    const holdingProfit = holdingAmount - holdingCost
    const holdingProfitRate = holdingCost ? holdingProfit / holdingCost * 100 : 0
    const estimateRate = latestNav > 0 ? (estimateNav - latestNav) / latestNav * 100 : 0
    Object.assign(existing, {
      ...existing,
      ...request,
      holdingAmount,
      holdingCost,
      currentEstimateNav: estimateNav,
      latestOfficialNav: latestNav,
      holdingProfit,
      holdingProfitRate,
      dailyProfit: request.holdingShare * (estimateNav - latestNav),
      currentEstimateGrowthRate: estimateRate,
      officialNavUpdated: existing.officialNavUpdated,
      officialNavDate: existing.officialNavDate,
      relatedThemeRate: estimateRate,
      valuationSource: 'MOCK',
      estimateBasis: '重仓股占比加权估算',
      marketStatus: 'A股交易中',
      sourcePlatform: request.sourcePlatform || existing.sourcePlatform,
      regularInvestment: Boolean(request.regularInvestment),
      coreHolding: Boolean(request.coreHolding),
      watchFocus: Boolean(request.watchFocus),
      updateTime: now,
      disclaimer: DISCLAIMER
    })
    return existing
  },
  async deleteHolding(id: number) {
    const index = holdings.findIndex((item) => item.id === id)
    if (index === -1) throw new Error('未找到持仓记录')
    holdings.splice(index, 1)
    for (let reportIndex = aiSuggestions.length - 1; reportIndex >= 0; reportIndex -= 1) {
      if (aiSuggestions[reportIndex].holdingId === id) {
        aiSuggestions.splice(reportIndex, 1)
      }
    }
  },
  async clearHolding(id: number, request?: ClearHoldingRequest) {
    const existing = holdings.find((item) => item.id === id)
    if (!existing) throw new Error('未找到持仓记录')
    const clearedAmount = existing.holdingAmount
    const clearedShare = existing.holdingShare
    const clearedNav = existing.currentEstimateNav || existing.latestOfficialNav || (clearedShare > 0 ? clearedAmount / clearedShare : 0)
    const tradeAmount = request?.tradeAmount ?? clearedAmount
    const tradeFee = request?.tradeFee ?? 0
    if (clearedAmount > 0 || clearedShare > 0) {
      trades.unshift({
        id: Date.now(),
        accountId: existing.accountId,
        holdingId: existing.id,
        fundCode: existing.fundCode,
        fundName: existing.fundName,
        tradeType: 'SELL',
        tradeStatus: 'COMPLETED',
        tradeAmount,
        tradeShare: clearedShare,
        tradeNav: clearedNav,
        tradeFee,
        tradeTime: now,
        remark: simulatedRemark(request?.remark || '清仓自动生成的模拟卖出流水'),
        simulatedTradeNotice: SIMULATED_TRADE_NOTICE
      })
    }
    Object.assign(existing, {
      activeFund: false,
      holdingAmount: 0,
      holdingShare: 0,
      holdingCost: 0,
      holdingProfit: 0,
      holdingProfitRate: 0,
      dailyProfit: 0,
      positionRate: 0,
      currentEstimateGrowthRate: 0,
      updateTime: now
    })
    return existing
  },
  async recalculateHolding(id: number) {
    const existing = holdings.find((item) => item.id === id)
    if (!existing) throw new Error('未找到持仓记录')
    const holdingProfit = existing.holdingAmount - existing.holdingCost
    existing.holdingProfit = holdingProfit
    existing.holdingProfitRate = existing.holdingCost ? holdingProfit / existing.holdingCost * 100 : 0
    const latestNav = existing.latestOfficialNav || 1
    const estimateNav = existing.currentEstimateNav || latestNav
    const estimateRate = latestNav > 0 ? (estimateNav - latestNav) / latestNav * 100 : 0
    const baseAmount = existing.holdingShare > 0 && latestNav > 0 ? existing.holdingShare * latestNav : undefined
    existing.dailyProfit = dailyProfitByRate(existing.holdingAmount, estimateRate, baseAmount)
    existing.updateTime = now
    return existing
  },
  async syncOfficialNav() {
    holdings.forEach((holding, index) => {
      const finalRate = [4.52, -0.09, 1.81, -7.81, 2.53][index] ?? holding.currentEstimateGrowthRate
      const previousNav = holding.latestOfficialNav || holding.currentEstimateNav || 1
      const finalNav = previousNav * (1 + finalRate / 100)
      holding.latestOfficialNav = finalNav
      holding.currentEstimateNav = finalNav
      holding.currentEstimateGrowthRate = finalRate
      holding.relatedThemeRate = finalRate
      holding.holdingAmount = holding.holdingShare * finalNav
      holding.holdingProfit = holding.holdingAmount - holding.holdingCost
      holding.holdingProfitRate = holding.holdingCost ? holding.holdingProfit / holding.holdingCost * 100 : 0
      holding.dailyProfit = holding.holdingShare * (finalNav - previousNav)
      holding.officialNavUpdated = true
      holding.officialNavDate = today
      holding.updateTime = now
    })
    const total = holdings.reduce((sum, item) => sum + item.holdingAmount, 0)
    holdings.forEach((holding) => {
      holding.positionRate = total > 0 ? holding.holdingAmount / total * 100 : 0
    })
    return holdings
  },
  async strategies() {
    return strategySignals
  },
  async quantHealth(): Promise<QuantEngineHealth> {
    return {
      status: 'UP',
      service: 'quant-engine',
      modelVersion: 'rule-v1.39.0',
      enabled: true
    }
  },
  async quantSignals(query: { accountId?: number; holdingId?: number; fundCode?: string; action?: string } = {}) {
    const latestByHolding = new Map<number, QuantSignal>()
    for (const signal of [...quantSignals].sort((left, right) => {
      const timeDiff = new Date(right.signalTime).getTime() - new Date(left.signalTime).getTime()
      if (timeDiff !== 0) return timeDiff
      return right.id - left.id
    })) {
      if (!latestByHolding.has(signal.holdingId)) {
        latestByHolding.set(signal.holdingId, signal)
      }
    }
    return Array.from(latestByHolding.values()).filter((item) => {
      if (query.accountId && item.accountId !== query.accountId) return false
      if (query.holdingId && item.holdingId !== query.holdingId) return false
      if (query.fundCode && item.fundCode !== query.fundCode) return false
      if (query.action && item.action !== query.action) return false
      return true
    })
  },
  async analyzeQuantHolding(holdingId: number) {
    const holding = holdings.find((item) => item.id === holdingId)
    if (!holding) throw new Error('未找到持仓记录')
    const signal = { ...toQuantSignal(holding), id: Date.now(), signalTime: now }
    const existingIndex = quantSignals.findIndex((item) => item.holdingId === holdingId)
    if (existingIndex >= 0) quantSignals.splice(existingIndex, 1, signal)
    else quantSignals.unshift(signal)
    return signal
  },
  async analyzeQuantAccount(accountId: number) {
    const results = holdings.filter((item) => item.accountId === accountId).map((item, index) => ({
      ...toQuantSignal(item, index),
      id: Date.now() + index,
      signalTime: now
    }))
    results.forEach((signal) => {
      const existingIndex = quantSignals.findIndex((item) => item.holdingId === signal.holdingId)
      if (existingIndex >= 0) quantSignals.splice(existingIndex, 1, signal)
      else quantSignals.unshift(signal)
    })
    return results
  },
  async aiHistory() {
    const activeHoldingIds = new Set(holdings.map((holding) => holding.id))
    activeHoldingIds.forEach((holdingId) => pruneAiHistory(holdingId))
    return aiSuggestions.filter((report) => activeHoldingIds.has(report.holdingId))
  },
  async profit() {
    return buildProfitAnalysis()
  },
  async calendar() {
    return buildProfitCalendar()
  },
  async trades() {
    return trades
  },
  async dataSources() {
    return dataSources
  },
  async dataSourceHealth() {
    return dataSourceHealth
  },
  async aiRuntimeConfig() {
    return aiRuntimeConfig
  },
  async saveDataSource(request: DataSourceConfigRequest) {
    const saved: DataSourceConfig = {
      id: Date.now(),
      userId: 1,
      sourceName: request.sourceName,
      baseUrl: request.baseUrl,
      timeoutMs: request.timeoutMs,
      refreshIntervalSeconds: request.refreshIntervalSeconds,
      rateLimitPerMinute: request.rateLimitPerMinute,
      enabled: request.enabled,
      priority: request.priority,
      configJson: request.configJson,
      userOverride: true,
      updateTime: now
    }
    dataSources.unshift(saved)
    return saved
  },
  async updateDataSource(id: number, request: DataSourceConfigRequest) {
    const existing = dataSources.find((item) => item.id === id)
    if (!existing) throw new Error('未找到数据源配置')
    Object.assign(existing, {
      ...request,
      userOverride: true,
      updateTime: now
    })
    return existing
  },
  async operationLogs(query: OperationLogQuery = {}) {
    const filtered = operationLogs.filter((item) => {
      if (query.module && item.module !== query.module) return false
      if (query.success !== undefined && item.success !== query.success) return false
      return true
    })
    return page(filtered, query.pageNo, query.pageSize)
  },
  async apiCallLogs(query: ApiCallLogQuery = {}) {
    const filtered = apiCallLogs.filter((item) => {
      if (query.provider && item.provider !== query.provider) return false
      if (query.success !== undefined && item.success !== query.success) return false
      return true
    })
    return page(filtered, query.pageNo, query.pageSize)
  },
  async strategyConfigs() {
    return strategyConfigs
  },
  async riskProfile() {
    return riskProfile
  },
  async saveRiskProfile(request: RiskProfileRequest) {
    Object.assign(riskProfile, {
      ...request,
      updateTime: now
    })
    return riskProfile
  },
  async saveStrategyConfig(request: StrategyConfigRequest) {
    const existing = strategyConfigs.find((item) => item.strategyType === request.strategyType && item.configName === request.configName)
    const saved: StrategyConfig = {
      id: existing?.id || Date.now(),
      configName: request.configName,
      strategyType: request.strategyType,
      fundType: request.fundType || null,
      paramsJson: request.paramsJson,
      enabled: request.enabled,
      updateTime: now
    }
    if (existing) {
      Object.assign(existing, saved)
      return existing
    }
    strategyConfigs.unshift(saved)
    return saved
  },
  async fundBasicInfo(fundCode: string): Promise<FundBasicInfo> {
    const holding = getHoldingByCode(fundCode)
    return {
      fundCode: holding.fundCode,
      fundName: holding.fundName,
      fundType: holding.fundType,
      managerName: fundManagers[holding.fundCode] || '量化团队',
      establishDate: '2018-06-23',
      riskLevel: holding.fundType.includes('债') ? 'LOW' : holding.fundType.includes('ETF') ? 'MEDIUM' : 'HIGH',
      trackingIndex: holding.fundType.includes('指数') || holding.fundType.includes('ETF') ? '沪深300 / 行业指数' : '',
      activeFund: holding.activeFund
    }
  },
  async searchFunds(keyword: string, mode: FundSearchMode = 'FUZZY') {
    const normalized = keyword.trim().toLowerCase()
    if (!normalized) return []
    return fundSearchPool
      .filter((item) => {
        const code = item.fundCode.toLowerCase()
        const name = item.fundName.toLowerCase()
        const pinyin = (item.pinyin || '').toLowerCase()
        if (mode === 'EXACT') {
          return code === normalized || name === normalized || pinyin === normalized
        }
        return code.includes(normalized) || name.includes(normalized) || pinyin.includes(normalized)
      })
      .slice(0, 10)
  },
  async fundNav(fundCode: string) {
    return buildFundNav(fundCode)
  },
  async heavyStocks(fundCode: string) {
    return fundStocks[fundCode] || defaultStocks
  },
  async themes(fundCode: string) {
    return fundThemes[fundCode] || defaultThemes
  },
  async peerRank(fundCode: string): Promise<FundPeerRank> {
    const holding = getHoldingByCode(fundCode)
    return {
      rank: holding.watchFocus ? 118 : 246,
      total: 920,
      percentile: holding.watchFocus ? 12.8 : 26.7,
      period: '近一年'
    }
  },
  async fundScreenerRank(query: FundScreenerRankQuery = {}) {
    const filtered = fundScreenerRankItems.filter((item) => {
      if (query.fundType && item.fundType !== query.fundType) return false
      if (query.recommendLevel && item.recommendLevel !== query.recommendLevel) return false
      if (query.minScore !== undefined && item.qualityScore < query.minScore) return false
      return true
    })
    const sorted = [...filtered].sort((left, right) => right.qualityScore - left.qualityScore)
    return page(sorted, query.pageNo, query.pageSize)
  },
  async fundScreenerExplain(fundCode: string): Promise<FundScreenerExplain> {
    const item = fundScreenerRankItems.find((rankItem) => rankItem.fundCode === fundCode) || fundScreenerRankItems[0]
    return {
      fundCode: item.fundCode,
      fundName: item.fundName,
      fundType: item.fundType,
      qualityScore: item.qualityScore,
      recommendLevel: item.recommendLevel,
      scoreBreakdown: {
        returnScore: item.returnScore,
        riskScore: item.riskScore,
        stabilityScore: item.stabilityScore,
        excessScore: item.excessScore,
        peerScore: item.peerScore,
        liquidityScore: item.liquidityScore || 0,
        dataScore: item.dataScore
      },
      factors: {
        return60d: item.return60d,
        return120d: item.return120d,
        return250d: item.return250d,
        maxDrawdown120d: item.maxDrawdown120d,
        volatility120d: item.volatility120d,
        peerPercentile: item.peerPercentile
      },
      reasons: item.reasons,
      risks: item.risks,
      scoreDate: item.scoreDate,
      modelVersion: 'screener-rule-v1',
      disclaimer: DISCLAIMER
    }
  },
  async refreshFundScreenerScore(): Promise<FundScreenerTaskResult> {
    return {
      taskName: 'REFRESH_SCORE',
      status: 'SKIPPED',
      successCount: 0,
      failureCount: 0,
      skippedCount: 1,
      costTimeMs: 0,
      errorSummaries: [],
      message: 'mock 模式下未执行真实评分刷新',
      finishTime: now
    }
  },
  async refreshFundScreenerFull(): Promise<FundScreenerTaskResult> {
    return {
      taskName: 'REFRESH_FULL',
      status: 'SKIPPED',
      successCount: 0,
      failureCount: 0,
      skippedCount: 1,
      costTimeMs: 0,
      errorSummaries: [],
      message: 'mock 模式下未执行完整同步路径',
      finishTime: now
    }
  },
  async runFundScreenerBacktest(): Promise<FundScreenerTaskResult> {
    return {
      taskName: 'SCREENER_BACKTEST',
      status: 'SUCCESS',
      successCount: 6,
      failureCount: 0,
      skippedCount: 39,
      costTimeMs: 86,
      errorSummaries: [],
      message: '增量回测完成：新增6条，跳过39条，失败0条',
      finishTime: now
    }
  },
  async fundScreenerValidation(): Promise<FundScreenerValidation> {
    const buckets: FundScreenerValidation['metrics'][number]['bucketName'][] = ['TOP_5', 'TOP_10', 'WATCH', 'NEUTRAL', 'AVOID']
    const horizons: FundScreenerValidation['metrics'][number]['horizonDays'][] = [20, 60, 120]
    const baseReturn: Record<FundScreenerValidation['metrics'][number]['bucketName'], number> = {
      TOP_5: 7.4,
      TOP_10: 6.2,
      WATCH: 4.1,
      NEUTRAL: 2.8,
      AVOID: -0.6
    }
    return {
      latestRunDate: today,
      earliestScoreDate: '2026-01-05',
      latestScoreDate: '2026-04-08',
      status: 'EFFECTIVE',
      conclusion: 'TOP_10 在60日维度跑赢全样本 2.30%，胜率 61.00%，且 AVOID 未跑赢 NEUTRAL，策略当前有效。',
      calibrationAdvice: [
        '保持当前推荐阈值，持续观察20/60/120日分层稳定性。',
        '在引入新评分因子前，先确认多个市场阶段仍保持正超额收益。'
      ],
      policy: {
        strongMinScore: 82,
        strongTopPercent: 8,
        watchMinScore: 72,
        watchTopPercent: 20,
        neutralMinScore: 58,
        minValidationSamples: 30,
        minValidationScoreDates: 3
      },
      lookbackMetrics: buckets.flatMap((bucketName, bucketIndex) => horizons.map((horizonDays, horizonIndex) => ({
        bucketName,
        horizonDays,
        sampleCount: 110 - bucketIndex * 8,
        scoreDateCount: 1,
        avgForwardReturn: baseReturn[bucketName] * (0.7 + horizonIndex * 0.3),
        winRate: 67 - bucketIndex * 4 + horizonIndex,
        avgExcessReturn: (3.2 - bucketIndex * 0.8) * (0.75 + horizonIndex * 0.18),
        maxDrawdown: -4.8 - bucketIndex * 1.1 - horizonIndex * 1.0,
        statisticallySignificant: true
      }))),
      metrics: buckets.flatMap((bucketName, bucketIndex) => horizons.map((horizonDays, horizonIndex) => ({
        bucketName,
        horizonDays,
        sampleCount: 48 - bucketIndex * 3,
        scoreDateCount: 4,
        avgForwardReturn: baseReturn[bucketName] * (0.55 + horizonIndex * 0.25),
        winRate: 64 - bucketIndex * 5 + horizonIndex,
        avgExcessReturn: (2.8 - bucketIndex * 0.9) * (0.65 + horizonIndex * 0.2),
        maxDrawdown: -5.8 - bucketIndex * 1.3 - horizonIndex * 1.1,
        statisticallySignificant: true
      })))
    }
  },
  async refreshEstimate(fundCode: string) {
    return {
      fundCode,
      estimateDate: today,
      estimateNav: 1.2868,
      estimateGrowthRate: 0.42,
      estimateTime: now,
      sourceName: 'MOCK_FALLBACK',
      delayed: false,
      rawPayload: '{"mock":true}'
    }
  },
  async generateAiAnalysis(holdingId: number) {
    const holding = holdings.find((item) => item.id === holdingId) || holdings[0]
    const report: AiAnalysisReport = {
      id: Date.now(),
      accountId: holding.accountId,
      holdingId: holding.id,
      fundCode: holding.fundCode,
      modelName: 'deepseek-v4-flash',
      action: holding.dailyProfit < 0 ? 'WATCH' : 'HOLD',
      actionText: holding.dailyProfit < 0 ? '建议观察' : '建议持有',
      suggestAmount: 0,
      suggestRatio: 0,
      confidence: 0.71,
      riskLevel: 'MEDIUM',
      deadline: '15:00前',
      strategy: '仓位监控 / 观望',
      reasons: ['本地 mock 生成分析，真实联调时由后端策略和 AI 模块返回。'],
      risks: ['当天估值只作为参考，晚间正式净值前不是最终净值。'],
      dataSummary: `${holding.fundCode} 当前持仓 ${holding.holdingAmount.toFixed(2)} 元。`,
      finalConclusion: '保持观察，等待正式净值确认。',
      fallbackUsed: true,
      analysisTime: now,
      disclaimer: DISCLAIMER
    }
    aiSuggestions.unshift(report)
    pruneAiHistory(holding.id)
    return report
  },
  async generateAiAccountAnalysis(accountId: number) {
    const reports = await Promise.all(
      holdings
        .filter((item) => item.accountId === accountId)
        .map((item) => this.generateAiAnalysis(item.id))
    )
    return reports
  },
  async createTrade(request: TradeRecordRequest) {
    const trade: TradeRecord = {
      id: Date.now(),
      accountId: request.accountId,
      holdingId: request.holdingId,
      fundCode: request.fundCode,
      fundName: request.fundName,
      tradeType: request.tradeType,
      tradeStatus: request.tradeStatus || 'PROCESSING',
      tradeAmount: request.tradeAmount,
      tradeShare: request.tradeShare || Math.round(request.tradeAmount / (request.tradeNav || 1.2)),
      tradeNav: request.tradeNav || 1.2,
      tradeFee: request.tradeFee || 0,
      tradeTime: request.tradeTime || now,
      remark: simulatedRemark(request.remark),
      simulatedTradeNotice: SIMULATED_TRADE_NOTICE
    }
    trades.unshift(trade)
    return trade
  },
  async deleteTrade(id: number) {
    const index = trades.findIndex((item) => item.id === id)
    if (index === -1) throw new Error('未找到交易记录')
    if (trades[index].tradeStatus !== 'PROCESSING') throw new Error('已正式执行的交易不能删除')
    trades.splice(index, 1)
  },
  async createConvertPair(request: ConvertPairTradeRequest) {
    const outHolding = holdings.find((item) => item.id === request.outHoldingId)
    const timestamp = Date.now()
    const outNav = request.outTradeNav || outHolding?.currentEstimateNav || outHolding?.latestOfficialNav || 1.2
    const inNav = request.inTradeNav || 1.2
    const outTrade: TradeRecord = {
      id: timestamp,
      accountId: request.accountId,
      holdingId: request.outHoldingId,
      fundCode: outHolding?.fundCode || '',
      fundName: outHolding?.fundName || '',
      tradeType: 'CONVERT_OUT',
      tradeStatus: request.tradeStatus || 'COMPLETED',
      tradeAmount: request.outTradeAmount,
      tradeShare: request.outTradeShare || Math.round(request.outTradeAmount / Math.max(outNav, 0.0001)),
      tradeNav: outNav,
      tradeFee: request.outTradeFee || 0,
      tradeTime: request.tradeTime || now,
      remark: simulatedRemark(request.remark),
      simulatedTradeNotice: SIMULATED_TRADE_NOTICE
    }
    const inTrade: TradeRecord = {
      id: timestamp + 1,
      accountId: request.accountId,
      holdingId: request.inHoldingId,
      fundCode: request.inFundCode,
      fundName: request.inFundName,
      tradeType: 'CONVERT_IN',
      tradeStatus: request.tradeStatus || 'COMPLETED',
      tradeAmount: request.inTradeAmount,
      tradeShare: request.inTradeShare || Math.round(request.inTradeAmount / Math.max(inNav, 0.0001)),
      tradeNav: inNav,
      tradeFee: request.inTradeFee || 0,
      tradeTime: request.tradeTime || now,
      remark: simulatedRemark(request.remark),
      simulatedTradeNotice: SIMULATED_TRADE_NOTICE
    }
    trades.unshift(inTrade, outTrade)
    return [outTrade, inTrade]
  }
}
