<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Histogram, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MetricTile from '@/components/common/MetricTile.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import { quantApi } from '@/api/quant'
import { percent, percentUnsigned } from '@/utils/format'
import { buildValidationRows, significanceText, taskResultMessageLevel } from '@/utils/fundScreenerValidation'
import type { ValidationBucketRow } from '@/utils/fundScreenerValidation'
import type { FundScreenerExplain, FundScreenerRankItem, FundScreenerRankQuery, FundScreenerRecommendLevel, FundScreenerValidation, PageResponse } from '@/types/domain'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const fullRefreshing = ref(false)
const validationLoading = ref(false)
const backtestRunning = ref(false)
const detailLoading = ref(false)
const drawerVisible = ref(false)
const rankPage = ref<PageResponse<FundScreenerRankItem>>({ pageNo: 1, pageSize: 20, total: 0, records: [] })
const selectedExplain = ref<FundScreenerExplain | null>(null)
const validation = ref<FundScreenerValidation | null>(null)
const validationRows = computed(() => buildValidationRows(validation.value?.metrics || []))
const latestScoreDate = computed(() => rankPage.value.records[0]?.scoreDate || '--')
const scoredCount = computed(() => rankPage.value.total)
const includedCount = computed(() => Math.max(rankPage.value.total, rankPage.value.records.length))

const filters = reactive<FundScreenerRankQuery>({
  fundType: '',
  recommendLevel: '',
  minScore: 60,
  onlyActiveFund: false,
  pageNo: 1,
  pageSize: 20,
  sortBy: 'qualityScore'
})

const fundTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '主动权益', value: 'ACTIVE_EQUITY' },
  { label: '混合基金', value: 'MIXED' },
  { label: '指数基金', value: 'INDEX' }
]

const recommendOptions: Array<{ label: string; value: FundScreenerRecommendLevel | '' }> = [
  { label: '全部等级', value: '' },
  { label: '强烈关注', value: 'STRONG' },
  { label: '观察名单', value: 'WATCH' },
  { label: '中性', value: 'NEUTRAL' },
  { label: '回避', value: 'AVOID' }
]
const validationHorizons = [20, 60, 120] as const

function levelText(level: string) {
  const map: Record<string, string> = {
    STRONG: '强烈关注',
    WATCH: '观察',
    NEUTRAL: '中性',
    AVOID: '回避'
  }
  return map[level] || level
}

function levelType(level: string) {
  if (level === 'STRONG') return 'success'
  if (level === 'WATCH') return 'warning'
  if (level === 'AVOID') return 'danger'
  return 'info'
}

function scoreTone(value?: number | null) {
  if ((value || 0) >= 85) return 'rise'
  if ((value || 0) >= 75) return 'info'
  if ((value || 0) >= 60) return 'warning'
  return 'fall'
}

function numberText(value?: number | null, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return '--'
  return value.toFixed(digits)
}

function scoreValue(primary?: number | null, fallback?: number | null) {
  if (primary !== null && primary !== undefined && Number.isFinite(primary) && primary > 0) {
    return primary
  }
  return fallback
}

function signedPercent(value?: number | null, digits = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) return '--'
  return percent(value, digits)
}

function unsignedPercent(value?: number | null, digits = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) return '--'
  return percentUnsigned(value, digits)
}

function validationStatusText(status?: FundScreenerValidation['status']) {
  const labels: Record<FundScreenerValidation['status'], string> = {
    EFFECTIVE: '策略有效',
    NEUTRAL: '策略中性',
    FAILED: '策略失效',
    INSUFFICIENT: '样本不足'
  }
  return status ? labels[status] : '尚未验证'
}

function validationStatusType(status?: FundScreenerValidation['status']) {
  if (status === 'EFFECTIVE') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'NEUTRAL') return 'warning'
  return 'info'
}

function metricAt(row: ValidationBucketRow, horizon: 20 | 60 | 120) {
  return row.horizons[horizon]
}

function queryParams(): FundScreenerRankQuery {
  return {
    ...filters,
    fundType: filters.fundType || undefined,
    recommendLevel: filters.recommendLevel || undefined
  }
}

