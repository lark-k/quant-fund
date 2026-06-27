<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { quantApi } from '@/api/quant'
import BaseChart from '@/components/charts/BaseChart.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import MetricTile from '@/components/common/MetricTile.vue'
import { fundNavOption } from '@/components/charts/chartOptions'
import { formatDateTime, metricTone, money, percent, signed, toneClass } from '@/utils/format'
import { DISCLAIMER } from '@/types/domain'
import type { FundBasicInfo, FundEstimate, FundHolding, FundNavPoint, FundPeerRank, FundStockHolding, FundTheme, TradeRecord } from '@/types/domain'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const refreshing = ref(false)
const generating = ref(false)
const addingHolding = ref(false)
const holding = ref<FundHolding>()
const basicInfo = ref<FundBasicInfo>()
const estimate = ref<FundEstimate | null>(null)
const navPoints = ref<FundNavPoint[]>([])
type NavCacheEntry = {
  points: FundNavPoint[]
  fetchedAt: number
  latestDate: string
}
const navCache = ref<Record<string, NavCacheEntry>>({})
const tradePoints = ref<TradeRecord[]>([])
const heavyStocks = ref<FundStockHolding[]>([])
const themes = ref<FundTheme[]>([])
const peerRank = ref<FundPeerRank | null>(null)
const hasMatchedHolding = ref(false)
const infoLoadFailed = ref(false)
const activeNavRange = ref('1M')
const selectedIndexCode = ref('000300')
const NAV_CACHE_TTL_MS = 2 * 60 * 1000
const NAV_REFRESH_INTERVAL_MS = 2 * 60 * 1000

const navRangeOptions = [
  { label: '近1月', value: '1M', months: 1 },
  { label: '近3月', value: '3M', months: 3 },
  { label: '近6月', value: '6M', months: 6 },
  { label: '近1年', value: '1Y', months: 12 },
  { label: '近3年', value: '3Y', months: 36 }
]

const indexOptions = [
  { label: '沪深300', value: '000300' },
  { label: '创业板指', value: '399006' },
  { label: '上证指数', value: '000001' },
  { label: '深证成指', value: '399001' }
]

const fundCode = computed(() => String(route.query.fundCode || holding.value?.fundCode || ''))
const selectedIndexName = computed(() => indexOptions.find((item) => item.value === selectedIndexCode.value)?.label || '沪深300')
const chartNavPoints = computed(() => filterNavPoints(navPoints.value, activeNavRange.value))
const chartKey = computed(() => {
  const first = chartNavPoints.value[0]?.date || ''
  const last = chartNavPoints.value[chartNavPoints.value.length - 1]?.date || ''
  return `${fundCode.value}-${activeNavRange.value}-${selectedIndexCode.value}-${chartNavPoints.value.length}-${first}-${last}`
})
const costNav = computed(() => {
  if (!hasMatchedHolding.value || !holding.value || holding.value.holdingShare <= 0 || holding.value.holdingCost <= 0) return null
  return holding.value.holdingCost / holding.value.holdingShare
})
const latestFundReturn = computed(() => {
  const points = chartNavPoints.value
  if (points.length < 2) return null
  const firstNav = points[0].nav
  const latestNav = points[points.length - 1].nav
  return firstNav > 0 ? (latestNav - firstNav) / firstNav * 100 : null
})
const latestIndexReturn = computed(() => {
  const rates = chartNavPoints.value
    .map((point) => point.indexReturnRate)
    .filter((value): value is number => value !== null && value !== undefined)
  if (!rates.length) return null
  const base = rates[0]
  const latest = rates[rates.length - 1]
  const baseFactor = 1 + base / 100
  return baseFactor > 0 ? ((1 + latest / 100) / baseFactor - 1) * 100 : latest
})
const chart = computed(() => fundNavOption(chartNavPoints.value, tradePoints.value, {
  costNav: costNav.value,
  indexName: selectedIndexName.value,
  showLegend: false
}))
const effectiveEstimateRate = computed(() => holding.value?.relatedThemeRate ?? estimate.value?.estimateGrowthRate ?? 0)
const canGenerateAiAnalysis = computed(() => Boolean(hasMatchedHolding.value && holding.value?.id))
const detailStatusText = computed(() => {
  if (hasMatchedHolding.value) return '已加入持仓'
  if (infoLoadFailed.value) return '基础资料同步失败'
  return '未加入持仓，仅展示真实数据源详情'
})
const detailStatusDescription = computed(() => {
  if (hasMatchedHolding.value) return '我的收益、成本和持有天数来自持仓与交易流水。'
  if (infoLoadFailed.value) return '当前仅能使用路由基金代码和已返回的行情数据展示，建议稍后重试或检查数据源健康状态。'
  return '该基金尚未加入持仓，持有金额、收益、成本、持有天数等个人指标不展示。'
})
const oneYearReturn = computed(() => {
  if (navPoints.value.length < 2) return holding.value?.holdingProfitRate || 0
  const first = navPoints.value[0].nav
  const last = navPoints.value[navPoints.value.length - 1].nav
  return first > 0 ? (last - first) / first * 100 : 0
})
const maxDrawdown = computed(() => {
  let peak = navPoints.value[0]?.nav || 1
  return navPoints.value.reduce((min, point) => {
    peak = Math.max(peak, point.nav)
    return Math.min(min, (point.nav - peak) / peak * 100)
  }, 0)
})
const managerText = computed(() => basicInfo.value?.managerName || '基金经理待同步')
const rankText = computed(() => {
  if (!peerRank.value) return '--'
  if (peerRank.value.rank && peerRank.value.total) return `${peerRank.value.rank}/${peerRank.value.total}`
  return peerRank.value.rankText || '--'
})
let navRequestSeq = 0
let navRefreshTimer: ReturnType<typeof window.setInterval> | null = null

