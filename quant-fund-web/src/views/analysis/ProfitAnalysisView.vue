<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { quantApi } from '@/api/quant'
import type { FundProfitRank, ProfitAnalysis } from '@/types/domain'
import MetricTile from '@/components/common/MetricTile.vue'
import BaseChart from '@/components/charts/BaseChart.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { returnTrendOption } from '@/components/charts/chartOptions'
import { metricTone, money, percent, signed, toneClass } from '@/utils/format'

type TrendRange = 'TODAY' | 'THIS_WEEK' | 'THIS_MONTH' | 'THIS_YEAR' | 'ALL'
type BenchmarkIndex = '000300' | '000001' | '399006'
type ReturnTrendPoint = {
  label: string
  portfolioReturn: number | null
  indexReturn: number | null
  dailyProfit?: number | null
}

const data = ref<ProfitAnalysis>()
const loading = ref(true)
const activeRange = ref<TrendRange>('TODAY')
const activeIndexCode = ref<BenchmarkIndex>('000300')
const intradayTrendPoints = ref<ReturnTrendPoint[]>([])
let refreshTimer: number | undefined

const rangeOptions: Array<{ label: string; value: TrendRange }> = [
  { label: '当日', value: 'TODAY' },
  { label: '本周', value: 'THIS_WEEK' },
  { label: '本月', value: 'THIS_MONTH' },
  { label: '今年', value: 'THIS_YEAR' },
  { label: '全部', value: 'ALL' }
]

const benchmarkIndices: Array<{ label: string; value: BenchmarkIndex }> = [
  { label: '沪深300', value: '000300' },
  { label: '上证指数', value: '000001' },
  { label: '创业板指', value: '399006' }
]

onMounted(() => {
  void loadData()
  refreshTimer = window.setInterval(() => {
    if (activeRange.value === 'TODAY') {
      void loadData(false)
    }
  }, 60000)
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

async function loadData(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    data.value = await quantApi.profit(rangeParams())
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
    } else {
      intradayTrendPoints.value = []
    }
  } finally {
    if (showLoading) loading.value = false
  }
}

const activeIndexName = computed(() => benchmarkIndices.find((item) => item.value === activeIndexCode.value)?.label || '沪深300')
const trendPoints = computed<ReturnTrendPoint[]>(() => {
  if (activeRange.value === 'TODAY') {
    return intradayTrendPoints.value
  }
  return (data.value?.trend || []).map((point) => ({
    label: point.date.slice(5),
    portfolioReturn: cumulativeReturnRate(point),
    indexReturn: point.indexReturnRate ?? null,
    dailyProfit: point.dailyProfit
  }))
})
const chart = computed(() => returnTrendOption(trendPoints.value, activeIndexName.value))
const selectedPeriod = computed(() => data.value?.periodStats.find((item) => item.period === activeRange.value))
const periodRate = (period: string) => data.value?.periodStats.find((item) => item.period === period)?.profitRate || 0
const currentPortfolioReturn = computed(() => {
  if (activeRange.value === 'TODAY') {
    const latest = [...trendPoints.value].reverse().find((point) => point.portfolioReturn !== null)
    return latest?.portfolioReturn ?? periodRate('TODAY')
  }
  return selectedPeriod.value?.profitRate ?? data.value?.selectedRangeProfitRate ?? 0
})
const currentIndexReturn = computed(() => {
  const latest = [...trendPoints.value].reverse().find((point) => point.indexReturn !== null)
  return latest?.indexReturn ?? null
})
const excessReturn = computed(() => currentIndexReturn.value === null ? null : currentPortfolioReturn.value - currentIndexReturn.value)
const chartStatusText = computed(() => activeRange.value === 'TODAY'
  ? '组合总日收益率分时走势'
  : data.value?.indexCompareStatus || '真实区间收益率走势')
