<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Download, Refresh, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { quantApi } from '@/api/quant'
import BaseChart from '@/components/charts/BaseChart.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import MetricTile from '@/components/common/MetricTile.vue'
import type { BacktestBatchResponse, BacktestResult, BacktestRunRequest, PortfolioAccount } from '@/types/domain'
import { metricTone, money, percent, percentUnsigned, toneClass } from '@/utils/format'

const loadingAccounts = ref(true)
const running = ref(false)
const refreshingNav = ref(false)
const exportingSamples = ref(false)
const accounts = ref<PortfolioAccount[]>([])
const response = ref<BacktestBatchResponse>()
const mlResponse = ref<BacktestBatchResponse>()
const selectedFundCode = ref('')

const form = reactive({
  accountId: undefined as number | undefined,
  startDate: yearsAgo(3),
  endDate: today(),
  initialCash: 10000,
  feeRate: 0.0015,
  buyThreshold: 52,
  sellThreshold: 6,
  maxSinglePositionRate: 45,
  buyStepRatio: 20,
  sellStepRatio: 8,
  takeProfitRate: 300,
  stopLossRate: -18,
  minNavSamples: 40,
  warmupDays: 180,
  trendHoldReturn20d: 1.5,
  trendHoldMa20Deviation: -7,
  workers: 6,
  compareMl: false
})

onMounted(async () => {
  try {
    accounts.value = await quantApi.portfolios()
    form.accountId = accounts.value[0]?.id
  } finally {
    loadingAccounts.value = false
  }
})

const results = computed(() => response.value?.results || [])
const summary = computed(() => response.value?.summary)
const mlSummary = computed(() => mlResponse.value?.summary)
const mlComparison = computed(() => {
  if (!summary.value || !mlSummary.value) return undefined
  const baseAnnualTrades = summary.value.avgAnnualTradeCount ?? summary.value.avgTradeCount
  const mlAnnualTrades = mlSummary.value.avgAnnualTradeCount ?? mlSummary.value.avgTradeCount
  return {
    annualReturnDelta: mlSummary.value.avgAnnualReturnRate - summary.value.avgAnnualReturnRate,
    drawdownDelta: mlSummary.value.avgMaxDrawdownRate - summary.value.avgMaxDrawdownRate,
    passRateDelta: mlSummary.value.passRate - summary.value.passRate,
    tradeCountDelta: mlAnnualTrades - baseAnnualTrades,
    outperformDelta: mlSummary.value.outperformPositionBenchmarkRate - summary.value.outperformPositionBenchmarkRate,
    mlAppliedFundRate: mlSummary.value.mlAppliedFundRate || 0,
    avgMlScoreAdjustmentAbs: mlSummary.value.avgMlScoreAdjustmentAbs || 0,
    maxMlScoreAdjustmentAbs: mlSummary.value.maxMlScoreAdjustmentAbs || 0,
    avgMlExpectedReturn: mlSummary.value.avgMlExpectedReturn || 0,
    avgMlExpectedReturnPositiveDays: mlSummary.value.avgMlExpectedReturnPositiveDays || 0,
    avgMlExpectedReturnPositiveDayRate: mlSummary.value.avgMlExpectedReturnPositiveDayRate || 0,
    avgMlProbability: mlSummary.value.avgMlProbability || 0,
    avgMlBullishDays: mlSummary.value.avgMlBullishDays || 0,
    avgMlBullishDayRate: mlSummary.value.avgMlBullishDayRate || 0,
    avgMlSignalStrength: mlSummary.value.avgMlSignalStrength || 0,
    avgMlConfidenceScore: mlSummary.value.avgMlConfidenceScore || 0,
    avgMlConfidenceMediumHighDayRate: mlSummary.value.avgMlConfidenceMediumHighDayRate || 0
  }
})
const selectedResult = computed(() => {
  if (!results.value.length) return undefined
  return results.value.find((item) => item.fundCode === selectedFundCode.value) || results.value[0]
})
const topResults = computed(() => [...results.value].sort((a, b) => b.annualReturnRate - a.annualReturnRate).slice(0, 8))
const weakResults = computed(() => [...results.value].sort((a, b) => a.annualReturnRate - b.annualReturnRate).slice(0, 5))
const selectedTrades = computed(() => selectedResult.value?.trades || [])
const tradeActionCounts = computed(() => {
  const trades = selectedResult.value?.trades || []
  return {
    buy: trades.filter((item) => item.action === 'BUY').length,
    sell: trades.filter((item) => item.action === 'SELL').length
  }
})
const verdictTone = computed(() => {
  const passRate = summary.value?.passRate || 0
  if (passRate >= 60) return 'rise'
  if (passRate >= 40) return 'warning'
  return 'fall'
})
const resultChart = computed(() => {
  const curve = selectedResult.value?.equityCurve || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: '#8fa6b7' } },
    grid: { left: 42, right: 24, top: 42, bottom: 32 },
    xAxis: { type: 'category', data: curve.map((item) => item.date), axisLabel: { color: '#8fa6b7' } },
    yAxis: [
      { type: 'value', name: '资产', axisLabel: { color: '#8fa6b7' }, splitLine: { lineStyle: { color: '#1f3440' } } },
      { type: 'value', name: '评分', min: 0, max: 100, axisLabel: { color: '#8fa6b7' } }
    ],
    series: [
      { name: '策略资产', type: 'line', smooth: true, data: curve.map((item) => item.totalAsset), symbol: 'none', lineStyle: { color: '#67e8f9' } },
      { name: '信号评分', type: 'line', yAxisIndex: 1, smooth: true, data: curve.map((item) => item.signalScore), symbol: 'none', lineStyle: { color: '#fbbf24' } }
    ]
  }
})
const tradeChart = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 34, right: 20, top: 24, bottom: 28 },
  xAxis: { type: 'category', data: ['买入', '卖出'], axisLabel: { color: '#8fa6b7' } },
  yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#8fa6b7' }, splitLine: { lineStyle: { color: '#1f3440' } } },
  series: [{ type: 'bar', data: [tradeActionCounts.value.buy, tradeActionCounts.value.sell], itemStyle: { color: '#60a5fa' }, barMaxWidth: 42 }]
}))

