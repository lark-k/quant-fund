<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { quantApi } from '@/api/quant'
import type { AiAnalysisReport, FundHolding, QuantSignal } from '@/types/domain'
import { DISCLAIMER } from '@/types/domain'
import ActionTag from '@/components/common/ActionTag.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import MetricTile from '@/components/common/MetricTile.vue'
import { actionPercent, formatDateTime, money, percent, percentUnsigned, signed, toneClass } from '@/utils/format'

const route = useRoute()
const holdings = ref<FundHolding[]>([])
const reports = ref<AiAnalysisReport[]>([])
const selected = ref<AiAnalysisReport>()
const quantSignal = ref<QuantSignal | null>(null)
const selectedHoldingId = ref<number>()
const loading = ref(true)
const generating = ref(false)
const showAllHistoryReports = ref(false)
const HISTORY_PREVIEW_LIMIT = 5

const selectedHolding = computed(() => holdings.value.find((item) => item.id === selectedHoldingId.value))
const filteredReports = computed(() => {
  if (!selectedHoldingId.value) return reports.value
  return reports.value.filter((item) => item.holdingId === selectedHoldingId.value)
})
const visibleHistoryReports = computed(() => {
  return showAllHistoryReports.value ? filteredReports.value : filteredReports.value.slice(0, HISTORY_PREVIEW_LIMIT)
})
const historyHasMore = computed(() => filteredReports.value.length > HISTORY_PREVIEW_LIMIT)

