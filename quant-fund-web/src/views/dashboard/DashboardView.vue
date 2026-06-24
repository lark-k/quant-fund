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
import { estimateHoldingOption, positionOption, profitTrendOption } from '@/components/charts/chartOptions'
import { quantApi } from '@/api/quant'
import { useDashboardStore } from '@/stores/dashboard'
import { metricTone, money, percent, signed, toneClass } from '@/utils/format'

const store = useDashboardStore()
const router = useRouter()
const activeRange = ref('近1年')
const refreshing = ref(false)
let refreshTimer: number | undefined

onMounted(() => {
  store.fetchOverview(true)
  refreshTimer = window.setInterval(autoRefreshEstimate, 60000)
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

const overview = computed(() => store.overview)
const summary = computed(() => overview.value?.summary)
const trendOption = computed(() => profitTrendOption(overview.value?.profitTrend || []))
const allocationOption = computed(() => positionOption(overview.value?.positionDistribution || []))
const estimateOption = computed(() => estimateHoldingOption(overview.value?.topHoldings || []))
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
const estimateRefreshRate = computed(() => {
  const status = overview.value?.estimateStatus
  if (!status?.trackedFundCount) return 0
  return status.refreshedTodayCount / status.trackedFundCount * 100
})
const officialSynced = computed(() => overview.value?.estimateStatus.statusText.includes('正式净值已同步') || false)
const finalNavUpdatedCount = computed(() => overview.value?.topHoldings.filter((item) => item.officialNavUpdated).length || 0)
const allFinalNavUpdated = computed(() => {
  const holdings = overview.value?.topHoldings || []
  return holdings.length > 0 && holdings.every((item) => item.officialNavUpdated)
})
const dailyProfitRate = computed(() => {
  if (!summary.value) return 0
  return safeRatio(summary.value.dailyProfit, summary.value.totalAsset)
})

function safeRatio(part: number, total: number) {
  return total > 0 ? part / total * 100 : 0
}

function displaySignalType(type: string) {
  const labels: Record<string, string> = {
    POSITION_MONITOR: '仓位监控',
    TAKE_PROFIT: '止盈回撤',
    LOW_BUY: '低吸观察',
    RISK_ALERT: '风险预警'
  }
  return labels[type] || type
}

function updatedBadgeText(date?: string | null) {
  if (!date) return '已更新'
  const today = new Date().toISOString().slice(0, 10)
  return date === today ? '已更新' : `已更新至 ${date.slice(5)}`
}

function relatedThemeText(theme?: string | null) {
  return theme && theme !== '主动权益' ? theme : '重仓板块待同步'
}

function isIntradayEstimateWindow(date = new Date()) {
  const day = date.getDay()
  if (day === 0 || day === 6) return false
  const minutes = date.getHours() * 60 + date.getMinutes()
  return (minutes >= 9 * 60 + 25 && minutes <= 11 * 60 + 35)
    || (minutes >= 12 * 60 + 55 && minutes <= 15 * 60 + 5)
}

async function refreshEstimate() {
  const holdings = overview.value?.topHoldings || []
  if (!holdings.length) {
    ElMessage.warning('暂无可刷新的持仓基金')
    return
  }
  refreshing.value = true
  try {
    const officialHoldings = await quantApi.syncOfficialNav()
    const officialUpdatedCount = officialHoldings.filter((holding) => holding.officialNavUpdated).length
    if (!isIntradayEstimateWindow()) {
      await store.fetchOverview()
      if (officialUpdatedCount) {
        ElMessage.success(`已同步 ${officialUpdatedCount} 只基金最新正式净值，并按最终净值重算收益`)
      } else {
        ElMessage.warning('当前不在盘中估值时间，暂不刷新盘中估值')
      }
      return
    }
    const pendingHoldings = officialHoldings.length ? officialHoldings.filter((holding) => !holding.officialNavUpdated) : holdings
    const results = await Promise.allSettled(pendingHoldings.map(async (holding) => {
      await quantApi.refreshEstimate(holding.fundCode)
      await quantApi.recalculateHolding(holding.id)
    }))
    await store.fetchOverview()
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

async function autoRefreshEstimate() {
  if (refreshing.value || !overview.value?.topHoldings.length) return
  if (!isIntradayEstimateWindow()) return
  refreshing.value = true
  try {
    const officialHoldings = await quantApi.syncOfficialNav()
    const pendingHoldings = officialHoldings.length ? officialHoldings.filter((holding) => !holding.officialNavUpdated) : overview.value.topHoldings
    await Promise.allSettled(pendingHoldings.map(async (holding) => {
      await quantApi.refreshEstimate(holding.fundCode)
      await quantApi.recalculateHolding(holding.id)
    }))
    await store.fetchOverview()
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
                      <strong v-if="holding.officialNavUpdated" class="updated-badge">{{ updatedBadgeText(holding.officialNavDate) }}</strong>
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

        <section class="panel trend-panel">
          <div class="panel-header">
            <h2 class="panel-title">收益走势与回撤</h2>
            <button class="panel-link" @click="go('/profit-analysis')">更多 ›</button>
          </div>
          <div class="panel-body">
            <BaseChart :option="trendOption" :height="246" />
            <div class="segmented">
              <button v-for="range in ['近1月', '近3月', '近6月', '今年以来', '近1年']" :key="range" :class="{ active: activeRange === range }" @click="activeRange = range">{{ range }}</button>
            </div>
          </div>
        </section>
      </div>

      <div class="dashboard-market-column">
    <section class="panel estimate-panel">
      <div class="panel-header">
        <h2 class="panel-title">{{ allFinalNavUpdated ? '最新净值（已更新）' : '盘中估值（估算）' }}</h2>
        <button class="panel-link" :disabled="refreshing" @click="refreshEstimate">{{ refreshing ? '同步中' : '同步净值' }}</button>
      </div>
      <div class="panel-body estimate-stack">
        <div class="disclaimer-bar estimate-status-line">
          <span>{{ allFinalNavUpdated ? '最新正式净值已更新，收益已按最终净值计算' : overview.estimateStatus.statusText }} · {{ overview.estimateStatus.latestEstimateTime || '--' }}</span>
          <strong v-if="allFinalNavUpdated || officialSynced" class="sync-badge">已更新</strong>
        </div>
        <MetricTile label="当日估算收益（元）" :value="signed(summary.dailyProfit)" sub-label="当前仓位加权估值涨跌" :delta="percent(dailyProfitRate)" :tone="metricTone(summary.dailyProfit)" />
        <div class="mini-stat-grid estimate-stats">
          <div><span>跟踪基金</span><strong>{{ overview.estimateStatus.trackedFundCount }}</strong></div>
          <div><span>最新已更新</span><strong>{{ finalNavUpdatedCount || overview.estimateStatus.refreshedTodayCount }}</strong></div>
          <div><span>刷新覆盖</span><strong>{{ percent(estimateRefreshRate, 0) }}</strong></div>
        </div>
        <BaseChart :option="estimateOption" :height="180" />
      </div>
    </section>

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
            <div class="item-copy suggestion-strategy">{{ item.fundCode }} · {{ item.strategy }}</div>
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
            所有建议仅供参考，最终买卖由用户在原平台手动确认。
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
      <div class="panel-body signal-list">
        <article v-for="signal in overview.latestStrategySignals" :key="signal.id" class="signal-item">
          <div class="item-title">
            <span>{{ signal.signalTime.slice(11, 16) }} · {{ displaySignalType(signal.signalType) }}</span>
            <ActionTag :action="signal.action" :text="signal.actionText" />
          </div>
          <div class="signal-meta-row">
            <span>{{ signal.fundCode }}</span>
            <span>强度 {{ percent(signal.confidence * 100, 0) }}</span>
            <span>{{ signal.riskLevel === 'HIGH' ? '高风险' : signal.riskLevel === 'MEDIUM' ? '中风险' : '低风险' }}</span>
          </div>
          <div class="item-copy">{{ signal.reasons[0] }}</div>
        </article>
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

    <section class="panel allocation-panel">
      <div class="panel-header">
        <h2 class="panel-title">持仓分布</h2>
        <button class="panel-link" @click="go('/holdings')">更多 ›</button>
      </div>
      <div class="panel-body">
        <BaseChart :option="allocationOption" :height="172" />
        <table class="terminal-table">
          <tbody>
            <tr v-for="item in overview.positionDistribution" :key="item.name">
              <td>{{ item.name }}</td>
              <td>{{ percent(item.rate) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel risk-panel">
      <div class="panel-header">
        <h2 class="panel-title">风险预警</h2>
        <button class="panel-link" @click="go('/ai-analysis')">更多 ›</button>
      </div>
      <div class="panel-body alert-list">
        <div class="disclaimer-bar">共 {{ overview.riskAlertCount }} 条预警</div>
        <article class="alert-item">
          <div class="item-title"><span>高 · 波动预警</span><span>09:41</span></div>
          <div class="item-copy">国投瑞银新能源混合A 波动率快速上升，近 5 日波动率 28.34%，高于历史 90% 区间。</div>
        </article>
        <article class="alert-item">
          <div class="item-title"><span>中 · 回撤预警</span><span>09:30</span></div>
          <div class="item-copy">易方达蓝筹精选混合回撤扩大，接近预警阈值。</div>
        </article>
      </div>
    </section>
      </div>
    </div>

    <div class="bottom-trade-bar">
      <span>数据来源：量化数据仓、估算数据，仅供参考</span>
      <strong>模拟交易模块：仅为模拟操作，并非真实交易</strong>
      <button class="primary-button" @click="go('/trades')">模拟交易</button>
    </div>
  </div>
</template>