onMounted(loadDetail)
onBeforeUnmount(stopNavRefreshTimer)
watch(() => route.fullPath, loadDetail)
watch(selectedIndexCode, () => loadSelectedIndexNav(false))

function navText(value: number | null | undefined) {
  return value === null || value === undefined ? '--' : value.toFixed(4)
}

function nullablePercent(value: number | null | undefined, digits = 2) {
  return value === null || value === undefined ? '--' : percent(value, digits)
}

function legendTone(value: number | null | undefined) {
  if (value === null || value === undefined) return 'neutral'
  return value >= 0 ? 'rise' : 'fall'
}

function isoDate(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function navHistoryStartDate(range = activeNavRange.value) {
  const option = navRangeOptions.find((item) => item.value === range) || navRangeOptions[0]
  const date = new Date()
  date.setMonth(date.getMonth() - option.months)
  date.setDate(date.getDate() - 10)
  return isoDate(date)
}

function navHistoryEndDate() {
  return isoDate(new Date())
}

function filterNavPoints(points: FundNavPoint[], range: string) {
  if (!points.length) return []
  const option = navRangeOptions.find((item) => item.value === range) || navRangeOptions[0]
  const lastDate = new Date(`${points[points.length - 1].date}T00:00:00`)
  const start = new Date(lastDate)
  start.setMonth(start.getMonth() - option.months)
  const filtered = points.filter((point) => new Date(`${point.date}T00:00:00`) >= start)
  return filtered.length >= 2 ? filtered : points.slice(-2)
}

function sortNavPoints(points: FundNavPoint[]) {
  return points
    .slice()
    .sort((left, right) => left.date.localeCompare(right.date))
}

function latestNavDate(points: FundNavPoint[]) {
  return points[points.length - 1]?.date || ''
}

function freshCacheEntry(indexCode: string) {
  const entry = navCache.value[indexCode]
  if (!entry?.points.length) return null
  return Date.now() - entry.fetchedAt <= NAV_CACHE_TTL_MS ? entry : null
}

function cacheNav(indexCode: string, points: FundNavPoint[]) {
  const sorted = sortNavPoints(points)
  const nextEntry = {
    points: sorted,
    fetchedAt: Date.now(),
    latestDate: latestNavDate(sorted)
  }
  const currentLatestDate = latestNavDate(navPoints.value)
  navCache.value = currentLatestDate && nextEntry.latestDate && nextEntry.latestDate !== currentLatestDate
    ? { [indexCode]: nextEntry }
    : { ...navCache.value, [indexCode]: nextEntry }
  return nextEntry
}

function stopNavRefreshTimer() {
  if (navRefreshTimer !== null) {
    window.clearInterval(navRefreshTimer)
    navRefreshTimer = null
  }
}

function startNavRefreshTimer() {
  stopNavRefreshTimer()
  navRefreshTimer = window.setInterval(() => {
    void loadSelectedIndexNav(true)
  }, NAV_REFRESH_INTERVAL_MS)
}

async function loadSelectedIndexNav(force = false) {
  const code = fundCode.value
  if (!code) return
  const indexCode = selectedIndexCode.value
  const cached = freshCacheEntry(indexCode)
  if (!force && cached) {
    navPoints.value = cached.points
    return
  }
  const requestSeq = ++navRequestSeq
  const nav = await quiet(quantApi.fundNav(code, {
    startDate: navHistoryStartDate('3Y'),
    endDate: navHistoryEndDate(),
    indexCode
  }))
  const entry = cacheNav(indexCode, nav || [])
  if (requestSeq === navRequestSeq && selectedIndexCode.value === indexCode) {
    navPoints.value = entry.points
  }
}

function prefetchIndexNavs(code: string) {
  for (const option of indexOptions) {
    if (option.value === selectedIndexCode.value || freshCacheEntry(option.value)) continue
    void quantApi.fundNav(code, {
      startDate: navHistoryStartDate('3Y'),
      endDate: navHistoryEndDate(),
      indexCode: option.value
    }).then((nav) => {
      if (fundCode.value !== code) return
      cacheNav(option.value, nav)
    }).catch(() => undefined)
  }
}

function stockRatio(stock: FundStockHolding) {
  return stock.positionRate ?? stock.holdingRatio ?? 0
}

function relatedThemeText(theme?: string | null) {
  return theme && theme !== '主动权益' ? theme : '重仓板块待同步'
}

function resetDetailState() {
  holding.value = undefined
  basicInfo.value = undefined
  estimate.value = null
  navPoints.value = []
  navCache.value = {}
  tradePoints.value = []
  heavyStocks.value = []
  themes.value = []
  peerRank.value = null
  hasMatchedHolding.value = false
  infoLoadFailed.value = false
}

async function quiet<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request
  } catch {
    return null
  }
}