async function loadRank() {
  loading.value = true
  try {
    rankPage.value = await quantApi.fundScreenerRank(queryParams())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '基金优选榜单加载失败')
    rankPage.value = { pageNo: filters.pageNo || 1, pageSize: filters.pageSize || 20, total: 0, records: [] }
  } finally {
    loading.value = false
  }
}

async function loadValidation() {
  validationLoading.value = true
  try {
    validation.value = await quantApi.fundScreenerValidation()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '策略验证结果加载失败')
  } finally {
    validationLoading.value = false
  }
}

async function runBacktest() {
  backtestRunning.value = true
  try {
    const result = await quantApi.runFundScreenerBacktest()
    const message = [result.message || '增量回测执行完成', ...result.errorSummaries.slice(0, 2)].join('；')
    const level = taskResultMessageLevel(result.status)
    if (level === 'error') ElMessage.error(message)
    else if (level === 'warning') ElMessage.warning(message)
    else ElMessage.success(message)
    await loadValidation()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '增量回测执行失败')
  } finally {
    backtestRunning.value = false
  }
}

async function refreshScore() {
  refreshing.value = true
  try {
    const result = await quantApi.refreshFundScreenerScore()
    ElMessage.info(result.message || '评分刷新任务已提交')
    await loadRank()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '评分刷新失败')
  } finally {
    refreshing.value = false
  }
}

async function refreshFullPath() {
  fullRefreshing.value = true
  try {
    const result = await quantApi.refreshFundScreenerFull()
    ElMessage.success(result.message || '完整同步路径执行完成')
    await loadRank()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '完整同步路径执行失败')
  } finally {
    fullRefreshing.value = false
  }
}

async function openExplain(row: FundScreenerRankItem) {
  drawerVisible.value = true
  detailLoading.value = true
  selectedExplain.value = null
  try {
    selectedExplain.value = await quantApi.fundScreenerExplain(row.fundCode)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '评分解释加载失败')
  } finally {
    detailLoading.value = false
  }
}

function viewFund(row: FundScreenerRankItem) {
  router.push({ path: '/fund-detail', query: { fundCode: row.fundCode } })
}

function handlePageChange(pageNo: number) {
  filters.pageNo = pageNo
  void loadRank()
}

function handlePageSizeChange(pageSize: number) {
  filters.pageSize = pageSize
  filters.pageNo = 1
  void loadRank()
}

function resetPageAndLoad() {
  filters.pageNo = 1
  void loadRank()
}

onMounted(() => {
  void loadRank()
  void loadValidation()
})
</script>