const selectedConclusion = computed(() => cleanAdvisoryText(selected.value?.finalConclusion || ''))
const selectedDisclaimer = computed(() => selected.value?.disclaimer || DISCLAIMER)
const mlBusinessInfo = computed(() => {
  const metrics = parseMetrics(quantSignal.value?.metricsJson)
  if (!metrics.mlAvailable) return undefined
  const probability = metricNumber(metrics.mlProbability)
  const expectedReturn = metricNumber(metrics.mlExpectedReturn)
  const lower = metricNumber(metrics.mlExpectedReturnLower)
  const upper = metricNumber(metrics.mlExpectedReturnUpper)
  const adjustment = metricNumber(metrics.mlScoreAdjustment)
  const confidenceScore = metricNumber(metrics.mlConfidenceScore)
  const signalStrength = metricNumber(metrics.mlSignalStrength)
  const qualityWeight = metricNumber(metrics.mlQualityWeight)
  return {
    direction: String(metrics.mlDirectionText || '中性'),
    confidence: levelText(String(metrics.mlConfidenceLevel || 'LOW')),
    modelQuality: levelText(String(metrics.mlModelQualityLevel || 'LOW')),
    probabilityText: probability === null ? '--' : percentUnsigned(probability * 100, 0),
    confidenceScoreText: confidenceScore === null ? '--' : percentUnsigned(confidenceScore * 100, 0),
    signalStrengthText: signalStrength === null ? '--' : percentUnsigned(signalStrength * 100, 0),
    qualityWeightText: qualityWeight === null ? '--' : percentUnsigned(qualityWeight * 100, 0),
    expectedText: expectedReturn === null ? '--' : percent(expectedReturn, 2),
    bandText: lower === null || upper === null ? '收益区间不足' : `${percent(lower, 2)} 至 ${percent(upper, 2)}`,
    adjustmentText: adjustment === null ? '--' : adjustment.toFixed(2)
  }
})
const quantMetricItems = computed(() => {
  const signal = quantSignal.value
  const metrics = parseMetrics(signal?.metricsJson)
  if (!signal) return []
  return [
    {
      key: 'trend',
      label: '趋势分',
      score: signal.trendScore,
      weight: '35%',
      formula: '50 + 5/20/60日收益 + 20日均线偏离 + 20日趋势斜率 - 连跌惩罚',
      calculation: trendCalculation(signal.trendScore, metrics),
      summary: '衡量基金净值走势是否持续向上，趋势越顺、均线越强，分数越高。',
      terms: [
        term('20日均线偏离', '当前净值相对近20日平均净值的偏离，偏高说明短期强势，也可能偏热。'),
        term('20日趋势斜率', '近20日净值曲线的上行或下行速度，用来判断趋势方向。'),
        term('连跌惩罚', '连续下跌超过2天后开始扣分，避免短期下行趋势被忽略。')
      ],
      factors: [
        factor('5日收益', percentValue(metrics.return5d)),
        factor('20日收益', percentValue(metrics.return20d)),
        factor('60日收益', percentValue(metrics.return60d)),
        factor('20日均线偏离', percentValue(metrics.ma20Deviation)),
        factor('连跌天数', integerValue(metrics.consecutiveDownDays))
      ]
    },
    {
      key: 'opportunity',
      label: '机会分',
      score: signal.opportunityScore,
      weight: '25%',
      formula: '50 - 短期涨幅/均线偏离 + 中期正收益加分 - 20日大回撤惩罚',
      calculation: opportunityCalculation(signal.opportunityScore, metrics),
      summary: '衡量当前是否还有合适介入空间，涨太急会扣分，中期走强会加分。',
      terms: [
        term('中期正收益加分', '20日、60日收益为正时给予加分，表示中期走势仍有支撑。'),
        term('短期涨幅扣分', '5日涨幅过高会扣分，避免追在短期过热位置。'),
        term('20日大回撤惩罚', '近20日最大回撤超过10%后开始扣分，回撤越深机会分越低。')
      ],
      factors: [
        factor('20日收益', percentValue(metrics.return20d)),
        factor('60日收益', percentValue(metrics.return60d)),
        factor('5日收益', percentValue(metrics.return5d)),
        factor('20日回撤', percentValue(metrics.maxDrawdown20d)),
        factor('20日均线偏离', percentValue(metrics.ma20Deviation))
      ]
    },
    {
      key: 'risk',
      label: '风险分',
      score: signal.riskScore,
      weight: '15%',
      formula: '78 - 20日波动率温和惩罚 - 60日回撤惩罚 - 亏损天数惩罚，并按风险偏好修正',
      calculation: riskCalculation(signal.riskScore, metrics, signal.riskLevel),
      summary: '衡量波动和回撤压力。对主动基金、指数基金和 ETF，高波动主要限制加仓，不再单独触发减仓。',
      terms: [
        term('20日波动率', '近20日净值涨跌的年化波动水平，越高代表短期不稳定，但权益基金不会仅因高波动被卖出。'),
        term('60日最大回撤', '近60日从阶段高点到低点的最大跌幅，用来衡量下跌深度。'),
        term('亏损天数惩罚', '近20日亏损天数超过一半后扣分，说明下跌频率偏高。')
      ],
      factors: [
        factor('20日波动率', percentValue(metrics.volatility20d)),
        factor('60日最大回撤', percentValue(metrics.maxDrawdown60d)),
        factor('亏损天数占比', percentValue(metrics.lossDayRatio20d)),
        factor('下行波动率', percentValue(metrics.downsideVolatility20d))
      ]
    },
    {
      key: 'position',
      label: '仓位分',
      score: signal.positionScore,
      weight: '15%',
      formula: '92 - 单基金仓位占限额惩罚 + 盈利缓冲 - 浮亏压力',
      calculation: positionCalculation(signal.positionScore, metrics),
      summary: '衡量单只基金是否接近配置上限，避免单基过度集中；盈利缓冲会小幅加分。',
      terms: [
        term('单基占限额', '当前单只基金仓位占个人风险上限的比例，越接近1越接近上限。'),
        term('盈利缓冲', '当前持仓收益为正时形成的安全垫，最多按20%收益参与加分。'),
        term('浮亏压力', '当前持仓收益为负时的亏损幅度，亏得越多扣分越多。')
      ],
      factors: [
        factor('单基金仓位', percentValue(metrics.positionRate)),
        factor('单基占限额', ratioValue(metrics.positionToSingleLimit)),
        factor('盈利缓冲', percentValue(metrics.profitBuffer)),
        factor('浮亏压力', percentValue(metrics.lossPressure))
      ]
    },
    {
      key: 'momentum',
      label: '动量分',
      score: signal.momentumScore,
      weight: '10%',
      formula: '50 + 关联板块涨跌 + 当日估值涨跌 + 连涨奖励 - 连跌惩罚',
      calculation: momentumCalculation(signal.momentumScore, metrics),
      summary: '衡量短期动量和估值热度，板块与估值同步走强会抬高分数。',
      terms: [
        term('关联板块', '基金重仓或相关主题板块的当日表现，用来估算短期情绪。'),
        term('当日估值', '盘中估算净值涨跌，不等于晚间正式净值。'),
        term('连涨奖励', '连续上涨天数越多，短期动量加分越高，但最多按4天计算。'),
        term('连跌惩罚', '连续下跌会扣动量分，最多按4天计算。')
      ],
      factors: [
        factor('关联板块', percentValue(metrics.themeRate)),
        factor('当日估值', percentValue(metrics.estimateGrowthRate)),
        factor('连涨天数', integerValue(metrics.consecutiveUpDays)),
        factor('连跌天数', integerValue(metrics.consecutiveDownDays))
      ]
    }
  ]
})