async function runBacktest() {
  if (running.value) return
  if (!validateBacktestRange()) return
  running.value = true
  try {
    mlResponse.value = undefined
    response.value = await quantApi.runBacktest(buildBacktestRequest(false))
    if (form.compareMl) {
      mlResponse.value = await quantApi.runBacktest(buildBacktestRequest(true))
    }
    selectedFundCode.value = response.value.results[0]?.fundCode || ''
    ElMessage.success('回测完成')
  } finally {
    running.value = false
  }
}

async function refreshNavCache() {
  if (refreshingNav.value) return
  if (!validateBacktestRange()) return
  refreshingNav.value = true
  try {
    const result = await quantApi.refreshBacktestNavCache(buildBacktestRequest(false))
    if (result.failedCount > 0) {
      ElMessage.warning(`历史净值拉取完成：成功 ${result.successCount} 只，失败/空数据 ${result.failedCount} 只`)
    } else {
      ElMessage.success(`历史净值已缓存：${result.successCount} 只基金`)
    }
  } finally {
    refreshingNav.value = false
  }
}

async function exportMlTrainingSamples() {
  if (exportingSamples.value) return
  if (!validateBacktestRange()) return
  exportingSamples.value = true
  try {
    const blob = await quantApi.exportMlTrainingSamples(buildBacktestRequest(false))
    const fileName = `quantfund_ml_training_${form.startDate}_${form.endDate}.csv`
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    ElMessage.success('ML训练样本已导出')
  } finally {
    exportingSamples.value = false
  }
}

function validateBacktestRange() {
  if (!form.startDate || !form.endDate || form.startDate > form.endDate) {
    ElMessage.warning('请选择有效的回测区间')
    return false
  }
  return true
}

