<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { quantApi } from '@/api/quant'
import type { AiAnalysisReport, FundHolding } from '@/types/domain'
import { DISCLAIMER } from '@/types/domain'
import ActionTag from '@/components/common/ActionTag.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import MetricTile from '@/components/common/MetricTile.vue'
import { money, percent, signed, toneClass } from '@/utils/format'

const route = useRoute()
const holdings = ref<FundHolding[]>([])
const reports = ref<AiAnalysisReport[]>([])
const selected = ref<AiAnalysisReport>()
const selectedHoldingId = ref<number>()
const loading = ref(true)
const generating = ref(false)

const selectedHolding = computed(() => holdings.value.find((item) => item.id === selectedHoldingId.value))
const filteredReports = computed(() => {
  if (!selectedHoldingId.value) return reports.value
  return reports.value.filter((item) => item.holdingId === selectedHoldingId.value)
})

const aiSourceText = computed(() => {
  if (!selected.value) return ''
  return selected.value.fallbackUsed ? 'AI 降级兜底' : 'DeepSeek 真实分析'
})
const selectedConclusion = computed(() => cleanAdvisoryText(selected.value?.finalConclusion || ''))
const selectedDisclaimer = computed(() => selected.value?.disclaimer || DISCLAIMER)

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
  } finally {
    loading.value = false
  }
})

function preferredReport(holdingId?: number) {
  const scoped = holdingId ? reports.value.filter((item) => item.holdingId === holdingId) : reports.value
  return scoped.find((item) => !item.fallbackUsed) || scoped[0]
}

function cleanAdvisoryText(value: string) {
  return value
    .replace(/[；;，,。\s]*(买卖建议)?仅供参考[，,、]?(不构成投资建议)?[，,、]?(不承诺收益)?[；;，,。\s]*/g, '')
    .replace(/[；;，,。\s]*用户(须|必须)?自行到原基金平台手动操作[。.\s]*/g, '')
    .trim()
}

watch(selectedHoldingId, (holdingId) => {
  selected.value = preferredReport(holdingId)
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
    const report = await quantApi.generateAiAnalysis(holdingId)
    reports.value = [report, ...reports.value.filter((item) => item.id !== report.id)]
    selected.value = report
    selectedHoldingId.value = report.holdingId
    ElMessage.success(report.fallbackUsed ? 'AI 调用失败，已降级生成 WATCH 建议' : 'DeepSeek 真实分析已生成')
  } finally {
    generating.value = false
  }
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
            {{ generating ? '生成中' : '一键生成分析' }}
          </button>
        </div>
      </div>

      <div v-if="selected" class="panel-body">
        <DisclaimerBar />
        <div class="ai-hero">
          <div class="ai-action">
            <ActionTag :action="selected.action" :text="selected.actionText" />
            <strong>{{ selectedConclusion }}</strong>
            <span>{{ selected.deadline }} · {{ selected.modelName }} · {{ aiSourceText }}</span>
          </div>
          <div class="fund-badges">
            <span>{{ selected.fundCode }}</span>
            <span>{{ selectedHolding?.fundName || '持仓基金' }}</span>
            <span>{{ selectedHolding?.fundType || '类型待同步' }}</span>
          </div>
        </div>
        <div class="disclaimer-bar">
          <span>{{ selectedDisclaimer }}</span>
        </div>

        <div class="metric-row">
          <MetricTile label="建议金额" :value="money(selected.suggestAmount)" :delta="selected.suggestAmount ? '模拟记录参考' : '不建议操作'" tone="warning" />
          <MetricTile label="建议比例" :value="percent(selected.suggestRatio)" sub-label="由用户自行确认" tone="warning" />
          <MetricTile label="信心分" :value="percent(selected.confidence * 100, 0)" sub-label="模型置信度" tone="info" />
          <MetricTile label="风险等级" :value="selected.riskLevel" sub-label="LOW / MEDIUM / HIGH" tone="fall" />
          <MetricTile label="持仓金额" :value="selectedHolding ? money(selectedHolding.holdingAmount) : '--'" />
          <MetricTile label="当日收益" :value="selectedHolding ? signed(selectedHolding.dailyProfit) : '--'" :class="selectedHolding ? toneClass(selectedHolding.dailyProfit) : ''" />
        </div>
      </div>

      <div v-else class="panel-body">
        <EmptyState title="暂无 AI 建议" description="选择持仓后点击一键生成分析。" />
      </div>
    </section>

    <div v-if="selected" class="insight-grid">
      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">触发策略</h2></div>
        <div class="panel-body insight-list">
          <div class="insight-item">
            <span>{{ selected.strategy }}</span>
            <strong>{{ selected.action }}</strong>
          </div>
          <div class="insight-item">
            <span>估值说明</span>
            <strong>非最终净值</strong>
          </div>
          <div class="insight-item">
            <span>交易性质</span>
            <strong>模拟参考</strong>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">详细理由</h2></div>
        <div class="panel-body bullet-list">
          <p v-if="selected.fallbackUsed" class="ai-fallback-note">当前报告不是 DeepSeek 真实分析，请检查后端日志或稍后重新生成。</p>
          <p v-for="reason in selected.reasons" :key="reason">{{ reason }}</p>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">风险提示</h2></div>
        <div class="panel-body bullet-list warning">
          <p v-for="risk in selected.risks" :key="risk">{{ risk }}</p>
        </div>
      </section>
    </div>

    <section v-if="selected" class="panel">
      <div class="panel-header">
        <h2 class="panel-title">数据摘要</h2>
        <span class="item-meta">AI 只能基于后端结构化指标分析</span>
      </div>
      <div class="panel-body">
        <p class="item-copy">{{ selected.dataSummary }}</p>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">历史 AI 分析记录</h2>
        <span class="item-meta">{{ filteredReports.length }} 条</span>
      </div>
      <div class="panel-body">
        <table v-if="filteredReports.length" class="terminal-table">
          <thead><tr><th>时间</th><th>基金</th><th>动作</th><th>建议金额</th><th>风险</th><th>模型</th><th>摘要</th></tr></thead>
          <tbody>
            <tr v-for="item in filteredReports" :key="item.id" @click="selectReport(item)">
              <td>{{ item.analysisTime }}</td>
              <td>{{ item.fundCode }}</td>
              <td><ActionTag :action="item.action" :text="item.actionText" /></td>
              <td>{{ money(item.suggestAmount) }}</td>
              <td>{{ item.riskLevel }}</td>
              <td>{{ item.modelName }}</td>
              <td>{{ item.dataSummary }}</td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无历史分析" description="当前持仓还没有生成过 AI 分析。" />
      </div>
    </section>

    <DisclaimerBar />
  </div>
</template>