onMounted(async () => {
  try {
    const [holdingList, history] = await Promise.all([quantApi.holdings(), quantApi.aiHistory()])
    holdings.value = holdingList
    const activeHoldingIds = new Set(holdingList.map((item) => item.id))
    reports.value = history.filter((report) => activeHoldingIds.has(report.holdingId))
    const queryHoldingId = Number(route.query.holdingId)
    selectedHoldingId.value = Number.isFinite(queryHoldingId) && activeHoldingIds.has(queryHoldingId)
      ? queryHoldingId
      : reports.value[0]?.holdingId || holdingList[0]?.id
    selected.value = preferredReport(selectedHoldingId.value) || undefined
    await loadQuantSignal(selectedHoldingId.value)
  } finally {
    loading.value = false
  }
})

function preferredReport(holdingId?: number) {
  const scoped = holdingId ? reports.value.filter((item) => item.holdingId === holdingId) : reports.value
  return [...scoped].sort((left, right) => {
    const timeDiff = Date.parse(right.analysisTime) - Date.parse(left.analysisTime)
    if (timeDiff !== 0) return timeDiff
    return right.id - left.id
  })[0]
}

function cleanAdvisoryText(value: string) {
  return value
    .replace(/[；;，,。\s]*(买卖建议)?仅供参考[，,、]?(不构成投资建议)?[，,、]?(不承诺收益)?[；;，,。\s]*/g, '')
    .replace(/[；;，,。\s]*用户(须|必须)?自行到原基金平台手动操作[。.\s]*/g, '')
    .trim()
}

function parseMetrics(value?: string | null) {
  if (!value) return {}
  try {
    return JSON.parse(value) as Record<string, unknown>
  } catch {
    return {}
  }
}

function factor(label: string, value: string) {
  return { label, value }
}

function term(label: string, description: string) {
  return { label, description }
}

function metricNumber(value: unknown) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function levelText(value: string) {
  if (value === 'HIGH') return '高'
  if (value === 'MEDIUM') return '中'
  if (value === 'LOW') return '低'
  return value || '--'
}

function percentValue(value: unknown) {
  const numberValue = metricNumber(value)
  return numberValue === null ? '--' : percent(numberValue, 2)
}

function integerValue(value: unknown) {
  const numberValue = metricNumber(value)
  return numberValue === null ? '--' : String(Math.round(numberValue))
}

function ratioValue(value: unknown) {
  const numberValue = metricNumber(value)
  return numberValue === null ? '--' : `${numberValue.toFixed(2)}x`
}

function metric(value: unknown, fallback = 0) {
  return metricNumber(value) ?? fallback
}

function clampScore(value: number) {
  return Math.max(0, Math.min(100, value))
}

function scoreCalc(raw: number, finalScore: number) {
  const clamped = clampScore(raw)
  const suffix = Math.abs(clamped - raw) > 0.01 ? `，截断为 ${finalScore.toFixed(1)}` : ''
  return `= ${raw.toFixed(1)}${suffix}`
}

function trendCalculation(finalScore: number, metrics: Record<string, unknown>) {
  const return5d = metric(metrics.return5d)
  const return20d = metric(metrics.return20d)
  const return60d = metric(metrics.return60d)
  const ma20Deviation = metric(metrics.ma20Deviation)
  const trendSlope20d = metric(metrics.trendSlope20d)
  const downPenalty = Math.max(metric(metrics.consecutiveDownDays) - 2, 0) * 5
  const raw = 50 + return5d * 1.2 + return20d * 1.1 + return60d * 0.35 + ma20Deviation * 0.7 + trendSlope20d * 6 - downPenalty
  return `50 + ${return5d.toFixed(2)}×1.2 + ${return20d.toFixed(2)}×1.1 + ${return60d.toFixed(2)}×0.35 + ${ma20Deviation.toFixed(2)}×0.7 + ${trendSlope20d.toFixed(2)}×6 - ${downPenalty.toFixed(1)} ${scoreCalc(raw, finalScore)}`
}