async function loadDetail() {
  stopNavRefreshTimer()
  loading.value = true
  try {
    const holdings = await quiet(quantApi.holdings()) || []
    const queryHoldingId = Number(route.query.holdingId)
    const queryCode = String(route.query.fundCode || '')
    let matchedHolding = Number.isFinite(queryHoldingId) && queryHoldingId > 0
      ? holdings.find((item) => item.id === queryHoldingId)
      : undefined
    if (!matchedHolding && queryCode) {
      matchedHolding = holdings.find((item) => item.fundCode === queryCode)
    }
    if (!matchedHolding && !queryCode) {
      matchedHolding = holdings[0]
    }
    hasMatchedHolding.value = Boolean(matchedHolding)
    const code = matchedHolding?.fundCode || queryCode || ''
    if (!code) {
      resetDetailState()
      return
    }
    const [infoResult, estimateResult, nav, stocks, themeList, rank, tradeList] = await Promise.all([
      quiet(quantApi.fundBasicInfo(code)),
      quiet(quantApi.fundEstimate(code)),
      quiet(quantApi.fundNav(code, {
        startDate: navHistoryStartDate('3Y'),
        endDate: navHistoryEndDate(),
        indexCode: selectedIndexCode.value
      })),
      quiet(quantApi.heavyStocks(code)),
      quiet(quantApi.themes(code)),
      quiet(quantApi.peerRank(code)),
      quiet(quantApi.trades())
    ])
    infoLoadFailed.value = !infoResult
    const info = infoResult || {
      fundCode: code,
      fundName: matchedHolding?.fundName || code,
      fundType: matchedHolding?.fundType || 'UNKNOWN',
      activeFund: matchedHolding?.activeFund || false
    }
    basicInfo.value = info
    estimate.value = estimateResult
    const sortedNav = sortNavPoints(nav || [])
    navPoints.value = sortedNav
    cacheNav(selectedIndexCode.value, sortedNav)
    tradePoints.value = (tradeList || []).filter((trade) => trade.fundCode === code)
    heavyStocks.value = stocks || []
    themes.value = themeList || []
    peerRank.value = rank
    holding.value = matchedHolding || {
      id: 0,
      accountId: 0,
      fundCode: code,
      fundName: info.fundName,
      fundType: info.fundType,
      activeFund: Boolean(info.activeFund),
      holdingAmount: 0,
      holdingShare: 0,
      holdingCost: 0,
      currentEstimateNav: estimateResult?.estimateNav ?? null,
      latestOfficialNav: navPoints.value[navPoints.value.length - 1]?.nav ?? null,
      holdingProfit: 0,
      holdingProfitRate: 0,
      dailyProfit: 0,
      yesterdayProfit: 0,
      positionRate: 0,
      currentEstimateGrowthRate: estimateResult?.estimateGrowthRate ?? 0,
      officialNavUpdated: false,
      officialNavDate: null,
      relatedThemeName: themes.value[0]?.themeName || info.trackingIndex || '重仓板块待同步',
      relatedThemeRate: themes.value[0]?.estimatedRate ?? estimateResult?.estimateGrowthRate ?? 0,
      valuationSource: themes.value[0]?.sourceName || estimateResult?.sourceName || 'EAST_MONEY',
      estimateBasis: themes.value.length ? '重仓股占比加权估算' : '基金估值涨跌率',
      marketStatus: info.fundType === 'QDII' ? '海外市场参考' : 'A股交易中',
      holdingDays: 0,
      sourcePlatform: '真实数据源',
      regularInvestment: false,
      coreHolding: false,
      watchFocus: false,
      updateTime: estimateResult?.estimateTime || '',
      disclaimer: DISCLAIMER
    }
    prefetchIndexNavs(code)
    startNavRefreshTimer()
  } finally {
    loading.value = false
  }
}