const winRate = computed(() => {
  if (!data.value?.trend.length) return 0
  const profitDays = data.value.trend.filter((item) => item.dailyProfit > 0).length
  return profitDays / data.value.trend.length * 100
})
const beatIndexRate = computed(() => data.value?.indexCompare?.available ? data.value.indexCompare.excessReturn : 0)
const indexCompareLabel = computed(() => {
  const compare = data.value?.indexCompare
  if (!compare?.available) return '指数对比'
  return `${compare.excessReturn >= 0 ? '跑赢' : '落后'}${compare.indexName}`
})
const todayTrendPoint = computed(() => {
  if (!data.value?.trend.length) return undefined
  const today = new Date().toISOString().slice(0, 10)
  return data.value.trend.find((item) => item.date === today) || data.value.trend[data.value.trend.length - 1]
})
const todayProfitStatusText = computed(() => todayTrendPoint.value?.profitStatusText || '按已同步收益计算')

function selectRange(range: TrendRange) {
  if (activeRange.value === range) return
  activeRange.value = range
  void loadData()
}

function selectBenchmarkIndex(indexCode: BenchmarkIndex) {
  if (activeIndexCode.value === indexCode) return
  activeIndexCode.value = indexCode
  void loadData()
}

function rangeParams() {
  const now = new Date()
  const start = new Date(now)
  if (activeRange.value === 'TODAY') {
    return { startDate: isoDate(now), endDate: isoDate(now), indexCode: activeIndexCode.value }
  }
  if (activeRange.value === 'THIS_WEEK') {
    const day = now.getDay() || 7
    start.setDate(now.getDate() - day + 1)
    return { startDate: isoDate(start), endDate: isoDate(now), indexCode: activeIndexCode.value }
  }
  if (activeRange.value === 'THIS_MONTH') {
    start.setDate(1)
    return { startDate: isoDate(start), endDate: isoDate(now), indexCode: activeIndexCode.value }
  }
  if (activeRange.value === 'THIS_YEAR') {
    start.setMonth(0, 1)
    return { startDate: isoDate(start), endDate: isoDate(now), indexCode: activeIndexCode.value }
  }
  return { endDate: isoDate(now), indexCode: activeIndexCode.value }
}

function isoDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function isAShareIntradayLabel(label: string) {
  const [hour, minute] = label.split(':').map(Number)
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return false
  const minutes = hour * 60 + minute
  return (minutes >= 9 * 60 + 30 && minutes <= 11 * 60 + 30)
    || (minutes >= 13 * 60 && minutes <= 15 * 60)
}

function cumulativeReturnRate(point: ProfitAnalysis['trend'][number]) {
  return point.totalAsset > 0 ? point.cumulativeProfit / point.totalAsset * 100 : point.dailyProfitRate
}

function rankLabel(index: number) {
  return String(index + 1).padStart(2, '0')
}

function rankRows(rows: FundProfitRank[]) {
  return rows.map((item, index) => ({ ...item, rank: rankLabel(index) }))
}

const profitRows = computed(() => rankRows((data.value?.profitTop5 || []).filter((item) => item.holdingProfit > 0)))
const lossRows = computed(() => rankRows((data.value?.lossTop5 || []).filter((item) => item.holdingProfit < 0)))
</script>