function buildBacktestRequest(enableMl = false): BacktestRunRequest {
  return {
    accountId: form.accountId,
    startDate: form.startDate,
    endDate: form.endDate,
    initialCash: form.initialCash,
    feeRate: form.feeRate,
    strategyParams: {
      buyThreshold: form.buyThreshold,
      sellThreshold: form.sellThreshold,
      maxSinglePositionRate: form.maxSinglePositionRate,
      buyStepRatio: form.buyStepRatio,
      sellStepRatio: form.sellStepRatio,
      takeProfitRate: form.takeProfitRate,
      stopLossRate: form.stopLossRate,
      minNavSamples: form.minNavSamples,
      warmupDays: form.warmupDays,
      trendHoldReturn20d: form.trendHoldReturn20d,
      trendHoldMa20Deviation: form.trendHoldMa20Deviation
    },
    options: {
      workers: form.workers,
      saveEquityCurve: true,
      saveTrades: true,
      enableMl
    }
  }
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

function yearsAgo(years: number) {
  const date = new Date()
  date.setFullYear(date.getFullYear() - years)
  return date.toISOString().slice(0, 10)
}

function statusText(item: BacktestResult) {
  return item.passed ? '通过' : '需观察'
}

function nullableRatio(value?: number | null, digits = 2) {
  return value === null || value === undefined ? '--' : value.toFixed(digits)
}

function actionText(action: string) {
  return action === 'BUY' ? '买入' : '卖出'
}

function reasonText(reason: string) {
  const labels: Record<string, string> = {
    strong_trend_buy: '强趋势加仓',
    trend_start_buy: '趋势启动买入',
    score_above_buy_threshold: '评分达买入阈值',
    weak_trend_defense: '弱趋势防守',
    score_exit: '评分跌破卖出阈值',
    risk_exit: '止损/风险退出',
    extreme_risk_exit: '极端风险退出',
    profit_exit: '止盈退出',
    sell_exit: '卖出退出'
  }
  return labels[reason] || reason
}
</script>

<template>
  <LoadingState v-if="loadingAccounts" text="正在加载回测账户" />
  <div v-else class="screen-grid backtest-screen">
    <section class="panel backtest-control-panel">
      <div class="panel-header">
        <h2 class="panel-title">回测验证</h2>
        <div class="panel-actions">
          <button class="ghost-button" type="button" :disabled="refreshingNav || running" @click="refreshNavCache">
            <el-icon :class="{ spinning: refreshingNav }"><component :is="refreshingNav ? Refresh : Download" /></el-icon>
            <span>{{ refreshingNav ? '拉取中' : '拉取净值' }}</span>
          </button>
          <button class="ghost-button" type="button" :disabled="exportingSamples || refreshingNav || running" @click="exportMlTrainingSamples">
            <el-icon :class="{ spinning: exportingSamples }"><component :is="exportingSamples ? Refresh : Download" /></el-icon>
            <span>{{ exportingSamples ? '导出中' : '导出样本' }}</span>
          </button>
          <button class="primary-button" type="button" :disabled="running || refreshingNav || exportingSamples" @click="runBacktest">
            <el-icon :class="{ spinning: running }"><component :is="running ? Refresh : VideoPlay" /></el-icon>
            <span>{{ running ? '回测中' : '运行回测' }}</span>
          </button>
        </div>
      </div>
      <div class="backtest-form">
        <label>
          <span>账户</span>
          <select v-model.number="form.accountId">
            <option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.accountName }}</option>
          </select>
        </label>
        <label><span>开始日期</span><input v-model="form.startDate" type="date"></label>
        <label><span>结束日期</span><input v-model="form.endDate" type="date"></label>
        <label><span>初始资金</span><input v-model.number="form.initialCash" type="number" min="100" step="100"></label>
        <label><span>手续费率</span><input v-model.number="form.feeRate" type="number" min="0" step="0.0001"></label>
        <label><span>买入阈值</span><input v-model.number="form.buyThreshold" type="number" min="0" max="100"></label>
        <label><span>卖出阈值</span><input v-model.number="form.sellThreshold" type="number" min="0" max="100"></label>
        <label><span>单基金上限</span><input v-model.number="form.maxSinglePositionRate" type="number" min="1" max="100"></label>
        <label><span>买入步长</span><input v-model.number="form.buyStepRatio" type="number" min="1" max="100"></label>
        <label><span>卖出步长</span><input v-model.number="form.sellStepRatio" type="number" min="1" max="100"></label>
        <label><span>止盈线</span><input v-model.number="form.takeProfitRate" type="number" step="1"></label>
        <label><span>止损线</span><input v-model.number="form.stopLossRate" type="number" step="1"></label>
        <label><span>最小样本</span><input v-model.number="form.minNavSamples" type="number" min="2" max="120"></label>
        <label><span>预热天数</span><input v-model.number="form.warmupDays" type="number" min="0" max="365"></label>
        <label><span>持有20日收益</span><input v-model.number="form.trendHoldReturn20d" type="number" step="1"></label>
        <label><span>持有均线偏离</span><input v-model.number="form.trendHoldMa20Deviation" type="number" step="1"></label>
        <label><span>Workers</span><input v-model.number="form.workers" type="number" min="1" max="12"></label>
        <label class="toggle-row"><span>ML辅助对比</span><input v-model="form.compareMl" type="checkbox"></label>
      </div>
    </section>

    <EmptyState
      v-if="!response"
      title="暂无回测结果"
      description="选择区间和参数后运行回测，系统会只使用本地已缓存净值进行验证。"
    />

    <template v-else>
      <div class="metric-row">
        <MetricTile label="通过率" :value="percentUnsigned(summary?.passRate || 0, 1)" :delta="summary?.diagnosis" :tone="verdictTone" />
        <MetricTile label="跑赢同仓位" :value="percentUnsigned(summary?.outperformPositionBenchmarkRate || 0, 1)" :delta="`满仓基准 ${percentUnsigned(summary?.outperformBuyHoldRate || 0, 1)}`" :tone="metricTone((summary?.outperformPositionBenchmarkRate || 0) - 52)" />
        <MetricTile label="平均年化收益" :value="percent(summary?.avgAnnualReturnRate || 0, 2)" :delta="`中位数 ${percent(summary?.medianAnnualReturnRate || 0, 2)}`" :tone="metricTone(summary?.avgAnnualReturnRate || 0)" />
        <MetricTile label="平均最大回撤" :value="percent(summary?.avgMaxDrawdownRate || 0, 2)" :delta="`最差 ${percent(summary?.worstMaxDrawdownRate || 0, 2)}`" tone="fall" />
        <MetricTile label="年均交易次数" :value="nullableRatio(summary?.avgAnnualTradeCount ?? summary?.avgTradeCount, 1)" :delta="`总均次 ${nullableRatio(summary?.avgTradeCount, 1)}`" :tone="metricTone(6 - (summary?.avgAnnualTradeCount ?? summary?.avgTradeCount ?? 0))" />
        <MetricTile label="样本基金" :value="`${response.successCount}/${response.fundCount}`" :delta="response.failedCount ? `失败 ${response.failedCount}` : response.modelVersion" tone="info" />
      </div>

      <section v-if="mlComparison && mlResponse" class="panel">
        <div class="panel-header">
          <h2 class="panel-title">ML辅助 A/B 对比</h2>
          <span class="item-meta">{{ mlResponse.modelVersion }}</span>
        </div>
        <div class="metric-row compact-metrics">
          <MetricTile label="年化差异" :value="percent(mlComparison.annualReturnDelta, 2)" :delta="`ML ${percent(mlSummary?.avgAnnualReturnRate || 0, 2)} / 规则 ${percent(summary?.avgAnnualReturnRate || 0, 2)}`" :tone="metricTone(mlComparison.annualReturnDelta)" />
          <MetricTile label="通过率差异" :value="percentUnsigned(mlComparison.passRateDelta, 1)" :delta="`ML ${percentUnsigned(mlSummary?.passRate || 0, 1)}`" :tone="metricTone(mlComparison.passRateDelta)" />
          <MetricTile label="同仓跑赢差异" :value="percentUnsigned(mlComparison.outperformDelta, 1)" :delta="`ML ${percentUnsigned(mlSummary?.outperformPositionBenchmarkRate || 0, 1)}`" :tone="metricTone(mlComparison.outperformDelta)" />
          <MetricTile label="回撤差异" :value="percent(mlComparison.drawdownDelta, 2)" :delta="mlComparison.drawdownDelta <= 0 ? '回撤改善' : '回撤变大'" :tone="mlComparison.drawdownDelta <= 0 ? 'rise' : 'fall'" />
          <MetricTile label="交易次数差异" :value="nullableRatio(mlComparison.tradeCountDelta, 1)" :delta="mlComparison.tradeCountDelta <= 0 ? '频率下降' : '频率上升'" :tone="mlComparison.tradeCountDelta <= 0 ? 'rise' : 'warning'" />
          <MetricTile label="偏强概率" :value="percentUnsigned(mlComparison.avgMlProbability, 1)" :delta="`样本偏强占比 ${percentUnsigned(mlComparison.avgMlBullishDayRate, 1)}`" :tone="metricTone(mlComparison.avgMlProbability - 50)" />
          <MetricTile label="预测收益参考" :value="percent(mlComparison.avgMlExpectedReturn, 2)" delta="未来20个净值样本参考" :tone="metricTone(mlComparison.avgMlExpectedReturn)" />
          <MetricTile label="平均参考权重" :value="percentUnsigned(mlComparison.avgMlConfidenceScore, 1)" :delta="`中高可信覆盖 ${percentUnsigned(mlComparison.avgMlConfidenceMediumHighDayRate, 1)}`" tone="info" />
          <MetricTile label="信号强度" :value="percentUnsigned(mlComparison.avgMlSignalStrength, 1)" delta="越高越偏离 50%" tone="info" />
          <MetricTile label="ML生效基金" :value="percentUnsigned(mlComparison.mlAppliedFundRate, 1)" :delta="`最大调分 ${nullableRatio(mlComparison.maxMlScoreAdjustmentAbs, 2)}`" tone="info" />
          <MetricTile label="平均调分" :value="nullableRatio(mlComparison.avgMlScoreAdjustmentAbs, 2)" delta="仅表示参与强度" tone="info" />
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2 class="panel-title">参数适配结论</h2>
          <span class="item-meta">{{ response.taskId }}</span>
        </div>
        <div class="panel-body backtest-verdict" :class="`verdict-${verdictTone}`">
          <strong>{{ summary?.diagnosis }}</strong>
          <span>区间 {{ form.startDate }} 至 {{ form.endDate }}，预热 {{ form.warmupDays }} 天，趋势持有 {{ form.trendHoldReturn20d }}% / {{ form.trendHoldMa20Deviation }}%，默认并发 {{ form.workers }}。</span>
        </div>
      </section>

      <div class="backtest-main-grid">
        <section class="panel">
          <div class="panel-header">
            <h2 class="panel-title">基金结果</h2>
            <span class="item-meta">按年化收益排序</span>
          </div>
          <div class="panel-body table-scroll">
            <table class="terminal-table">
              <thead>
                <tr>
                  <th>基金</th>
                  <th>状态</th>
                  <th>年化</th>
                  <th>同仓位跑赢</th>
                  <th>回撤</th>
                  <th>样本</th>
                  <th>交易</th>
                  <th>结论</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in results"
                  :key="item.fundCode"
                  :class="{ selected: selectedResult?.fundCode === item.fundCode }"
                  @click="selectedFundCode = item.fundCode"
                >
                  <td>
                    <strong>{{ item.fundCode }}</strong>
                    <span>{{ item.fundName }}</span>
                  </td>
                  <td><span :class="['status-pill', item.passed ? 'pass' : 'watch']">{{ statusText(item) }}</span></td>
                  <td :class="toneClass(item.annualReturnRate)">{{ percent(item.annualReturnRate, 2) }}</td>
                  <td :class="toneClass(item.positionExcessReturnRate)">{{ percent(item.positionExcessReturnRate, 2) }}</td>
                  <td :class="toneClass(item.maxDrawdownRate)">{{ percent(item.maxDrawdownRate, 2) }}</td>
                  <td>{{ percentUnsigned(item.dataCoverageRate || 0, 1) }}</td>
                  <td>{{ item.tradeCount }}</td>
                  <td>{{ item.diagnosis }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header">
            <h2 class="panel-title">单基金曲线</h2>
            <span class="item-meta">{{ selectedResult?.fundCode }} {{ selectedResult?.fundName }}</span>
          </div>
          <div class="panel-body">
            <BaseChart :option="resultChart" :height="300" />
            <div class="selected-metrics" v-if="selectedResult">
              <span>总收益 <strong :class="toneClass(selectedResult.totalReturnRate)">{{ percent(selectedResult.totalReturnRate, 2) }}</strong></span>
              <span>同仓位基准 <strong :class="toneClass(selectedResult.positionBenchmarkReturnRate)">{{ percent(selectedResult.positionBenchmarkReturnRate, 2) }}</strong></span>
              <span>满仓基准 <strong :class="toneClass(selectedResult.benchmarkReturnRate)">{{ percent(selectedResult.benchmarkReturnRate, 2) }}</strong></span>
              <span>夏普 <strong>{{ nullableRatio(selectedResult.sharpeRatio, 2) }}</strong></span>
              <span>卡玛 <strong>{{ nullableRatio(selectedResult.calmarRatio, 2) }}</strong></span>
            </div>
          </div>
        </section>
      </div>

      <section class="panel">
        <div class="panel-header">
          <h2 class="panel-title">交易明细诊断</h2>
          <span class="item-meta">{{ selectedResult?.fundCode }} {{ selectedResult?.tradeCount || 0 }} 笔</span>
        </div>
        <div v-if="selectedTrades.length" class="panel-body trade-detail-wrap">
          <table class="terminal-table trade-detail-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>操作</th>
                <th>金额</th>
                <th>仓位变化</th>
                <th>评分</th>
                <th>原因</th>
                <th>5日/20日/60日</th>
                <th>均线/回撤</th>
                <th>趋势/机会/风险</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(trade, index) in selectedTrades" :key="`${trade.date}-${trade.action}-${index}`">
                <td>{{ trade.date }}</td>
                <td>
                  <span :class="['trade-action-pill', trade.action.toLowerCase()]">{{ actionText(trade.action) }}</span>
                  <small>{{ percentUnsigned(trade.tradeRatio || 0, 1) }}</small>
                </td>
                <td>
                  <strong>{{ money(trade.amount || 0, 2) }}</strong>
                  <small>净值 {{ nullableRatio(trade.nav, 4) }}</small>
                </td>
                <td>
                  <strong>{{ percentUnsigned(trade.positionRateBefore || 0, 1) }} → {{ percentUnsigned(trade.positionRateAfter || 0, 1) }}</strong>
                  <small>份额 {{ money(trade.share || 0, 2) }}</small>
                </td>
                <td>
                  <strong>{{ nullableRatio(trade.score, 1) }}</strong>
                  <small>交易费 {{ money(trade.fee || 0, 2) }}</small>
                </td>
                <td>{{ reasonText(trade.reason) }}</td>
                <td>
                  <span :class="toneClass(trade.return5d || 0)">{{ percent(trade.return5d || 0, 1) }}</span>
                  <span :class="toneClass(trade.return20d || 0)">{{ percent(trade.return20d || 0, 1) }}</span>
                  <span :class="toneClass(trade.return60d || 0)">{{ percent(trade.return60d || 0, 1) }}</span>
                </td>
                <td>
                  <span :class="toneClass(trade.ma20Deviation || 0)">均线 {{ percent(trade.ma20Deviation || 0, 1) }}</span>
                  <span :class="toneClass(trade.maxDrawdown60d || 0)">回撤 {{ percent(trade.maxDrawdown60d || 0, 1) }}</span>
                </td>
                <td>
                  <span>趋 {{ nullableRatio(trade.trendScore, 0) }}</span>
                  <span>机 {{ nullableRatio(trade.opportunityScore, 0) }}</span>
                  <span>险 {{ nullableRatio(trade.riskScore, 0) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState v-else title="暂无交易明细" description="该基金在当前回测参数下没有触发买入或卖出。" />
      </section>

      <div class="backtest-bottom-grid">
        <section class="panel">
          <div class="panel-header"><h2 class="panel-title">交易分布</h2></div>
          <div class="panel-body">
            <BaseChart :option="tradeChart" :height="190" />
          </div>
        </section>

        <section class="panel">
          <div class="panel-header"><h2 class="panel-title">表现较好</h2></div>
          <div class="panel-body compact-list">
            <button v-for="item in topResults" :key="item.fundCode" type="button" @click="selectedFundCode = item.fundCode">
              <span>{{ item.fundCode }} {{ item.fundName }}</span>
              <strong :class="toneClass(item.annualReturnRate)">{{ percent(item.annualReturnRate, 2) }}</strong>
            </button>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header"><h2 class="panel-title">薄弱样本</h2></div>
          <div class="panel-body compact-list">
            <button v-for="item in weakResults" :key="item.fundCode" type="button" @click="selectedFundCode = item.fundCode">
              <span>{{ item.fundCode }} {{ item.fundName }}</span>
              <strong :class="toneClass(item.annualReturnRate)">{{ percent(item.annualReturnRate, 2) }}</strong>
            </button>
          </div>
        </section>
      </div>

      <DisclaimerBar />
    </template>
  </div>
</template>

<style scoped>
.backtest-screen {
  gap: 12px;
}

.backtest-form {
  display: grid;
  grid-template-columns: repeat(7, minmax(118px, 1fr));
  gap: 10px;
  padding: 12px;
}

.backtest-form label {
  display: grid;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
}

.backtest-form input,
.backtest-form select {
  height: 34px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  background: var(--surface-2);
  color: var(--text);
  padding: 0 10px;
  min-width: 0;
}

.backtest-verdict {
  display: grid;
  gap: 6px;
  padding: 12px;
  border-radius: 6px;
  background: rgba(96, 165, 250, 0.08);
}

.backtest-verdict strong {
  color: var(--text);
}

.backtest-verdict span {
  color: var(--muted);
}

.verdict-rise {
  border-left: 3px solid var(--green);
}

.verdict-warning {
  border-left: 3px solid var(--amber);
}

.verdict-fall {
  border-left: 3px solid var(--red);
}

.backtest-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.8fr);
  gap: 12px;
}

.backtest-bottom-grid {
  display: grid;
  grid-template-columns: 0.7fr 1fr 1fr;
  gap: 12px;
}

.table-scroll {
  max-height: 430px;
  overflow: auto;
}

.terminal-table tr {
  cursor: pointer;
}

.terminal-table tr.selected {
  background: rgba(96, 165, 250, 0.1);
}

.terminal-table td:first-child {
  display: grid;
  gap: 2px;
}

.terminal-table td:first-child span {
  color: var(--muted);
  font-size: 12px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
}

.status-pill.pass {
  color: var(--green);
  background: rgba(34, 197, 94, 0.12);
}

.status-pill.watch {
  color: var(--amber);
  background: rgba(251, 191, 36, 0.12);
}

.selected-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-top: 8px;
}

