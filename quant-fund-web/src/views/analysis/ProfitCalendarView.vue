<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { quantApi } from '@/api/quant'
import type { ProfitCalendar } from '@/types/domain'
import MetricTile from '@/components/common/MetricTile.vue'
import BaseChart from '@/components/charts/BaseChart.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { calendarBarOption } from '@/components/charts/chartOptions'
import { metricTone, money, percent, signed, toneClass } from '@/utils/format'

const data = ref<ProfitCalendar>()
const loading = ref(true)
type CalendarCell = ProfitCalendar['days'][number] & { blank: boolean; key: string }

onMounted(async () => {
  try {
    data.value = await quantApi.calendar()
  } finally {
    loading.value = false
  }
})

const chart = computed(() => calendarBarOption(data.value?.days || []))
const tradingDays = computed(() => data.value?.days.filter((day) => day.tradingDay) || [])
const profitDays = computed(() => tradingDays.value.filter((day) => day.dailyProfit > 0).length)
const lossDays = computed(() => tradingDays.value.filter((day) => day.dailyProfit < 0).length)
const bestDay = computed(() => tradingDays.value.reduce((best, day) => day.dailyProfit > best.dailyProfit ? day : best, tradingDays.value[0]))
const worstDay = computed(() => tradingDays.value.reduce((worst, day) => day.dailyProfit < worst.dailyProfit ? day : worst, tradingDays.value[0]))
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const calendarCells = computed(() => {
  const days = data.value?.days || []
  if (!days.length) return []
  const first = new Date(`${days[0].date}T00:00:00`)
  const blankCount = (first.getDay() + 6) % 7
  const blanks: CalendarCell[] = Array.from({ length: blankCount }, (_, index) => ({
    blank: true,
    key: `blank-${index}`,
    date: '',
    dailyProfit: 0,
    dailyProfitRate: 0,
    cumulativeProfit: 0,
    heatLevel: 'BLANK',
    tradingDay: false,
    tradingDayLabel: ''
  }))
  return [
    ...blanks,
    ...days.map((day) => ({ ...day, blank: false, key: day.date }))
  ] satisfies CalendarCell[]
})
const todayText = new Date().toISOString().slice(0, 10)
const profitRows = computed(() => (data.value?.profitTop5 || []).filter((item) => item.holdingProfit > 0))
const lossRows = computed(() => (data.value?.lossTop5 || []).filter((item) => item.holdingProfit < 0))

function dayStatusText(day: ProfitCalendar['days'][number]) {
  if (!day.tradingDay) return day.profitStatusText || day.tradingDayLabel
  const rateText = percent(day.dailyProfitRate, 2)
  return day.profitStatusText ? `${rateText} · ${day.profitStatusText}` : rateText
}
</script>

<template>
  <LoadingState v-if="loading" text="正在加载盈亏日历" />
  <EmptyState v-else-if="!data" title="暂无盈亏日历" description="同步净值或添加模拟交易后会生成日历。" />
  <div v-else class="screen-grid">
    <div class="metric-row">
      <MetricTile label="月累计收益" :value="signed(data.monthlyProfit)" :delta="percent(data.monthlyProfitRate)" :tone="metricTone(data.monthlyProfit)" />
      <MetricTile label="盈利天数" :value="`${profitDays} 天`" sub-label="交易日统计" tone="rise" />
      <MetricTile label="亏损天数" :value="`${lossDays} 天`" sub-label="风险复盘" tone="fall" />
      <MetricTile label="最佳单日" :value="bestDay ? signed(bestDay.dailyProfit) : '--'" :delta="bestDay ? bestDay.date : ''" :tone="metricTone(bestDay?.dailyProfit || 0)" />
      <MetricTile label="最弱单日" :value="worstDay ? signed(worstDay.dailyProfit) : '--'" :delta="worstDay ? worstDay.date : ''" :tone="metricTone(worstDay?.dailyProfit || 0)" />
      <MetricTile label="日历月份" :value="data.month" sub-label="红涨绿跌" tone="info" />
    </div>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">盈亏日历 · {{ data.month }}</h2>
        <span class="item-meta">每日收益金额 / 每日收益率 / 累计收益；休市日不参与统计</span>
      </div>
      <div class="panel-body">
        <div class="calendar-toolbar">
          <div class="segmented compact">
            <button class="active">日</button>
            <button disabled>月</button>
            <button disabled>年</button>
          </div>
          <strong>{{ data.month.replace('-', '年') }}月</strong>
        </div>
        <div class="calendar-weekdays">
          <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
        </div>
        <div class="calendar-grid calendar-month-grid">
          <button
            v-for="day in calendarCells"
            :key="day.key"
            class="calendar-cell"
            :class="[
              day.blank ? 'blank' : day.heatLevel.toLowerCase(),
              !day.blank && day.date === todayText ? 'today' : '',
              !day.blank && !day.tradingDay ? 'non-trading' : ''
            ]"
            type="button"
            :title="day.blank ? '' : `${day.date} ${day.tradingDayLabel} ${signed(day.dailyProfit)} ${dayStatusText(day)}`"
            :disabled="day.blank"
          >
            <template v-if="!day.blank">
              <strong>{{ day.date.slice(8) }}<em v-if="day.date === todayText">今</em></strong>
              <span v-if="day.tradingDay" :class="toneClass(day.dailyProfit)">{{ signed(day.dailyProfit) }}</span>
              <span v-else>休</span>
              <small>{{ dayStatusText(day) }}</small>
            </template>
          </button>
        </div>
        <BaseChart :option="chart" :height="180" />
      </div>
    </section>

    <div class="insight-grid two">
      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">盈利 TOP5</h2></div>
        <div class="panel-body">
          <table class="terminal-table">
            <thead><tr><th>基金</th><th>持仓金额</th><th>收益</th><th>收益率</th></tr></thead>
            <tbody>
              <tr v-for="item in profitRows" :key="item.holdingId">
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
            <thead><tr><th>基金</th><th>持仓金额</th><th>收益</th><th>收益率</th></tr></thead>
            <tbody>
              <tr v-for="item in lossRows" :key="item.holdingId">
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
