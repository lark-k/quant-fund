<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { quantApi } from '@/api/quant'
import BaseChart from '@/components/charts/BaseChart.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import MetricTile from '@/components/common/MetricTile.vue'
import { fundNavOption } from '@/components/charts/chartOptions'
import { metricTone, money, percent, signed, toneClass } from '@/utils/format'
import type { FundBasicInfo, FundEstimate, FundHolding, FundNavPoint, FundPeerRank, FundStockHolding, FundTheme, TradeRecord } from '@/types/domain'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const refreshing = ref(false)
const generating = ref(false)
const holding = ref<FundHolding>()
const basicInfo = ref<FundBasicInfo>()
const estimate = ref<FundEstimate | null>(null)
const navPoints = ref<FundNavPoint[]>([])
const tradePoints = ref<TradeRecord[]>([])
const heavyStocks = ref<FundStockHolding[]>([])
const themes = ref<FundTheme[]>([])
const peerRank = ref<FundPeerRank | null>(null)

const fundCode = computed(() => String(route.query.fundCode || holding.value?.fundCode || ''))
const chart = computed(() => fundNavOption(navPoints.value, tradePoints.value))
const effectiveEstimateRate = computed(() => holding.value?.relatedThemeRate ?? estimate.value?.estimateGrowthRate ?? 0)
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

onMounted(loadDetail)
watch(() => route.fullPath, loadDetail)

function navText(value: number | null | undefined) {
  return value === null || value === undefined ? '--' : value.toFixed(4)
}

function nullablePercent(value: number | null | undefined, digits = 2) {
  return value === null || value === undefined ? '--' : percent(value, digits)
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
  tradePoints.value = []
  heavyStocks.value = []
  themes.value = []
  peerRank.value = null
}

async function quiet<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request
  } catch {
    return null
  }
}

async function loadDetail() {
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
    const code = matchedHolding?.fundCode || queryCode || ''
    if (!code) {
      resetDetailState()
      return
    }
    const [infoResult, estimateResult, nav, stocks, themeList, rank, tradeList] = await Promise.all([
      quiet(quantApi.fundBasicInfo(code)),
      quiet(quantApi.fundEstimate(code)),
      quiet(quantApi.fundNav(code)),
      quiet(quantApi.heavyStocks(code)),
      quiet(quantApi.themes(code)),
      quiet(quantApi.peerRank(code)),
      quiet(quantApi.trades())
    ])
    const info = infoResult || {
      fundCode: code,
      fundName: matchedHolding?.fundName || code,
      fundType: matchedHolding?.fundType || 'UNKNOWN',
      activeFund: matchedHolding?.activeFund || false
    }
    basicInfo.value = info
    estimate.value = estimateResult
    navPoints.value = (nav || [])
      .slice()
      .sort((left, right) => left.date.localeCompare(right.date))
      .slice(-260)
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
      disclaimer: ''
    }
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

async function generateAiAnalysis() {
  if (!holding.value || !holding.value.id) {
    ElMessage.warning('请先将基金加入持仓后再生成 AI 分析')
    return
  }
  generating.value = true
  try {
    await quantApi.generateAiAnalysis(holding.value.id)
    ElMessage.success('AI 分析已生成')
    router.push({ path: '/ai-analysis', query: { holdingId: holding.value.id, fundCode: holding.value.fundCode } })
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
          <button class="primary-button" :disabled="generating" @click="generateAiAnalysis">{{ generating ? '生成中' : '生成 AI 分析' }}</button>
        </div>
      </div>
      <div class="panel-body">
        <div class="detail-hero">
          <div>
            <div class="fund-code">{{ basicInfo?.fundType || holding.fundType }} / {{ managerText }}</div>
            <p>当日收益按关联板块或真实重仓行情估算；正式净值以基金公司晚间披露为准。</p>
          </div>
          <div class="fund-badges">
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
          <MetricTile label="持有金额" :value="money(holding.holdingAmount)" />
          <MetricTile label="持有份额" :value="money(holding.holdingShare, 2)" />
          <MetricTile label="持仓占比" :value="percent(holding.positionRate || 0)" />
          <MetricTile label="持仓成本" :value="money(holding.holdingCost)" />
          <MetricTile label="持有收益" :value="signed(holding.holdingProfit)" :delta="percent(holding.holdingProfitRate)" :tone="metricTone(holding.holdingProfit)" />
          <MetricTile label="当日收益" :value="signed(holding.dailyProfit)" :delta="holding.updateTime || estimate?.estimateTime || '--'" :tone="metricTone(holding.dailyProfit)" />
          <MetricTile label="昨日收益" :value="signed(holding.yesterdayProfit || 0)" :tone="metricTone(holding.yesterdayProfit || 0)" />
          <MetricTile label="持有天数" :value="`${holding.holdingDays} 天`" />
          <MetricTile label="最大回撤" :value="percent(maxDrawdown)" sub-label="净值曲线估算" tone="fall" />
          <MetricTile label="同类排名" :value="rankText" :delta="peerRank?.percentile !== undefined ? `百分位 ${percent(peerRank.percentile, 1)}` : peerRank?.category || ''" tone="info" />
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">净值走势</h2>
        <span class="item-meta">本基金 / 沪深300 / 买卖点；红点买入，绿点卖出</span>
      </div>
      <div class="panel-body">
        <BaseChart v-if="navPoints.length >= 2" :option="chart" :height="340" />
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
