<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MetricTile from '@/components/common/MetricTile.vue'
import ActionTag from '@/components/common/ActionTag.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import BaseChart from '@/components/charts/BaseChart.vue'
import { returnTrendOption } from '@/components/charts/chartOptions'
import { quantApi } from '@/api/quant'
import { useDashboardStore } from '@/stores/dashboard'
import { metricTone, money, percent, signed, toneClass } from '@/utils/format'
import type { MarketSessionStatus, ProfitAnalysis, StrategySignal } from '@/types/domain'

const store = useDashboardStore()
const router = useRouter()
type TrendRange = 'TODAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'ALL'
type BenchmarkIndex = '000300' | '000001' | '399006'
type TrendPoint = {
  label: string
  portfolioReturn: number | null
  indexReturn: number | null
  dailyProfit?: number | null
}

type StrategySignalGroup = {
  key: string
  count: number
  fundCode: string
  fundName?: string
  latest: StrategySignal
  types: string[]
}
const activeRange = ref<TrendRange>('TODAY')
const activeIndexCode = ref<BenchmarkIndex>('000300')
const refreshing = ref(false)
const trendLoading = ref(false)
const showAllStrategySignals = ref(false)
const profitAnalysis = ref<ProfitAnalysis>()
const intradayTrendPoints = ref<TrendPoint[]>([])
let refreshTimer: number | undefined
let initialOverviewPromise: Promise<unknown> | null = null
let lastOfficialNavSyncAt = 0
let lastEstimateRefreshAt = 0

const OFFICIAL_NAV_SYNC_COOLDOWN_MS = 6000
const ESTIMATE_REFRESH_COOLDOWN_MS = 11000
const AUTO_ESTIMATE_REFRESH_INTERVAL_MS = 15000

const trendRanges: Array<{ label: string; value: TrendRange }> = [
  { label: '今日', value: 'TODAY' },
  { label: '本周', value: 'WEEK' },
  { label: '本月', value: 'MONTH' },
  { label: '今年', value: 'YEAR' },
  { label: '全部', value: 'ALL' }
]

const benchmarkIndices: Array<{ label: string; value: BenchmarkIndex }> = [
  { label: '沪深300', value: '000300' },
  { label: '上证指数', value: '000001' },
  { label: '创业板指', value: '399006' }
]