function opportunityCalculation(finalScore: number, metrics: Record<string, unknown>) {
  const ma20Deviation = metric(metrics.ma20Deviation)
  const return5d = metric(metrics.return5d)
  const return20dBonus = Math.max(metric(metrics.return20d), 0) * 0.4
  const return60dBonus = Math.max(metric(metrics.return60d), 0) * 0.2
  const drawdownPenalty = Math.max(Math.abs(metric(metrics.maxDrawdown20d)) - 10, 0) * 1.2
  const raw = 50 - ma20Deviation - return5d * 0.6 + return20dBonus + return60dBonus - drawdownPenalty
  return `50 - ${ma20Deviation.toFixed(2)} - ${return5d.toFixed(2)}×0.6 + ${return20dBonus.toFixed(1)} + ${return60dBonus.toFixed(1)} - ${drawdownPenalty.toFixed(1)} ${scoreCalc(raw, finalScore)}`
}

function riskCalculation(finalScore: number, metrics: Record<string, unknown>, riskLevel: string) {
  const volatility20d = metric(metrics.volatility20d)
  const maxDrawdown60d = Math.abs(metric(metrics.maxDrawdown60d))
  const lossPenalty = Math.max(metric(metrics.lossDayRatio20d) - 50, 0) * 0.4
  const profileAdjustment = riskLevel === 'LOW' ? -6 : riskLevel === 'HIGH' ? 4 : 0
  const raw = 78 - volatility20d * 0.45 - maxDrawdown60d * 1.2 - lossPenalty + profileAdjustment
  return `78 - ${volatility20d.toFixed(2)}×0.45 - ${maxDrawdown60d.toFixed(2)}×1.2 - ${lossPenalty.toFixed(1)} ${profileAdjustment ? `${profileAdjustment > 0 ? '+' : '-'} ${Math.abs(profileAdjustment)}` : ''} ${scoreCalc(raw, finalScore)}`
}

function positionCalculation(finalScore: number, metrics: Record<string, unknown>) {
  const positionRatio = metric(metrics.positionToSingleLimit)
  const profitBonus = Math.min(metric(metrics.profitBuffer), 20) * 0.4
  const lossPenalty = metric(metrics.lossPressure) * 1.2
  const reducePenalty = metrics.shouldReduceByPosition ? 25 : 0
  const raw = 92 - positionRatio * 35 + profitBonus - lossPenalty - reducePenalty
  return `92 - ${positionRatio.toFixed(2)}×35 + ${profitBonus.toFixed(1)} - ${lossPenalty.toFixed(1)} - ${reducePenalty.toFixed(1)} ${scoreCalc(raw, finalScore)}`
}

function momentumCalculation(finalScore: number, metrics: Record<string, unknown>) {
  const themeRate = metric(metrics.themeRate)
  const estimateGrowthRate = metric(metrics.estimateGrowthRate)
  const upBonus = Math.min(metric(metrics.consecutiveUpDays), 4) * 3
  const downPenalty = Math.min(metric(metrics.consecutiveDownDays), 4) * 4
  const raw = 50 + themeRate * 2.5 + estimateGrowthRate * 2 + upBonus - downPenalty
  return `50 + ${themeRate.toFixed(2)}×2.5 + ${estimateGrowthRate.toFixed(2)}×2 + ${upBonus.toFixed(1)} - ${downPenalty.toFixed(1)} ${scoreCalc(raw, finalScore)}`
}

watch(selectedHoldingId, (holdingId) => {
  showAllHistoryReports.value = false
  selected.value = preferredReport(holdingId)
  void loadQuantSignal(holdingId)
})

function selectReport(report: AiAnalysisReport) {
  selected.value = report
  selectedHoldingId.value = report.holdingId
}