async function refreshEstimate() {
  if (!holding.value) return
  refreshing.value = true
  try {
    const latest = await quantApi.refreshEstimate(holding.value.fundCode)
    estimate.value = latest
    holding.value = {
      ...holding.value,
      currentEstimateNav: latest.estimateNav,
      currentEstimateGrowthRate: latest.estimateGrowthRate ?? 0,
      updateTime: latest.estimateTime
    }
    ElMessage.success(latest.delayed ? '估值已刷新，数据源提示延迟' : '估值已刷新')
  } finally {
    refreshing.value = false
  }
}

function normalizeFundType(rawType: string) {
  const source = String(rawType || '')
  const value = source.toUpperCase()
  if (value.includes('ETF')) return value.includes('LINK') || source.includes('联接') ? 'ETF_LINK' : 'ETF'
  if (value.includes('INDEX') || source.includes('指数')) return source.includes('增强') ? 'INDEX_ENHANCED' : 'INDEX'
  if (value.includes('BOND') || source.includes('债')) return 'BOND'
  if (value.includes('MONEY') || source.includes('货币')) return 'MONEY_MARKET'
  if (value.includes('QDII') || source.includes('海外') || source.includes('全球')) return 'QDII'
  if (value.includes('MIXED') || source.includes('混合')) return 'MIXED'
  if (value.includes('ACTIVE') || source.includes('主动') || source.includes('股票')) return 'ACTIVE_EQUITY'
  return 'UNKNOWN'
}

async function ensureAccount() {
  const portfolios = await quantApi.portfolios()
  let account = portfolios[0]
  if (!account) {
    account = await quantApi.createPortfolio({
      accountName: '手动基金账户',
      platformType: 'MANUAL',
      maxSingleFundPositionRate: 25
    })
  }
  return account.id
}

async function addCurrentFundToHolding() {
  if (!holding.value || hasMatchedHolding.value) return
  addingHolding.value = true
  try {
    const accountId = await ensureAccount()
    const fundType = normalizeFundType(basicInfo.value?.fundType || holding.value.fundType)
    const saved = await quantApi.createHolding({
      accountId,
      fundCode: holding.value.fundCode,
      fundName: holding.value.fundName,
      fundType,
      activeFund: fundType === 'ACTIVE_EQUITY' || fundType === 'MIXED',
      holdingAmount: 0,
      holdingShare: 0,
      holdingCost: 0,
      currentEstimateNav: holding.value.currentEstimateNav ?? estimate.value?.estimateNav ?? undefined,
      latestOfficialNav: holding.value.latestOfficialNav ?? undefined,
      sourcePlatform: '基金详情加入',
      regularInvestment: false,
      coreHolding: false,
      watchFocus: true
    })
    ElMessage.success('已加入持仓，请继续填写持有金额/收益或份额/成本')
    router.push({ path: '/holding-edit', query: { holdingId: saved.id, fundCode: saved.fundCode } })
  } finally {
    addingHolding.value = false
  }
}