<template>
  <LoadingState v-if="loading" text="正在加载收益复盘数据" />
  <EmptyState v-else-if="!data" title="暂无盈亏分析数据" description="添加持仓和模拟交易后会生成收益复盘。" />
  <div v-else class="screen-grid">
    <div class="toolbar-row">
      <div class="segmented" aria-label="收益周期">
        <button
          v-for="item in rangeOptions"
          :key="item.value"
          :class="{ active: activeRange === item.value }"
          @click="selectRange(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
      <span class="item-meta">统计区间：{{ data.startDate }} 至 {{ data.endDate }}</span>
    </div>

    <div class="metric-row">
      <MetricTile label="当日收益" :value="signed(data.todayProfit)" :delta="todayProfitStatusText" :tone="metricTone(data.todayProfit)" />
      <MetricTile label="本周收益" :value="signed(data.weekProfit)" :delta="percent(periodRate('THIS_WEEK'))" :tone="metricTone(data.weekProfit)" />
      <MetricTile label="本月收益" :value="signed(data.monthProfit)" :delta="percent(periodRate('THIS_MONTH'))" :tone="metricTone(data.monthProfit)" />
      <MetricTile label="今年收益" :value="signed(data.yearProfit)" :delta="percent(periodRate('THIS_YEAR'))" :tone="metricTone(data.yearProfit)" />
      <MetricTile label="全部收益" :value="signed(data.totalProfit)" :delta="percent(periodRate('ALL'))" :tone="metricTone(data.totalProfit)" />
      <MetricTile label="区间收益率" :value="percent(selectedPeriod?.profitRate || data.selectedRangeProfitRate)" :delta="signed(selectedPeriod?.profit || data.selectedRangeProfit)" :tone="metricTone(selectedPeriod?.profitRate || data.selectedRangeProfitRate)" />
    </div>

      <section class="panel">
        <div class="panel-header">
          <h2 class="panel-title">收益走势 / 指数对比</h2>
          <span class="item-meta">{{ chartStatusText }} · {{ todayProfitStatusText }}</span>
        </div>
      <div class="panel-body">
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
        <BaseChart :option="chart" :height="360" />
        <div class="return-trend-summary">
          <span>组合收益率：<strong :class="toneClass(currentPortfolioReturn)">{{ percent(currentPortfolioReturn, 2) }}</strong></span>
          <span>跑赢{{ activeIndexName }}：<strong :class="toneClass(excessReturn || 0)">{{ excessReturn === null ? '--' : percent(excessReturn, 2) }}</strong></span>
        </div>
      </div>
    </section>

    <div class="insight-grid">
      <section class="panel">
        <div class="panel-header">
          <h2 class="panel-title">复盘读数</h2>
          <span class="item-meta">不承诺收益，仅供复盘</span>
        </div>
        <div class="panel-body insight-list">
          <div class="insight-item">
            <span>盈利交易日占比</span>
            <strong class="text-rise">{{ percent(winRate, 1) }}</strong>
          </div>
          <div class="insight-item">
            <span>{{ indexCompareLabel }}</span>
            <strong :class="toneClass(beatIndexRate)">{{ data.indexCompare?.available ? percent(beatIndexRate) : '--' }}</strong>
          </div>
          <div class="insight-item">
            <span>本期累计收益</span>
            <strong :class="toneClass(selectedPeriod?.profit || data.selectedRangeProfit)">
              {{ signed(selectedPeriod?.profit || data.selectedRangeProfit) }}
            </strong>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">盈利 TOP5</h2></div>
        <div class="panel-body">
          <table class="terminal-table">
            <thead><tr><th>排名</th><th>基金</th><th>持仓金额</th><th>收益</th><th>收益率</th></tr></thead>
            <tbody>
              <tr v-for="item in profitRows" :key="item.holdingId">
                <td>{{ item.rank }}</td>
                <td>{{ item.fundCode }} · {{ item.fundName }}</td>
                <td>{{ money(item.holdingAmount) }}</td>
                <td :class="toneClass(item.holdingProfit)">{{ signed(item.holdingProfit) }}</td>
                <td :class="toneClass(item.holdingProfitRate)">{{ percent(item.holdingProfitRate) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">亏损 TOP5</h2></div>
        <div class="panel-body">
          <table class="terminal-table">
            <thead><tr><th>排名</th><th>基金</th><th>持仓金额</th><th>收益</th><th>收益率</th></tr></thead>
            <tbody>
              <tr v-for="item in lossRows" :key="item.holdingId">
                <td>{{ item.rank }}</td>
                <td>{{ item.fundCode }} · {{ item.fundName }}</td>
                <td>{{ money(item.holdingAmount) }}</td>
                <td :class="toneClass(item.holdingProfit)">{{ signed(item.holdingProfit) }}</td>
                <td :class="toneClass(item.holdingProfitRate)">{{ percent(item.holdingProfitRate) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <DisclaimerBar />
  </div>
</template>
