<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { quantApi } from '@/api/quant'
import type { FundProfitRank, ProfitAnalysis } from '@/types/domain'
import MetricTile from '@/components/common/MetricTile.vue'
import BaseChart from '@/components/charts/BaseChart.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { profitTrendOption } from '@/components/charts/chartOptions'
import { metricTone, money, percent, signed, toneClass } from '@/utils/format'

const data = ref<ProfitAnalysis>()
const loading = ref(true)
const activeRange = ref('THIS_MONTH')

const rangeOptions = [
  { label: '当日', value: 'TODAY' },
  { label: '本周', value: 'THIS_WEEK' },
  { label: '本月', value: 'THIS_MONTH' },
  { label: '今年', value: 'THIS_YEAR' },
  { label: '全部', value: 'ALL' }
]

onMounted(async () => {
  try {
    data.value = await quantApi.profit()
  } finally {
    loading.value = false
  }
})

const chart = computed(() => profitTrendOption(data.value?.trend || []))
const selectedPeriod = computed(() => data.value?.periodStats.find((item) => item.period === activeRange.value))
const periodRate = (period: string) => data.value?.periodStats.find((item) => item.period === period)?.profitRate || 0
const winRate = computed(() => {
  if (!data.value?.trend.length) return 0
  const profitDays = data.value.trend.filter((item) => item.dailyProfit > 0).length
  return profitDays / data.value.trend.length * 100
})
const beatIndexRate = computed(() => selectedPeriod.value ? selectedPeriod.value.profitRate - 1.68 : 0)

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
          @click="activeRange = item.value"
        >
          {{ item.label }}
        </button>
      </div>
      <span class="item-meta">统计区间：{{ data.startDate }} 至 {{ data.endDate }}</span>
    </div>

    <div class="metric-row">
      <MetricTile label="当日收益" :value="signed(data.todayProfit)" :delta="percent(periodRate('TODAY'))" :tone="metricTone(data.todayProfit)" />
      <MetricTile label="本周收益" :value="signed(data.weekProfit)" :delta="percent(periodRate('THIS_WEEK'))" :tone="metricTone(data.weekProfit)" />
      <MetricTile label="本月收益" :value="signed(data.monthProfit)" :delta="percent(periodRate('THIS_MONTH'))" :tone="metricTone(data.monthProfit)" />
      <MetricTile label="今年收益" :value="signed(data.yearProfit)" :delta="percent(periodRate('THIS_YEAR'))" :tone="metricTone(data.yearProfit)" />
      <MetricTile label="全部收益" :value="signed(data.totalProfit)" :delta="percent(periodRate('ALL'))" :tone="metricTone(data.totalProfit)" />
      <MetricTile label="区间收益率" :value="percent(selectedPeriod?.profitRate || data.selectedRangeProfitRate)" :delta="signed(selectedPeriod?.profit || data.selectedRangeProfit)" :tone="metricTone(selectedPeriod?.profitRate || data.selectedRangeProfitRate)" />
    </div>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">收益走势 / 指数对比 / 回撤监控</h2>
        <span class="item-meta">{{ data.indexCompareStatus }}</span>
      </div>
      <div class="panel-body">
        <BaseChart :option="chart" :height="360" />
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
            <span>跑赢沪深300</span>
            <strong :class="toneClass(beatIndexRate)">{{ percent(beatIndexRate) }}</strong>
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