.selected-metrics span {
  display: grid;
  gap: 4px;
  padding: 8px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  color: var(--muted);
  background: var(--surface);
}

.selected-metrics strong {
  color: var(--text);
}

.trade-detail-wrap {
  max-height: 300px;
  overflow: auto;
}

.trade-detail-table {
  min-width: 1120px;
}

.trade-detail-table td {
  vertical-align: top;
}

.trade-detail-table td span,
.trade-detail-table td small {
  display: block;
  margin-top: 2px;
}

.trade-detail-table small {
  color: var(--muted);
  font-size: 11px;
}

.trade-action-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-width: 42px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
}

.trade-action-pill.buy {
  color: var(--red);
  background: rgba(248, 113, 113, 0.12);
}

.trade-action-pill.sell {
  color: var(--green);
  background: rgba(34, 197, 94, 0.12);
}

.compact-list {
  display: grid;
  gap: 6px;
  max-height: 190px;
  overflow: auto;
}

.compact-list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 34px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text);
  padding: 0 10px;
  text-align: left;
}

.compact-list span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.primary-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.panel-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 1280px) {
  .backtest-form {
    grid-template-columns: repeat(4, minmax(120px, 1fr));
  }

  .backtest-main-grid,
  .backtest-bottom-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .backtest-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .selected-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