onMounted(() => {
  window.addEventListener('quantfund:refresh-estimate', handleHeaderRefreshEstimate)
  initialOverviewPromise = store.fetchOverview(true)
    .catch(() => undefined)
    .finally(() => {
      lastOfficialNavSyncAt = Date.now()
      initialOverviewPromise = null
    })
  loadReturnTrend()
  refreshTimer = window.setInterval(autoRefreshEstimate, AUTO_ESTIMATE_REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  window.removeEventListener('quantfund:refresh-estimate', handleHeaderRefreshEstimate)
})

const overview = computed(() => store.overview)
const summary = computed(() => overview.value?.summary)
const trendPoints = computed<TrendPoint[]>(() => {
  if (activeRange.value === 'TODAY') {
    return intradayTrendPoints.value
  }
  const points = profitAnalysis.value?.trend || []
  return points.map((point) => ({
    label: point.date.slice(5),
    portfolioReturn: point.dailyProfitRate,
    indexReturn: point.indexReturnRate ?? null,
    dailyProfit: point.dailyProfit
  }))
})
const activeIndexName = computed(() => benchmarkIndices.find((item) => item.value === activeIndexCode.value)?.label || '沪深300')
const trendOption = computed(() => returnTrendOption(trendPoints.value, activeIndexName.value))
const buySellSuggestionCount = computed(() => {
  return overview.value?.todayAiSuggestions.filter((item) => item.action === 'BUY' || item.action === 'SELL' || item.action === 'CONVERT').length || 0
})
const watchSuggestionCount = computed(() => {
  return overview.value?.todayAiSuggestions.filter((item) => item.action === 'WATCH' || item.action === 'HOLD').length || 0
})
const highRiskSignalCount = computed(() => {
  return overview.value?.latestStrategySignals.filter((item) => item.riskLevel === 'HIGH').length || 0
})
const activeSignalCount = computed(() => {
  return overview.value?.latestStrategySignals.filter((item) => item.action !== 'HOLD' && item.action !== 'WATCH').length || 0
})
const strategySignalGroups = computed<StrategySignalGroup[]>(() => {
  const signals = overview.value?.latestStrategySignals || []
  const groups = new Map<string, StrategySignal[]>()
  for (const signal of signals) {
    const key = signal.holdingId ? `holding:${signal.holdingId}` : `fund:${signal.fundCode}`
    const bucket = groups.get(key) || []
    bucket.push(signal)
    groups.set(key, bucket)
  }
  return Array.from(groups.entries())
    .map(([key, items]) => {
      const sorted = [...items].sort((a, b) => Date.parse(b.signalTime) - Date.parse(a.signalTime))
      const latest = sorted[0]
      return {
        key,
        count: sorted.length,
        fundCode: latest.fundCode,
        fundName: latest.fundName,
        latest,
        types: Array.from(new Set(sorted.map((item) => item.signalType)))
      }
    })
    .sort((a, b) => Date.parse(b.latest.signalTime) - Date.parse(a.latest.signalTime))
})
const visibleStrategySignalGroups = computed(() => showAllStrategySignals.value ? strategySignalGroups.value : strategySignalGroups.value.slice(0, 5))
const strategySignalCollapsed = computed(() => strategySignalGroups.value.length > 5)
const latestTrendPoint = computed(() => {
  const trend = overview.value?.profitTrend || []
  return trend[trend.length - 1]
})
const trendStatusText = computed(() => activeRange.value === 'TODAY'
  ? '盘中分时实时走势'
  : latestTrendPoint.value?.profitStatusText || '按已同步快照计算')
const dailyProfitRate = computed(() => {
  if (!summary.value) return 0
  return safeRatio(summary.value.dailyProfit, summary.value.totalAsset)
})
const currentPortfolioReturn = computed(() => {
  if (activeRange.value === 'TODAY') {
    return dailyProfitRate.value
  }
  const points = trendPoints.value
  return points[points.length - 1]?.portfolioReturn ?? dailyProfitRate.value
})
const currentIndexReturn = computed(() => {
  const points = [...trendPoints.value].reverse()
  return points.find((point) => point.indexReturn !== null)?.indexReturn ?? null
})
const excessReturn = computed(() => currentIndexReturn.value === null ? null : currentPortfolioReturn.value - currentIndexReturn.value)
const holdingNameByCode = computed(() => {
  return new Map((overview.value?.topHoldings || []).map((holding) => [holding.fundCode, holding.fundName]))
})

function safeRatio(part: number, total: number) {
  return total > 0 ? part / total * 100 : 0
}

function isoDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function trendRangeParams() {
  const now = new Date()
  const start = new Date(now)
  if (activeRange.value === 'TODAY') {
    return { indexCode: activeIndexCode.value }
  }
  if (activeRange.value === 'WEEK') {
    const day = now.getDay() || 7
    start.setDate(now.getDate() - day + 1)
    return { startDate: isoDate(start), endDate: isoDate(now), indexCode: activeIndexCode.value }
  }
  if (activeRange.value === 'MONTH') {
    start.setDate(1)
    return { startDate: isoDate(start), endDate: isoDate(now), indexCode: activeIndexCode.value }
  }
  if (activeRange.value === 'YEAR') {
    start.setMonth(0, 1)
    return { startDate: isoDate(start), endDate: isoDate(now), indexCode: activeIndexCode.value }
  }
  return { endDate: isoDate(now), indexCode: activeIndexCode.value }
}

async function loadReturnTrend() {
  trendLoading.value = true
  try {
    if (activeRange.value === 'TODAY') {
      const points = await quantApi.profitIntraday({ indexCode: activeIndexCode.value })
      intradayTrendPoints.value = points
        .map((point) => ({
          label: point.time.slice(11, 16),
          portfolioReturn: point.portfolioReturn,
          indexReturn: point.indexReturn,
          dailyProfit: point.dailyProfit
        }))
        .filter((point) => isAShareIntradayLabel(point.label))
      return
    }
    profitAnalysis.value = await quantApi.profit(trendRangeParams())
  } finally {
    trendLoading.value = false
  }
}

function selectTrendRange(range: TrendRange) {
  if (activeRange.value === range) return
  activeRange.value = range
  void loadReturnTrend()
}

function selectBenchmarkIndex(indexCode: BenchmarkIndex) {
  if (activeIndexCode.value === indexCode) return
  activeIndexCode.value = indexCode
  void loadReturnTrend()
}

function isAShareIntradayLabel(label: string) {
  const [hour, minute] = label.split(':').map(Number)
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return false
  const minutes = hour * 60 + minute
  return (minutes >= 9 * 60 + 30 && minutes <= 11 * 60 + 30)
    || (minutes >= 13 * 60 && minutes <= 15 * 60)
}

function displaySignalType(type: string) {
  const labels: Record<string, string> = {
    POSITION_MONITOR: '仓位监控',
    TAKE_PROFIT: '止盈回撤',
    LOW_BUY: '低吸观察',
    RISK_ALERT: '风险预警',
    POSITION_RISK: '仓位风险',
    MARKET_RISK: '市场风险',
    CLASSIFICATION: '基金分类',
    ADD_POSITION: '加仓信号',
    WATCH: '观察信号'
  }
  return labels[type] || type
}

function displayRiskLevel(level: string) {
  const labels: Record<string, string> = {
    HIGH: '高风险',
    MEDIUM: '中风险',
    LOW: '低风险'
  }
  return labels[level] || level
}

function riskLevelClass(level: string) {
  return level === 'HIGH' ? 'risk-level-high' : ''
}

function displaySignalFund(signal: { fundCode: string; fundName?: string }) {
  if (signal.fundName && signal.fundName !== signal.fundCode) {
    return `${signal.fundCode} · ${signal.fundName}`
  }
  return signal.fundCode
}

function displaySuggestionFund(item: { fundCode: string; fundName?: string }) {
  const fundName = item.fundName || holdingNameByCode.value.get(item.fundCode)
  if (fundName && fundName !== item.fundCode) {
    return `${item.fundCode} · ${fundName}`
  }
  return item.fundCode
}

function displayAlertFund(alert: { fundCode: string; fundName?: string }) {
  if (alert.fundName && alert.fundName !== alert.fundCode) {
    return `${alert.fundCode} · ${alert.fundName}`
  }
  return alert.fundCode
}

function alertTimeText(time: string) {
  return time ? time.slice(11, 16) : '--:--'
}
function updatedBadgeText(date?: string | null) {
  if (!date) return '已更新'
  const today = new Date().toISOString().slice(0, 10)
  return date === today ? '已更新' : `已更新至 ${date.slice(5)}`
}

function relatedThemeText(theme?: string | null) {
  return theme && theme !== '主动权益' ? theme : '重仓板块待同步'
}

async function loadMarketStatus() {
  try {
    return await quantApi.marketStatus()
  } catch {
    return null
  }
}

function aShareMarket(status: MarketSessionStatus | null) {
  return status?.markets.find((item) => item.market === 'A股') || null
}

function isAShareTrading(status: MarketSessionStatus | null) {
  return aShareMarket(status)?.trading === true
}

function isAShareEstimateRefreshAllowed(status: MarketSessionStatus | null) {
  if (isAShareTrading(status)) return true
  if (!status) return false
  const now = new Date()
  const day = now.getDay()
  if (day === 0 || day === 6) return false
  const minutes = now.getHours() * 60 + now.getMinutes()
  return minutes >= 9 * 60 + 30 && minutes <= 15 * 60
}

function aShareStatusText(status: MarketSessionStatus | null) {
  return aShareMarket(status)?.statusText || 'A股市场状态未同步'
}

async function refreshEstimate() {
  const holdings = overview.value?.topHoldings || []
  if (!holdings.length) {
    ElMessage.warning('暂无可刷新的持仓基金')
    return
  }
  if (Date.now() - lastEstimateRefreshAt < ESTIMATE_REFRESH_COOLDOWN_MS) {
    await store.fetchOverview()
    await loadReturnTrend()
    ElMessage.info('刚刚刷新过，已使用最新数据')
    return
  }
  lastEstimateRefreshAt = Date.now()
  refreshing.value = true
  try {
    const marketStatus = await loadMarketStatus()
    const officialHoldings = await syncOfficialNavWhenAllowed()
    const officialUpdatedCount = officialHoldings.filter((holding) => holding.officialNavUpdated).length
    if (!isAShareEstimateRefreshAllowed(marketStatus)) {
      await store.fetchOverview()
      if (officialUpdatedCount) {
        ElMessage.success(`已同步 ${officialUpdatedCount} 只基金最新正式净值，并按最终净值重算收益`)
      } else {
        ElMessage.warning(`当前${aShareStatusText(marketStatus)}，暂不刷新盘中估值`)
      }
      return
    }
    const officialUpdatedFundCodes = new Set(
      officialHoldings
        .filter((holding) => holding.officialNavUpdated)
        .map((holding) => holding.fundCode)
    )
    const pendingHoldings = holdings.filter((holding) => !officialUpdatedFundCodes.has(holding.fundCode))
    const results = await Promise.allSettled(pendingHoldings.map(async (holding) => {
      await quantApi.refreshEstimate(holding.fundCode).catch(() => undefined)
      await quantApi.recalculateHolding(holding.id)
    }))
    await store.fetchOverview()
    await loadReturnTrend()
    const successCount = results.filter((result) => result.status === 'fulfilled').length
    const failedCount = results.length - successCount
    if (officialUpdatedCount) {
      ElMessage.success(`已同步 ${officialUpdatedCount} 只基金最新正式净值，并按最终净值重算收益${failedCount ? `；${failedCount} 只估值暂未更新` : ''}`)
    } else if (successCount) {
      ElMessage.success(`今日正式净值暂未披露，已刷新 ${successCount} 只基金盘中估值${failedCount ? `，${failedCount} 只暂未更新` : ''}`)
    } else {
      ElMessage.warning('今日正式净值暂未披露，盘中估值也暂未刷新成功，请稍后重试')
    }
  } finally {
    refreshing.value = false
  }
}

function handleHeaderRefreshEstimate(event: Event) {
  const detail = (event as CustomEvent<{ handled?: boolean; complete?: () => void }>).detail
  if (detail) detail.handled = true
  void waitForInitialOverview()
    .then(refreshEstimate)
    .catch(() => undefined)
    .finally(() => detail?.complete?.())
}

async function waitForInitialOverview() {
  if (initialOverviewPromise) {
    await initialOverviewPromise
  }
}

async function syncOfficialNavWhenAllowed() {
  if (Date.now() - lastOfficialNavSyncAt < OFFICIAL_NAV_SYNC_COOLDOWN_MS) {
    return []
  }
  try {
    const holdings = await quantApi.syncOfficialNav()
    lastOfficialNavSyncAt = Date.now()
    return holdings
  } catch (error) {
    if (isRepeatSubmitError(error)) {
      lastOfficialNavSyncAt = Date.now()
      return []
    }
    throw error
  }
}

function isRepeatSubmitError(error: unknown) {
  return typeof error === 'object'
    && error !== null
    && 'response' in error
    && (error as { response?: { status?: number } }).response?.status === 409
}

async function autoRefreshEstimate() {
  if (refreshing.value || !overview.value?.topHoldings.length) return
  if (Date.now() - lastEstimateRefreshAt < ESTIMATE_REFRESH_COOLDOWN_MS) return
  const marketStatus = await loadMarketStatus()
  if (!isAShareEstimateRefreshAllowed(marketStatus)) return
  lastEstimateRefreshAt = Date.now()
  refreshing.value = true
  try {
    const officialHoldings = await syncOfficialNavWhenAllowed()
    const officialUpdatedFundCodes = new Set(
      officialHoldings
        .filter((holding) => holding.officialNavUpdated)
        .map((holding) => holding.fundCode)
    )
    const pendingHoldings = overview.value.topHoldings.filter((holding) => !officialUpdatedFundCodes.has(holding.fundCode))
    await Promise.allSettled(pendingHoldings.map(async (holding) => {
      await quantApi.refreshEstimate(holding.fundCode).catch(() => undefined)
      await quantApi.recalculateHolding(holding.id)
    }))
    await store.fetchOverview()
    await loadReturnTrend()
  } finally {
    refreshing.value = false
  }
}

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <LoadingState v-if="store.loading && !overview" />
  <div v-else-if="overview && summary" class="screen-grid dashboard-grid">
    <div class="metric-row dashboard-kpis">
      <MetricTile label="总资产（元）" :value="money(summary.totalAsset)" sub-label="总投入" :delta="money(summary.totalInvestAmount)" tone="neutral" />
      <MetricTile label="总收益（元）" :value="signed(summary.currentProfit)" sub-label="总收益率" :delta="percent(summary.currentProfitRate)" :tone="metricTone(summary.currentProfit)" />
      <MetricTile label="当日收益（元）" :value="signed(summary.dailyProfit)" sub-label="当日收益率" :delta="percent(dailyProfitRate)" :tone="metricTone(summary.dailyProfit)" />
      <MetricTile label="权益仓位" :value="percent(summary.equityPositionRate, 2)" sub-label="债券/现金" :delta="`${percent(summary.bondPositionRate, 2)} / ${percent(summary.cashPositionRate, 2)}`" tone="neutral" />
    </div>

    <div class="dashboard-workspace">
      <div class="dashboard-primary-column">
        <section class="panel dashboard-holdings-panel">
          <div class="panel-header">
            <h2 class="panel-title">自选/持仓监控</h2>
            <button class="panel-link" @click="go('/holdings')">更多 ›</button>
          </div>
          <div class="panel-body">
            <table v-if="overview.topHoldings.length" class="terminal-table dashboard-holdings-table">
              <thead>
                <tr>
                  <th style="width: 70px;">代码</th>
                  <th>基金名称</th>
                  <th>当日收益</th>
                  <th>关联板块/收益率</th>
                  <th>持有收益/收益率</th>
                  <th>持仓占比</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="holding in overview.topHoldings" :key="holding.id">
                  <td>{{ holding.fundCode }}</td>
                  <td>
                    <div class="holding-name-cell">
                      <span>{{ holding.fundName }}</span>
                      <div class="holding-meta-row">
                        <strong v-if="holding.officialNavUpdated" class="updated-badge">{{ updatedBadgeText(holding.officialNavDate) }}</strong>
                        <strong class="holding-amount-badge">￥{{ money(holding.holdingAmount) }}</strong>
                      </div>
                    </div>
                  </td>
                  <td :class="toneClass(holding.dailyProfit)">{{ signed(holding.dailyProfit) }}</td>
                  <td>
                    <div class="metric-pair">
                      <strong>{{ relatedThemeText(holding.relatedThemeName) }}</strong>
                      <span :class="toneClass(holding.relatedThemeRate || 0)">{{ percent(holding.relatedThemeRate || 0) }}</span>
                    </div>
                  </td>
                  <td>
                    <div class="metric-pair">
                      <strong :class="toneClass(holding.holdingProfit)">{{ signed(holding.holdingProfit) }}</strong>
                      <span :class="toneClass(holding.holdingProfitRate)">{{ percent(holding.holdingProfitRate) }}</span>
                    </div>
                  </td>
                  <td>{{ percent(holding.positionRate || safeRatio(holding.holdingAmount, summary.totalAsset)) }}</td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-cta">
              <EmptyState title="还没有持仓基金" description="先搜索基金名称或代码，把关注基金加入持仓。" />
              <button class="primary-button" @click="go('/holdings')">去添加基金</button>
            </div>
          </div>
        </section>

        <section class="panel trend-panel return-trend-panel">
          <div class="return-trend-tabs">
            <button
              v-for="range in trendRanges"
              :key="range.value"
              :class="{ active: activeRange === range.value }"
              type="button"
              @click="selectTrendRange(range.value)"
            >
              {{ range.label }}
            </button>
          </div>
          <div class="panel-header">
            <div>
              <h2 class="panel-title return-trend-title">收益走势</h2>
              <span class="item-meta">{{ trendStatusText }}</span>
            </div>
            <button class="panel-link" @click="go('/profit-analysis')">更多 ›</button>
          </div>
          <div class="panel-body return-trend-body">
            <div class="return-trend-legend">
              <span class="legend-item mine"><i></i>我的收益 <strong :class="toneClass(currentPortfolioReturn)">{{ percent(currentPortfolioReturn, 2) }}</strong></span>
              <div class="benchmark-switch" aria-label="收益对比指数">
                <button
                  v-for="index in benchmarkIndices"
                  :key="index.value"
                  :class="{ active: activeIndexCode === index.value }"
                  type="button"
                  @click="selectBenchmarkIndex(index.value)"
                >
                  <span class="legend-item index"><i></i>{{ index.label }}</span>
                  <strong v-if="activeIndexCode === index.value" :class="toneClass(currentIndexReturn || 0)">
                    {{ currentIndexReturn === null ? '--' : percent(currentIndexReturn, 2) }}
                  </strong>
                </button>
              </div>
            </div>
            <BaseChart :option="trendOption" :height="214" />
            <div class="return-trend-summary">
              <span>当日收益率：<strong :class="toneClass(dailyProfitRate)">{{ percent(dailyProfitRate, 2) }}</strong></span>
              <span>跑赢{{ activeIndexName }}：<strong :class="toneClass(excessReturn || 0)">{{ excessReturn === null ? '--' : percent(excessReturn, 2) }}</strong></span>
            </div>
            <div v-if="trendLoading" class="return-trend-loading">同步真实收益数据中...</div>
          </div>
        </section>
      </div>

      <div class="dashboard-market-column">
    <section class="panel ai-panel">
      <div class="panel-header">
        <h2 class="panel-title">AI 今日建议</h2>
        <button class="panel-link" @click="go('/ai-analysis')">更多 ›</button>
      </div>
      <div class="panel-body ai-panel-body">
        <DisclaimerBar />
        <div class="suggestion-scroll">
          <article v-for="item in overview.todayAiSuggestions" :key="item.id" class="suggestion-item">
            <div class="item-title">
              <ActionTag :action="item.action" :text="item.actionText" />
              <span class="suggestion-time">{{ item.analysisTime.slice(11, 16) }}</span>
            </div>
            <div class="item-copy suggestion-strategy">{{ displaySuggestionFund(item) }} · {{ item.strategy }}</div>
            <div class="item-meta suggestion-conclusion">{{ item.finalConclusion }} 置信度：{{ percent(item.confidence * 100, 0) }}</div>
          </article>
          <EmptyState v-if="!overview.todayAiSuggestions.length" title="暂无 AI 建议" description="生成新的 AI 分析后会在此展示。" />
        </div>
        <div class="ai-panel-footer">
          <button class="muted-link" @click="go('/ai-analysis')">查看全部建议 ›</button>
          <div class="mini-stat-grid">
            <div><span>买卖/转换</span><strong>{{ buySellSuggestionCount }}</strong></div>
            <div><span>观察/持有</span><strong>{{ watchSuggestionCount }}</strong></div>
            <div><span>覆盖持仓</span><strong>{{ overview.todayAiSuggestions.length }}</strong></div>
          </div>
          <div class="item-copy">
            {{ overview.disclaimer }}，最终买卖由用户在原平台手动确认。
          </div>
        </div>
      </div>
    </section>
      </div>

      <div class="dashboard-signal-column">
    <section class="panel strategy-panel">
      <div class="panel-header">
        <h2 class="panel-title">策略信号（实时）</h2>
        <button class="panel-link" @click="go('/strategy-config')">更多 ›</button>
      </div>
      <div class="panel-body signal-list signal-list--compact">
        <article v-for="signalGroup in visibleStrategySignalGroups" :key="signalGroup.key" class="signal-item signal-item--compact">
          <div class="item-title signal-item-title">
            <span>{{ signalGroup.latest.signalTime.slice(11, 16) }} · {{ displaySignalType(signalGroup.latest.signalType) }}</span>
            <div class="signal-title-actions">
              <ActionTag :action="signalGroup.latest.action" :text="signalGroup.latest.actionText" />
            </div>
          </div>
          <div class="signal-meta-row">
            <span>{{ displaySignalFund(signalGroup.latest) }}</span>
            <span>强度 {{ percent(signalGroup.latest.confidence * 100, 0) }}</span>
            <span :class="riskLevelClass(signalGroup.latest.riskLevel)">{{ displayRiskLevel(signalGroup.latest.riskLevel) }}</span>
          </div>
          <div v-if="signalGroup.types.length > 1" class="signal-type-row">
            <span v-for="type in signalGroup.types" :key="type">{{ displaySignalType(type) }}</span>
          </div>
          <div class="item-copy signal-reason">{{ signalGroup.latest.reasons[0] }}</div>
        </article>
        <button v-if="strategySignalCollapsed" class="panel-link signal-toggle" type="button" @click="showAllStrategySignals = !showAllStrategySignals">
          {{ showAllStrategySignals ? '收起' : `展开全部（${strategySignalGroups.length}）` }}
        </button>
        <DisclaimerBar />
        <div class="mini-stat-grid">
          <div><span>活跃信号</span><strong>{{ activeSignalCount }}</strong></div>
          <div><span>高风险</span><strong>{{ highRiskSignalCount }}</strong></div>
          <div><span>监控基金</span><strong>{{ summary.holdingCount }}</strong></div>
        </div>
        <div class="item-copy">
          策略信号用于盘中复盘，不触发真实下单。
        </div>
      </div>
    </section>

    <section class="panel risk-panel">
      <div class="panel-header">
        <h2 class="panel-title">风险预警</h2>
        <button class="panel-link" @click="go('/ai-analysis')">更多 ›</button>
      </div>
      <div class="panel-body alert-list">
        <div class="disclaimer-bar">{{ overview.disclaimer }} · 共 {{ overview.riskAlertCount }} 条预警</div>
        <article v-for="alert in overview.riskAlerts" :key="alert.id" class="alert-item">
          <div class="item-title">
            <span>{{ alert.title }}</span>
            <span>{{ alertTimeText(alert.alertTime) }}</span>
          </div>
          <div class="signal-meta-row">
            <span>{{ displayAlertFund(alert) }}</span>
            <span>{{ alert.alertType }}</span>
            <span :class="riskLevelClass(alert.riskLevel)">{{ displayRiskLevel(alert.riskLevel) }}</span>
          </div>
          <div class="item-copy">{{ alert.content }}</div>
        </article>
        <EmptyState v-if="!overview.riskAlerts.length" title="暂无风险预警" description="策略信号或 AI 分析触发风险项后会在此展示。" />
      </div>
    </section>
      </div>
    </div>
  </div>
</template>