<template>
  <section class="fund-screener-view">
    <div class="workspace-head">
      <div>
        <h2>基金优选</h2>
        <p>基于独立优选库、因子快照和可解释评分的全市场基金筛选工作台。</p>
      </div>
      <div class="head-actions">
        <el-button type="primary" :loading="refreshing" :disabled="fullRefreshing" @click="refreshScore">
          <el-icon><Refresh /></el-icon>
          <span>刷新评分</span>
        </el-button>
        <el-button type="warning" :loading="fullRefreshing" :disabled="refreshing" @click="refreshFullPath">
          <el-icon><Refresh /></el-icon>
          <span>完整补跑</span>
        </el-button>
      </div>
    </div>

    <section class="filter-band">
      <el-select v-model="filters.fundType" placeholder="基金类型" @change="resetPageAndLoad">
        <el-option v-for="item in fundTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.recommendLevel" placeholder="推荐等级" @change="resetPageAndLoad">
        <el-option v-for="item in recommendOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <label class="filter-field">
        <span>最低评分</span>
        <el-input-number v-model="filters.minScore" :min="0" :max="100" :step="5" controls-position="right" @change="resetPageAndLoad" />
      </label>
      <el-checkbox v-model="filters.onlyActiveFund" @change="resetPageAndLoad">仅主动管理</el-checkbox>
    </section>

    <div class="metric-row">
      <MetricTile label="覆盖基金" :value="`${includedCount} 只`" sub-label="当前筛选条件" tone="info" />
      <MetricTile label="评分池" :value="`${rankPage.total} 只`" sub-label="后端分页返回" tone="neutral" />
      <MetricTile label="已评分" :value="`${scoredCount} 只`" sub-label="最新榜单样本" tone="rise" />
      <MetricTile label="评分日期" :value="latestScoreDate" sub-label="最近一次评分" tone="info" />
    </div>

    <section class="validation-section">
      <div class="validation-head">
        <div>
          <div class="section-title validation-title">
            <span>策略验证</span>
            <el-tag :type="validationStatusType(validation?.status)" effect="dark">
              {{ validationStatusText(validation?.status) }}
            </el-tag>
          </div>
          <p>用历史评分后的真实净值检验分层效果；只做验证与调参建议，不会自动修改生产阈值。</p>
        </div>
        <el-button type="success" :loading="backtestRunning" @click="runBacktest">
          <el-icon><Histogram /></el-icon>
          <span>运行增量回测</span>
        </el-button>
      </div>

      <LoadingState v-if="validationLoading && !validation" title="正在加载策略验证" description="读取已缓存的历史分层回测结果。" />
      <template v-else-if="validation">
        <div class="validation-meta">
          <span>最新运行：<strong>{{ validation.latestRunDate || '--' }}</strong></span>
          <span>可用评分日期：<strong>{{ validation.earliestScoreDate || '--' }} 至 {{ validation.latestScoreDate || '--' }}</strong></span>
          <span>统计门槛：<strong>样本 ≥ {{ validation.policy.minValidationSamples }}，评分日 ≥ {{ validation.policy.minValidationScoreDates }}</strong></span>
        </div>

        <div class="validation-conclusion" :class="`validation-${validation.status.toLowerCase()}`">
          <strong>{{ validationStatusText(validation.status) }}</strong>
          <span>{{ validation.conclusion }}</span>
        </div>

        <el-table :data="validationRows" class="validation-table" stripe>
          <el-table-column prop="label" label="分层" width="100" fixed />
          <el-table-column v-for="horizon in validationHorizons" :key="horizon" :label="`${horizon}日`" min-width="255">
            <template #default="{ row }">
              <div v-if="metricAt(row, horizon)" class="validation-cell">
                <div class="validation-return">
                  <strong>{{ signedPercent(metricAt(row, horizon)?.avgForwardReturn) }}</strong>
                  <span>平均收益</span>
                </div>
                <div class="validation-details">
                  <span>胜率 {{ unsignedPercent(metricAt(row, horizon)?.winRate) }}</span>
                  <span>超额 {{ signedPercent(metricAt(row, horizon)?.avgExcessReturn) }}</span>
                  <span>最大回撤 {{ signedPercent(metricAt(row, horizon)?.maxDrawdown) }}</span>
                </div>
                <div class="validation-sample">
                  <span>{{ metricAt(row, horizon)?.sampleCount }} 个样本 / {{ metricAt(row, horizon)?.scoreDateCount }} 个评分日</span>
                  <el-tag
                    size="small"
                    :type="metricAt(row, horizon)?.statisticallySignificant ? 'success' : 'warning'"
                    effect="plain"
                  >
                    {{ significanceText(metricAt(row, horizon)) }}
                  </el-tag>
                </div>
              </div>
              <span v-else class="validation-empty">暂无到期样本</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="calibration-grid">
          <div class="policy-card">
            <strong>当前生产阈值</strong>
            <span>强烈关注：评分 ≥ {{ validation.policy.strongMinScore }} 且前 {{ validation.policy.strongTopPercent }}%</span>
            <span>观察：评分 ≥ {{ validation.policy.watchMinScore }} 或前 {{ validation.policy.watchTopPercent }}%</span>
            <span>中性：评分 ≥ {{ validation.policy.neutralMinScore }}；其余回避</span>
          </div>
          <div class="advice-card">
            <strong>算法校准建议</strong>
            <span v-for="item in validation.calibrationAdvice" :key="item">{{ item }}</span>
          </div>
        </div>
      </template>
      <EmptyState v-else title="暂无策略验证结果" description="点击“运行增量回测”，系统会计算已经具备未来净值窗口的历史评分日。" />
    </section>

    <section class="rank-section">
      <div class="section-title">
        <span>优选榜单</span>
        <small>评分结果仅供参考，不构成投资建议</small>
      </div>
      <LoadingState v-if="loading" title="正在加载基金优选榜单" description="读取独立优选评分结果。" />
      <template v-else>
        <el-table v-if="rankPage.records.length" :data="rankPage.records" class="rank-table" stripe>
          <el-table-column prop="rankNo" label="排名" width="72" fixed />
          <el-table-column label="基金" min-width="210" fixed>
            <template #default="{ row }">
              <div class="fund-cell">
                <strong>{{ row.fundName }}</strong>
                <span>{{ row.fundCode }} · {{ row.fundType }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="综合分" width="110" align="right">
            <template #default="{ row }">
              <b :class="`score-${scoreTone(row.qualityScore)}`">{{ numberText(row.qualityScore) }}</b>
            </template>
          </el-table-column>
          <el-table-column label="收益质量" width="110" align="right">
            <template #default="{ row }">{{ numberText(scoreValue(row.returnQualityScore, row.returnScore)) }}</template>
          </el-table-column>
          <el-table-column label="风险控制" width="110" align="right">
            <template #default="{ row }">{{ numberText(scoreValue(row.drawdownControlScore, row.riskScore)) }}</template>
          </el-table-column>
          <el-table-column label="稳定性" width="100" align="right">
            <template #default="{ row }">{{ numberText(scoreValue(row.consistencyScore, row.stabilityScore)) }}</template>
          </el-table-column>
          <el-table-column label="可投性" width="100" align="right">
            <template #default="{ row }">{{ numberText(scoreValue(row.investabilityScore, row.dataScore)) }}</template>
          </el-table-column>
          <el-table-column label="近3月" width="100" align="right">
            <template #default="{ row }">{{ signedPercent(row.return60d) }}</template>
          </el-table-column>
          <el-table-column label="近6月" width="100" align="right">
            <template #default="{ row }">{{ signedPercent(row.return120d) }}</template>
          </el-table-column>
          <el-table-column label="近1年" width="100" align="right">
            <template #default="{ row }">{{ signedPercent(row.return250d) }}</template>
          </el-table-column>
          <el-table-column label="最大回撤" width="110" align="right">
            <template #default="{ row }">{{ signedPercent(row.maxDrawdown120d) }}</template>
          </el-table-column>
          <el-table-column label="波动率" width="100" align="right">
            <template #default="{ row }">{{ signedPercent(row.volatility120d) }}</template>
          </el-table-column>
          <el-table-column label="同类百分位" width="120" align="right">
            <template #default="{ row }">{{ numberText(row.peerPercentile) }}%</template>
          </el-table-column>
          <el-table-column label="等级" width="108">
            <template #default="{ row }">
              <el-tag :type="levelType(row.recommendLevel)" effect="dark">{{ levelText(row.recommendLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="164" fixed="right">
            <template #default="{ row }">
              <div class="action-group">
                <el-button :icon="View" circle title="查看评分解释" @click="openExplain(row)" />
                <el-button :icon="Histogram" circle title="查看基金详情" @click="viewFund(row)" />
              </div>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-else title="暂无优选结果" description="当前筛选条件下没有评分记录，后续同步和评分批次完成后会显示榜单。" />
        <div class="pager-row">
          <el-pagination
            background
            layout="sizes, prev, pager, next, total"
            :page-size="rankPage.pageSize"
            :current-page="rankPage.pageNo"
            :total="rankPage.total"
            :page-sizes="[10, 20, 50]"
            @current-change="handlePageChange"
            @size-change="handlePageSizeChange"
          />
        </div>
      </template>
    </section>

    <DisclaimerBar text="基金优选和历史回测结果仅供参考，不构成投资建议，不承诺未来收益；历史表现不代表未来表现。" />

    <el-drawer
      v-model="drawerVisible"
      title="评分解释"
      size="420px"
      class="screener-explain-drawer"
      modal-class="screener-explain-overlay"
      header-class="screener-explain-drawer__header"
      body-class="screener-explain-drawer__body"
    >
      <LoadingState v-if="detailLoading" title="正在加载评分解释" />
      <div v-else-if="selectedExplain" class="explain-panel">
        <div class="explain-head">
          <strong>{{ selectedExplain.fundName || selectedExplain.fundCode }}</strong>
          <el-tag :type="levelType(selectedExplain.recommendLevel)" effect="dark">{{ levelText(selectedExplain.recommendLevel) }}</el-tag>
        </div>
        <div class="score-grid">
          <MetricTile label="综合分" :value="numberText(selectedExplain.qualityScore)" :tone="scoreTone(selectedExplain.qualityScore)" />
          <MetricTile label="收益质量" :value="numberText(scoreValue(selectedExplain.scoreBreakdown.returnQualityScore, selectedExplain.scoreBreakdown.returnScore))" tone="rise" />
          <MetricTile label="风险控制" :value="numberText(scoreValue(selectedExplain.scoreBreakdown.drawdownControlScore, selectedExplain.scoreBreakdown.riskScore))" tone="warning" />
          <MetricTile label="稳定性" :value="numberText(scoreValue(selectedExplain.scoreBreakdown.consistencyScore, selectedExplain.scoreBreakdown.stabilityScore))" tone="info" />
          <MetricTile label="可投性" :value="numberText(scoreValue(selectedExplain.scoreBreakdown.investabilityScore, selectedExplain.scoreBreakdown.dataScore))" tone="info" />
        </div>
        <div class="factor-strip">
          <span>基准：{{ selectedExplain.factors.benchmarkCode || '--' }}</span>
          <span>收益回撤比：{{ numberText(Number(selectedExplain.factors.returnDrawdownRatio120d ?? NaN), 2) }}</span>
          <span>多周期一致性：{{ numberText(Number(selectedExplain.factors.returnConsistencyScore ?? NaN)) }}</span>
        </div>
        <div class="explain-block">
          <h3>推荐理由</h3>
          <ul>
            <li v-for="reason in selectedExplain.reasons" :key="reason">{{ reason }}</li>
          </ul>
        </div>
        <div class="explain-block">
          <h3>风险提示</h3>
          <ul>
            <li v-for="risk in selectedExplain.risks" :key="risk">{{ risk }}</li>
          </ul>
        </div>
        <div class="meta-list">
          <span>数据日期：{{ selectedExplain.scoreDate || '--' }}</span>
          <span>模型版本：{{ selectedExplain.modelVersion }}</span>
        </div>
        <DisclaimerBar :text="selectedExplain.disclaimer" />
      </div>
      <EmptyState v-else title="暂无评分解释" description="请选择榜单中的基金查看解释。" />
    </el-drawer>
  </section>
</template>

<style scoped>
.fund-screener-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.workspace-head,
.filter-band,
.validation-section,
.rank-section {
  border: 1px solid var(--line-soft);
  background: var(--surface);
  border-radius: var(--radius);
}

.validation-section {
  min-width: 0;
  padding: 18px;
}

.validation-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.validation-head p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.validation-title {
  justify-content: flex-start;
  margin: 0;
}

.validation-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid rgba(117, 144, 158, 0.16);
  border-radius: 8px;
  background: rgba(9, 22, 29, 0.78);
  color: var(--muted);
  font-size: 12px;
}

.validation-meta strong {
  color: var(--text);
}

.validation-conclusion {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(96, 165, 250, 0.2);
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.1);
  color: #cbd9e2;
  line-height: 1.5;
}

.validation-conclusion strong {
  flex: 0 0 auto;
  color: #dbeafe;
}

.validation-effective {
  border-color: rgba(34, 197, 94, 0.25);
  background: rgba(34, 197, 94, 0.1);
}

.validation-failed {
  border-color: rgba(239, 68, 68, 0.25);
  background: rgba(239, 68, 68, 0.1);
}

.validation-neutral,
.validation-insufficient {
  border-color: rgba(245, 158, 11, 0.25);
  background: rgba(245, 158, 11, 0.09);
}

.validation-table {
  width: 100%;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: #0b171f;
  --el-table-header-text-color: #b6c7d2;
  --el-table-text-color: #d9e6ed;
  --el-table-row-hover-bg-color: rgba(61, 142, 255, 0.1);
  --el-table-border-color: rgba(117, 144, 158, 0.16);
  --el-fill-color-lighter: rgba(19, 35, 45, 0.72);
  border: 1px solid rgba(117, 144, 158, 0.16);
  border-radius: 8px;
  background: #0b151c;
}

.validation-cell {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 5px 0;
}

.validation-return,
.validation-sample {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.validation-return strong {
  color: #7dd3fc;
  font-size: 16px;
}

.validation-return span,
.validation-sample,
.validation-empty {
  color: var(--muted);
  font-size: 11px;
}

.validation-details {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  color: #b7c7d1;
  font-size: 11px;
}

.validation-details span {
  white-space: nowrap;
}

.calibration-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.policy-card,
.advice-card {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 12px 14px;
  border: 1px solid rgba(117, 144, 158, 0.16);
  border-radius: 8px;
  background: rgba(9, 22, 29, 0.78);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.policy-card strong,
.advice-card strong {
  color: var(--text);
  font-size: 13px;
}

.workspace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.workspace-head h2 {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--text);
}

.workspace-head p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.head-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.head-actions :deep(.el-button) {
  min-width: 112px;
  height: 38px;
  border: 0;
  font-weight: 700;
}

.head-actions :deep(.el-button--primary) {
  background: #2563eb;
  color: #f8fafc;
}

.head-actions :deep(.el-button--primary:hover),
.head-actions :deep(.el-button--primary:focus) {
  background: #3b82f6;
}

.head-actions :deep(.el-button--warning) {
  background: #d97706;
  color: #fff7ed;
}

.head-actions :deep(.el-button--warning:hover),
.head-actions :deep(.el-button--warning:focus) {
  background: #f59e0b;
}

.filter-band {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 14px;
  align-items: center;
  padding: 14px 16px;
  background: linear-gradient(180deg, rgba(15, 30, 39, 0.96), rgba(9, 20, 27, 0.98));
}

.filter-band :deep(.el-select) {
  flex: 1 1 210px;
  min-width: 180px;
}

.filter-band :deep(.el-input-number) {
  flex: 0 1 160px;
  width: 160px;
}

.filter-band :deep(.el-input__wrapper),
.filter-band :deep(.el-select__wrapper) {
  min-height: 42px;
  border: 1px solid var(--line);
  box-shadow: none;
  background: #0b171e;
}

.filter-band :deep(.el-input__inner),
.filter-band :deep(.el-select__placeholder),
.filter-band :deep(.el-select__selected-item) {
  color: #dce8ee;
}

.filter-band :deep(.el-input-number__decrease),
.filter-band :deep(.el-input-number__increase) {
  border-color: var(--line);
  background: #13242d;
  color: #9fb1bd;
}

.filter-field {
  display: flex;
  flex: 0 1 200px;
  align-items: center;
  min-width: 190px;
  height: 42px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #0b171e;
}

.filter-field > span {
  flex: 0 0 92px;
  padding: 0 12px;
  color: #9fb1bd;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.2;
  white-space: nowrap;
}

.filter-field :deep(.el-input-number) {
  flex: 1 1 auto;
  width: auto;
  min-width: 0;
}

.filter-field :deep(.el-input__wrapper) {
  border: 0;
  border-left: 1px solid var(--line);
  border-radius: 0;
}

.filter-band :deep(.el-checkbox) {
  flex: 0 0 auto;
  min-width: 112px;
  margin-right: 0;
  color: #9fb1bd;
  white-space: nowrap;
}

.filter-band :deep(.el-checkbox__label) {
  color: #9fb1bd;
  line-height: 1.2;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.rank-section {
  padding: 16px;
  min-width: 0;
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title span {
  font-weight: 700;
  color: var(--text);
}

.section-title small,
.fund-cell span,
.meta-list {
  color: var(--muted);
}

.rank-table {
  width: 100%;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: #0b171f;
  --el-table-header-text-color: #b6c7d2;
  --el-table-text-color: #d9e6ed;
  --el-table-row-hover-bg-color: rgba(61, 142, 255, 0.14);
  --el-table-border-color: rgba(117, 144, 158, 0.16);
  --el-fill-color-lighter: rgba(19, 35, 45, 0.72);
  overflow: hidden;
  border: 1px solid rgba(117, 144, 158, 0.16);
  border-radius: 8px;
  background: #0b151c;
}

.rank-table :deep(.el-table__inner-wrapper::before),
.rank-table :deep(.el-table__border-left-patch) {
  background-color: rgba(117, 144, 158, 0.16);
}

.rank-table :deep(.el-table__header-wrapper th),
.rank-table :deep(.el-table__fixed-header-wrapper th) {
  height: 46px;
  border-bottom: 1px solid rgba(117, 144, 158, 0.18);
  background: #0c1921;
  color: #b8c9d4;
  font-weight: 700;
}

.rank-table :deep(.el-table__row),
.rank-table :deep(.el-table__body tr),
.rank-table :deep(.el-table__fixed-body-wrapper tr) {
  background: #0b151c;
}

.rank-table :deep(.el-table__row--striped td.el-table__cell) {
  background: #0f1b24;
}

.rank-table :deep(td.el-table__cell) {
  border-bottom: 1px solid rgba(117, 144, 158, 0.12);
  background: transparent;
  color: #d7e3ea;
}

.rank-table :deep(.el-table__body tr:hover > td.el-table__cell),
.rank-table :deep(.el-table__body tr.hover-row > td.el-table__cell) {
  background: rgba(59, 130, 246, 0.13);
}

.rank-table :deep(.el-table-fixed-column--left),
.rank-table :deep(.el-table-fixed-column--right),
.rank-table :deep(.el-table__fixed-right-patch) {
  background: #0b151c;
}

.rank-table :deep(.el-table__row--striped .el-table-fixed-column--left),
.rank-table :deep(.el-table__row--striped .el-table-fixed-column--right) {
  background: #0f1b24;
}

.rank-table :deep(.el-table__body tr:hover .el-table-fixed-column--left),
.rank-table :deep(.el-table__body tr:hover .el-table-fixed-column--right) {
  background: rgba(59, 130, 246, 0.16);
}

.rank-table :deep(.el-tag) {
  border: 0;
  font-weight: 700;
}

.rank-table :deep(.el-button.is-circle) {
  border-color: rgba(148, 163, 184, 0.18);
  background: #101d26;
  color: #bcd0dc;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease;
}

.rank-table :deep(.el-button.is-circle:hover) {
  border-color: rgba(96, 165, 250, 0.46);
  background: rgba(37, 99, 235, 0.24);
  color: #eff6ff;
}

.fund-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.fund-cell strong {
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-rise {
  color: var(--green);
}

.score-info {
  color: var(--cyan);
}

.score-warning {
  color: var(--amber);
}

.score-fall {
  color: var(--red);
}

.action-group {
  display: flex;
  gap: 8px;
}

.pager-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

.pager-row :deep(.el-pagination) {
  --el-pagination-bg-color: #0b171e;
  --el-pagination-button-bg-color: #0b171e;
  --el-pagination-hover-color: #60a5fa;
  --el-pagination-text-color: #a8bac5;
  --el-disabled-bg-color: #0b171e;
  --el-disabled-text-color: #526774;
  --el-border-color: rgba(117, 144, 158, 0.18);
}

.pager-row :deep(.el-pager li),
.pager-row :deep(.btn-prev),
.pager-row :deep(.btn-next) {
  border: 1px solid rgba(117, 144, 158, 0.14);
}

.pager-row :deep(.el-pager li.is-active) {
  border-color: rgba(96, 165, 250, 0.58);
  background: #2563eb;
  color: #f8fafc;
}

.explain-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.explain-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.explain-head strong {
  color: var(--text);
  font-size: 18px;
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.factor-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 8px;
}

.factor-strip span {
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid rgba(117, 144, 158, 0.16);
  border-radius: 8px;
  background: rgba(12, 26, 35, 0.88);
  color: #9fb2bf;
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.explain-block {
  border-top: 1px solid var(--line-soft);
  padding-top: 12px;
}

.explain-block h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--text);
}

.explain-block ul {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  color: var(--muted);
  line-height: 1.55;
}

.meta-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
}

:global(.screener-explain-overlay) {
  background: rgba(3, 9, 13, 0.58);
  backdrop-filter: blur(2px);
}

:global(.screener-explain-drawer) {
  --el-drawer-bg-color: #071118;
  --el-bg-color: #071118;
  --el-bg-color-overlay: #071118;
  --el-text-color-primary: #e5eef5;
  --el-text-color-regular: #b9c8d3;
  --el-text-color-secondary: #8fa2af;
  --el-border-color: rgba(117, 144, 158, 0.2);
  --el-border-color-light: rgba(117, 144, 158, 0.14);
  overflow: hidden;
  border-left: 1px solid rgba(96, 165, 250, 0.2);
  background:
    radial-gradient(circle at 16% 0%, rgba(34, 211, 238, 0.13), transparent 34%),
    linear-gradient(180deg, #08151d 0%, #061017 100%);
  box-shadow: -18px 0 48px rgba(0, 0, 0, 0.34);
  color: #d9e7ef;
}

:global(.screener-explain-drawer__header) {
  align-items: center;
  min-height: 58px;
  margin: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(117, 144, 158, 0.16);
  background: rgba(8, 21, 29, 0.94);
  color: #e5eef5;
}

:global(.screener-explain-drawer .el-drawer__title) {
  color: #e5eef5;
  font-size: 16px;
  font-weight: 700;
}

:global(.screener-explain-drawer .el-drawer__close-btn) {
  color: #9fb2bf;
}

:global(.screener-explain-drawer .el-drawer__close-btn:hover) {
  color: #dbeafe;
}

:global(.screener-explain-drawer__body) {
  padding: 18px 20px 22px;
  background:
    linear-gradient(180deg, rgba(13, 27, 37, 0.96), rgba(6, 16, 23, 0.98)),
    #071118;
  color: #d9e7ef;
}

:global(.screener-explain-drawer .explain-head) {
  padding: 12px;
  border: 1px solid rgba(117, 144, 158, 0.16);
  border-radius: 8px;
  background: rgba(15, 29, 38, 0.86);
}

:global(.screener-explain-drawer .explain-head strong),
:global(.screener-explain-drawer .explain-block h3) {
  color: #eef6fb;
}

:global(.screener-explain-drawer .metric-tile) {
  border: 1px solid rgba(117, 144, 158, 0.16);
  background: rgba(12, 26, 35, 0.88);
  box-shadow: none;
}

:global(.screener-explain-drawer .metric-label),
:global(.screener-explain-drawer .meta-list),
:global(.screener-explain-drawer .explain-block ul) {
  color: #9fb2bf;
}

:global(.screener-explain-drawer .metric-value) {
  color: #eef6fb;
}

:global(.screener-explain-drawer .explain-block) {
  border-top-color: rgba(117, 144, 158, 0.16);
}

:global(.screener-explain-drawer .disclaimer-bar) {
  border-color: rgba(245, 158, 11, 0.22);
  background: rgba(245, 158, 11, 0.11);
  color: #f6c56d;
}

@media (max-width: 1180px) {
  .filter-band {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .workspace-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .head-actions,
  .head-actions :deep(.el-button) {
    width: 100%;
  }

  .filter-band,
  .metric-row,
  .calibration-grid {
    grid-template-columns: 1fr;
  }

  .validation-head,
  .validation-conclusion {
    align-items: stretch;
    flex-direction: column;
  }

  .factor-strip {
    grid-template-columns: 1fr;
  }
}
</style>