async function generateAiAnalysis() {
  const currentHolding = holding.value
  if (!canGenerateAiAnalysis.value || !currentHolding?.id) {
    ElMessage.warning('请先将基金加入持仓后再生成 AI 分析')
    return
  }
  generating.value = true
  try {
    await quantApi.generateAiAnalysis(currentHolding.id)
    ElMessage.success('AI 分析已生成')
    router.push({ path: '/ai-analysis', query: { holdingId: currentHolding.id, fundCode: currentHolding.fundCode } })
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <LoadingState v-if="loading" text="正在加载基金详情" />
  <EmptyState v-else-if="!holding" title="未找到基金详情" description="请先搜索基金或从持仓列表进入详情。" />
  <div v-else class="screen-grid">
    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">{{ holding.fundName }} · {{ holding.fundCode }}</h2>
        <div class="toolbar-row">
          <button class="ghost-button" :disabled="refreshing" @click="refreshEstimate">{{ refreshing ? '刷新中' : '刷新估值' }}</button>
          <button v-if="!hasMatchedHolding" class="ghost-button" :disabled="addingHolding" @click="addCurrentFundToHolding">{{ addingHolding ? '加入中' : '加入持仓' }}</button>
          <button class="primary-button" :disabled="generating || !canGenerateAiAnalysis" @click="generateAiAnalysis">{{ generating ? '生成中' : '生成 AI 分析' }}</button>
        </div>
      </div>
      <div class="panel-body">
        <div class="detail-hero">
          <div>
            <div class="fund-code">{{ basicInfo?.fundType || holding.fundType }} / {{ managerText }}</div>
            <p>当日收益按关联板块或真实重仓行情估算；正式净值以基金公司晚间披露为准。</p>
            <p class="item-meta">{{ detailStatusDescription }}</p>
          </div>
          <div class="fund-badges">
            <span>{{ detailStatusText }}</span>
            <span>{{ holding.marketStatus || '--' }}</span>
            <span>{{ holding.estimateBasis || '基金估值涨跌率' }}</span>
            <span>{{ holding.valuationSource || estimate?.sourceName || '真实数据源' }}</span>
          </div>
        </div>

        <div class="metric-row">
          <MetricTile label="当日估值" :value="navText(holding.currentEstimateNav ?? estimate?.estimateNav)" :delta="nullablePercent(effectiveEstimateRate)" :tone="metricTone(effectiveEstimateRate)" />
          <MetricTile label="关联板块" :value="relatedThemeText(holding.relatedThemeName)" :delta="nullablePercent(holding.relatedThemeRate)" :tone="metricTone(holding.relatedThemeRate || 0)" />
          <MetricTile label="最新正式净值" :value="navText(holding.latestOfficialNav)" />
          <MetricTile label="近一年收益" :value="percent(oneYearReturn)" sub-label="基于历史净值估算" :tone="metricTone(oneYearReturn)" />
          <MetricTile label="持有金额" :value="hasMatchedHolding ? money(holding.holdingAmount) : '--'" />
          <MetricTile label="持有份额" :value="hasMatchedHolding ? money(holding.holdingShare, 2) : '--'" />
          <MetricTile label="持仓占比" :value="hasMatchedHolding ? percent(holding.positionRate || 0) : '--'" />
          <MetricTile label="成本价" :value="hasMatchedHolding && costNav ? navText(costNav) : '--'" />
          <MetricTile label="持有收益" :value="hasMatchedHolding ? signed(holding.holdingProfit) : '--'" :delta="hasMatchedHolding ? percent(holding.holdingProfitRate) : ''" :tone="hasMatchedHolding ? metricTone(holding.holdingProfit) : 'neutral'" />
          <MetricTile label="当日收益" :value="hasMatchedHolding ? signed(holding.dailyProfit) : '--'" :delta="hasMatchedHolding ? formatDateTime(holding.updateTime || estimate?.estimateTime) : '未加入持仓'" :tone="hasMatchedHolding ? metricTone(holding.dailyProfit) : 'neutral'" />
          <MetricTile label="昨日收益" :value="hasMatchedHolding ? signed(holding.yesterdayProfit || 0) : '--'" :tone="hasMatchedHolding ? metricTone(holding.yesterdayProfit || 0) : 'neutral'" />
          <MetricTile label="持有天数" :value="hasMatchedHolding ? `${holding.holdingDays} 天` : '--'" />
          <MetricTile label="最大回撤" :value="percent(maxDrawdown)" sub-label="净值曲线估算" tone="fall" />
          <MetricTile label="同类排名" :value="rankText" :delta="peerRank?.percentile !== undefined ? `百分位 ${percent(peerRank.percentile, 1)}` : peerRank?.category || ''" tone="info" />
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">净值走势</h2>
      </div>
      <div class="panel-body">
        <div v-if="chartNavPoints.length >= 2" class="fund-chart-wrap">
          <div class="fund-chart-legend">
            <span class="legend-line fund-line"></span>
            <span>本基金</span>
            <strong :class="legendTone(latestFundReturn)">{{ nullablePercent(latestFundReturn, 2) }}</strong>

            <span class="legend-line index-line"></span>
            <label class="legend-index-picker">
              <select v-model="selectedIndexCode" aria-label="切换对比指数">
                <option v-for="item in indexOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
              <span>{{ selectedIndexName }}</span>
              <span class="legend-caret">▼</span>
            </label>
            <strong :class="legendTone(latestIndexReturn)">{{ nullablePercent(latestIndexReturn, 2) }}</strong>

            <span v-if="costNav" class="legend-line cost-line"></span>
            <span v-if="costNav" class="legend-cost">成本价 {{ navText(costNav) }}</span>
          </div>
          <BaseChart :key="chartKey" :option="chart" :height="320" />
        </div>
        <div v-if="navPoints.length >= 2" class="fund-range-tabs">
          <button
            v-for="item in navRangeOptions"
            :key="item.value"
            type="button"
            :class="{ active: activeNavRange === item.value }"
            @click="activeNavRange = item.value"
          >
            {{ item.label }}
          </button>
        </div>
        <EmptyState v-else title="暂无净值走势" description="真实数据源暂未返回足够的历史净值点。" />
      </div>
    </section>

    <div class="insight-grid two">
      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">关联板块 / 估算收益率</h2></div>
        <div class="panel-body tag-cloud">
          <div v-for="theme in themes" :key="theme.themeName" class="theme-chip">
            <span>{{ theme.themeName }}</span>
            <strong :class="toneClass(theme.estimatedRate || 0)">{{ nullablePercent(theme.estimatedRate, 2) }}</strong>
            <small>{{ percent(theme.weight || 0, 1) }}</small>
          </div>
          <EmptyState v-if="!themes.length" title="暂无关联板块" description="真实数据源暂未返回板块或持仓信息。" />
        </div>
      </section>

      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">上季度重仓</h2></div>
        <div class="panel-body">
          <table class="terminal-table">
            <thead><tr><th>代码</th><th>股票</th><th>行业/板块</th><th>涨跌幅</th><th>占净值</th><th>日期</th></tr></thead>
            <tbody>
              <tr v-for="stock in heavyStocks" :key="`${stock.marketSecId || stock.stockCode}-${stock.stockName}`">
                <td>{{ stock.stockCode }}</td>
                <td>{{ stock.stockName }}</td>
                <td>{{ stock.industry || '--' }}</td>
                <td :class="toneClass(stock.changeRate || 0)">{{ nullablePercent(stock.changeRate, 2) }}</td>
                <td>{{ percent(stockRatio(stock), 2) }}</td>
                <td>{{ stock.reportDate || '--' }}</td>
              </tr>
            </tbody>
          </table>
          <EmptyState v-if="!heavyStocks.length" title="暂无重仓数据" description="真实数据源暂未披露该基金持仓。" />
        </div>
      </section>
    </div>

    <DisclaimerBar />
  </div>
</template>