async function generate() {
  const holdingId = selectedHoldingId.value || holdings.value[0]?.id
  if (!holdingId) {
    ElMessage.warning('请先选择持仓基金')
    return
  }
  generating.value = true
  try {
    quantSignal.value = await quantApi.analyzeQuantHolding(holdingId)
    const report = await quantApi.generateAiAnalysis(holdingId)
    reports.value = [report, ...reports.value.filter((item) => item.id !== report.id)]
    selected.value = report
    selectedHoldingId.value = report.holdingId
    ElMessage.success(report.fallbackUsed ? 'AI 调用失败，已按量化建议生成解释' : '量化建议和 AI 解释已生成')
  } finally {
    generating.value = false
  }
}

async function loadQuantSignal(holdingId?: number) {
  if (!holdingId) {
    quantSignal.value = null
    return
  }
  const signals = await quantApi.quantSignals({ holdingId }).catch(() => [])
  quantSignal.value = signals[0] || null
}

</script>

<template>
  <LoadingState v-if="loading" text="正在加载 AI 分析" />
  <div v-else class="screen-grid">
    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">AI 量化分析</h2>
        <div class="toolbar-row">
          <select v-model.number="selectedHoldingId" class="form-control">
            <option v-for="item in holdings" :key="item.id" :value="item.id">
              {{ item.fundCode }} · {{ item.fundName }}
            </option>
          </select>
          <button class="primary-button" :disabled="generating" @click="generate">
            {{ generating ? '量化分析中' : '生成量化分析' }}
          </button>
        </div>
      </div>

      <div v-if="selected" class="panel-body ai-analysis-body">
        <div class="ai-report-hero">
          <div class="ai-report-main">
            <span class="ai-report-label">当前量化结论</span>
            <div class="ai-report-action">
              <ActionTag :action="selected.action" :text="selected.actionText" />
              <strong>{{ selectedConclusion }}</strong>
            </div>
            <div class="fund-badges ai-report-badges">
              <span>{{ selected.fundCode }}</span>
              <span>{{ selectedHolding?.fundName || '持仓基金' }}</span>
              <span>{{ selectedHolding?.fundType || '类型待同步' }}</span>
              <span v-if="quantSignal">{{ quantSignal.fallbackUsed ? 'Java fallback' : 'Python 规则引擎' }}</span>
              <span v-if="quantSignal">{{ quantSignal.modelVersion }}</span>
            </div>
          </div>
          <div v-if="quantSignal" class="ai-score-summary">
            <div>
              <span>量化评分</span>
              <strong>{{ quantSignal.totalScore.toFixed(1) }}</strong>
            </div>
            <div>
              <span>置信度</span>
              <strong>{{ percentUnsigned(quantSignal.confidence * 100, 0) }}</strong>
            </div>
          </div>
        </div>

        <div class="metric-row ai-metric-row">
          <MetricTile label="建议金额" :value="money(selected.suggestAmount)" :delta="selected.suggestAmount ? '模拟记录参考' : '不建议操作'" tone="warning" />
          <MetricTile label="建议比例" :value="actionPercent(selected.action, selected.suggestRatio)" sub-label="由用户自行确认" tone="warning" />
          <MetricTile label="信心分" :value="percentUnsigned(selected.confidence * 100, 0)" sub-label="模型置信度" tone="info" />
          <MetricTile label="风险等级" :value="selected.riskLevel" sub-label="LOW / MEDIUM / HIGH" tone="fall" />
          <MetricTile v-if="mlBusinessInfo" label="ML方向" :value="mlBusinessInfo.direction" :delta="`偏强概率 ${mlBusinessInfo.probabilityText}`" tone="info" />
          <MetricTile v-if="mlBusinessInfo" label="本次可信度" :value="mlBusinessInfo.confidenceScoreText" :sub-label="`模型质量 ${mlBusinessInfo.modelQuality} · 权重 ${mlBusinessInfo.qualityWeightText}`" :delta="`信号强度 ${mlBusinessInfo.signalStrengthText}`" tone="info" />
          <MetricTile v-if="mlBusinessInfo" label="预测收益区间" :value="mlBusinessInfo.expectedText" :delta="mlBusinessInfo.bandText" tone="warning" />
          <MetricTile label="持仓金额" :value="selectedHolding ? money(selectedHolding.holdingAmount) : '--'" />
          <MetricTile label="当日收益" :value="selectedHolding ? signed(selectedHolding.dailyProfit) : '--'" :class="selectedHolding ? toneClass(selectedHolding.dailyProfit) : ''" />
        </div>

        <div class="ai-report-note">
          <span>{{ selectedDisclaimer }}</span>
        </div>
      </div>

      <div v-else class="panel-body">
        <EmptyState title="暂无 AI 建议" description="选择持仓后点击生成量化分析。" />
      </div>
    </section>

    <section v-if="selected" class="panel ai-report-detail-panel">
      <div class="panel-header">
        <h2 class="panel-title">量化评分拆解</h2>
        <span class="item-meta">按当前 Python 规则引擎口径展示</span>
      </div>
      <div class="panel-body quant-score-layout">
        <div class="quant-score-grid">
          <article v-for="item in quantMetricItems" :key="item.key" class="quant-score-card">
            <div class="quant-score-head">
              <div>
                <span>{{ item.label }}</span>
                <strong>{{ item.score.toFixed(1) }}</strong>
              </div>
              <em>权重 {{ item.weight }}</em>
            </div>
            <p>{{ item.summary }}</p>
            <div class="quant-factor-list">
              <span v-for="metric in item.factors" :key="metric.label">
                <small>{{ metric.label }}</small>
                <b>{{ metric.value }}</b>
              </span>
            </div>
            <div class="quant-formula">{{ item.formula }}</div>
            <div class="quant-calculation">
              <small>计算过程</small>
              <span>{{ item.calculation }}</span>
            </div>
            <div class="quant-term-list">
              <span v-for="termItem in item.terms" :key="termItem.label">
                <b>{{ termItem.label }}</b>
                {{ termItem.description }}
              </span>
            </div>
          </article>
        </div>

        <aside class="quant-score-side">
          <section class="ai-report-section">
            <div class="ai-section-title">
              <h3>动作判定</h3>
              <span>{{ selected.action }}</span>
            </div>
            <div class="ai-side-card compact">
              <span>当前结论</span>
              <strong>{{ selected.actionText }}</strong>
              <small>{{ selected.strategy }}</small>
            </div>
            <div class="ai-side-card compact">
              <span>执行参考</span>
              <strong>{{ actionPercent(selected.action, selected.suggestRatio) }}</strong>
              <small>{{ selected.suggestAmount ? `约 ${money(selected.suggestAmount)} 元` : '不建议操作' }}</small>
            </div>
          </section>

          <section class="ai-report-section ai-risk-section">
          <div class="ai-section-title">
            <h3>风险提示</h3>
            <span>{{ selected.risks.length }} 条</span>
          </div>
          <div class="bullet-list warning ai-bullet-list">
              <p v-for="risk in selected.risks" :key="risk">{{ risk }}</p>
            </div>
          </section>
        </aside>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">历史 AI 分析记录</h2>
        <div class="history-header-actions">
          <span class="item-meta">{{ filteredReports.length }} 条</span>
          <button v-if="historyHasMore" class="panel-link" type="button" @click="showAllHistoryReports = !showAllHistoryReports">
            {{ showAllHistoryReports ? '收起' : `查看全部 ${filteredReports.length} 条` }}
          </button>
        </div>
      </div>
      <div class="panel-body ai-history-body">
        <div v-if="filteredReports.length" :class="['ai-history-scroll', { expanded: showAllHistoryReports }]">
        <table class="terminal-table ai-history-table">
          <thead><tr><th>时间</th><th>基金</th><th>动作</th><th>建议金额</th><th>风险</th><th>模型</th><th>摘要</th></tr></thead>
          <tbody>
            <tr v-for="item in visibleHistoryReports" :key="item.id" :class="{ selected: selected?.id === item.id }" @click="selectReport(item)">
              <td>{{ formatDateTime(item.analysisTime) }}</td>
              <td>{{ item.fundCode }}</td>
              <td><ActionTag :action="item.action" :text="item.actionText" /></td>
              <td>{{ money(item.suggestAmount) }}</td>
              <td>{{ item.riskLevel }}</td>
              <td>{{ item.modelName }}</td>
              <td>{{ item.dataSummary }}</td>
            </tr>
          </tbody>
        </table>
        </div>
        <EmptyState v-else title="暂无历史分析" description="当前持仓还没有生成过 AI 分析。" />
      </div>
    </section>

    <DisclaimerBar />
  </div>
</template>
