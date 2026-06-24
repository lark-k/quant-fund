<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { quantApi } from '@/api/quant'
import ActionTag from '@/components/common/ActionTag.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import MetricTile from '@/components/common/MetricTile.vue'
import { SIMULATED_TRADE_NOTICE, type FundHolding, type HoldingUpdateRequest, type TradeRecord } from '@/types/domain'
import { metricTone, money, percent, signed, toneClass } from '@/utils/format'

type SyncAction = 'BUY' | 'SELL' | 'REGULAR_INVEST' | 'CONVERT_OUT'
type EditMode = 'AMOUNT_PROFIT' | 'SHARE_COST'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const deleting = ref(false)
const recalculating = ref(false)
const tradeSaving = ref(false)
const dialogOpen = ref(false)
const holdings = ref<FundHolding[]>([])
const selectedHoldingId = ref<number>()
const editMode = ref<EditMode>('AMOUNT_PROFIT')
const activeHolding = computed(() => holdings.value.find((item) => item.id === selectedHoldingId.value))

const form = ref<HoldingUpdateRequest>({
  accountId: 1,
  fundCode: '',
  fundName: '',
  fundType: 'ACTIVE_EQUITY',
  activeFund: true,
  holdingAmount: 0,
  holdingShare: 0,
  holdingCost: 0,
  holdingProfit: 0,
  sourcePlatform: '',
  regularInvestment: false,
  coreHolding: false,
  watchFocus: false
})

const tradeForm = ref({
  tradeType: 'BUY' as TradeRecord['tradeType'],
  tradeStatus: 'PROCESSING' as TradeRecord['tradeStatus'],
  tradeAmount: 0,
  tradeNav: 1,
  tradeFee: 0,
  remark: SIMULATED_TRADE_NOTICE
})

const operationCards = [
  { action: 'BUY', title: '同步加仓', description: '记录你在原平台完成的追加买入' },
  { action: 'SELL', title: '同步减仓', description: '记录你在原平台完成的赎回或卖出' },
  { action: 'REGULAR_INVEST', title: '同步定投', description: '记录定投扣款后的持仓变化' },
  { action: 'CONVERT_OUT', title: '同步转换', description: '记录基金转换转出，转入可在交易页补记' }
] satisfies Array<{ action: SyncAction, title: string, description: string }>

onMounted(loadHoldings)

const referenceNav = computed(() => activeHolding.value?.currentEstimateNav || activeHolding.value?.latestOfficialNav || 0)
const previewAmount = computed(() => {
  if (editMode.value === 'SHARE_COST' && referenceNav.value > 0) {
    return Number(form.value.holdingShare || 0) * referenceNav.value
  }
  return Number(form.value.holdingAmount || 0)
})
const previewCost = computed(() => {
  if (editMode.value === 'AMOUNT_PROFIT') {
    return Math.max(previewAmount.value - Number(form.value.holdingProfit || 0), 0)
  }
  return Number(form.value.holdingCost || 0)
})
const previewProfit = computed(() => previewAmount.value - previewCost.value)
const previewProfitRate = computed(() => previewCost.value > 0 ? previewProfit.value / previewCost.value * 100 : 0)

async function loadHoldings() {
  loading.value = true
  try {
    holdings.value = await quantApi.holdings()
    const queryId = Number(route.query.holdingId)
    const queryCode = String(route.query.fundCode || '')
    const initial = holdings.value.find((item) => item.id === queryId)
      || holdings.value.find((item) => item.fundCode === queryCode)
      || holdings.value[0]
    if (initial) selectHolding(initial.id)
  } finally {
    loading.value = false
  }
}

function selectHolding(id: number) {
  selectedHoldingId.value = id
  const holding = holdings.value.find((item) => item.id === id)
  if (!holding) return
  form.value = {
    accountId: holding.accountId,
    fundCode: holding.fundCode,
    fundName: holding.fundName,
    fundType: holding.fundType,
    activeFund: holding.activeFund,
    holdingAmount: Number(holding.holdingAmount.toFixed(2)),
    holdingShare: Number(holding.holdingShare.toFixed(2)),
    holdingCost: Number(holding.holdingCost.toFixed(2)),
    holdingProfit: Number(holding.holdingProfit.toFixed(2)),
    sourcePlatform: holding.sourcePlatform,
    regularInvestment: holding.regularInvestment,
    coreHolding: holding.coreHolding,
    watchFocus: holding.watchFocus
  }
  tradeForm.value.tradeNav = holding.currentEstimateNav || holding.latestOfficialNav || 1
  tradeForm.value.tradeAmount = 0
  tradeForm.value.remark = SIMULATED_TRADE_NOTICE
}

async function saveHolding() {
  if (!activeHolding.value) return
  saving.value = true
  try {
    const amount = Number(form.value.holdingAmount || 0)
    const profit = Number(form.value.holdingProfit || 0)
    const cost = Number(form.value.holdingCost || 0)
    const share = Number(form.value.holdingShare || 0)
    const derivedShare = referenceNav.value > 0 ? amount / referenceNav.value : activeHolding.value.holdingShare
    const saved = await quantApi.updateHolding(activeHolding.value.id, {
      ...form.value,
      fundType: normalizeFundType(form.value.fundType),
      holdingAmount: editMode.value === 'AMOUNT_PROFIT' ? amount : previewAmount.value,
      holdingShare: editMode.value === 'AMOUNT_PROFIT' ? Math.max(derivedShare, 0) : share,
      holdingCost: editMode.value === 'SHARE_COST' ? cost : previewCost.value,
      holdingProfit: editMode.value === 'AMOUNT_PROFIT' ? profit : undefined
    })
    holdings.value = holdings.value.map((item) => item.id === saved.id ? saved : item)
    selectHolding(saved.id)
    router.replace({ path: '/holding-edit', query: { holdingId: saved.id, fundCode: saved.fundCode } })
    ElMessage.success('持仓信息已保存，净值与收益已按真实数据源重算')
  } finally {
    saving.value = false
  }
}

async function deleteCurrentHolding() {
  if (!activeHolding.value) return
  await ElMessageBox.confirm(`确认删除 ${activeHolding.value.fundName} 的持有记录？`, '删除持仓', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  deleting.value = true
  try {
    await quantApi.deleteHolding(activeHolding.value.id)
    holdings.value = holdings.value.filter((item) => item.id !== selectedHoldingId.value)
    ElMessage.success('持仓已删除')
    if (holdings.value[0]) {
      selectHolding(holdings.value[0].id)
    } else {
      selectedHoldingId.value = undefined
      router.push('/holdings')
    }
  } finally {
    deleting.value = false
  }
}

async function recalculateHolding() {
  if (!activeHolding.value) return
  recalculating.value = true
  try {
    const saved = await quantApi.recalculateHolding(activeHolding.value.id)
    holdings.value = holdings.value.map((item) => item.id === saved.id ? saved : item)
    selectHolding(saved.id)
    ElMessage.success('收益已按最新净值重新计算')
  } finally {
    recalculating.value = false
  }
}

function openSyncDialog(action: SyncAction) {
  if (!activeHolding.value) return
  tradeForm.value = {
    tradeType: action,
    tradeStatus: 'PROCESSING',
    tradeAmount: 0,
    tradeNav: activeHolding.value.currentEstimateNav || activeHolding.value.latestOfficialNav || 1,
    tradeFee: 0,
    remark: SIMULATED_TRADE_NOTICE
  }
  dialogOpen.value = true
}

function localDateTime() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function relatedThemeText(theme?: string | null) {
  return theme && theme !== '主动权益' ? theme : '重仓板块待同步'
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

async function saveTrade() {
  const holding = activeHolding.value
  if (!holding || tradeForm.value.tradeAmount <= 0) {
    ElMessage.warning('请填写有效的模拟交易金额')
    return
  }
  tradeSaving.value = true
  try {
    await quantApi.createTrade({
      accountId: holding.accountId,
      holdingId: holding.id,
      fundCode: holding.fundCode,
      fundName: holding.fundName,
      tradeType: tradeForm.value.tradeType,
      tradeStatus: tradeForm.value.tradeStatus,
      tradeAmount: Number(tradeForm.value.tradeAmount),
      tradeShare: Math.round(Number(tradeForm.value.tradeAmount) / Math.max(Number(tradeForm.value.tradeNav), 0.0001)),
      tradeNav: Number(tradeForm.value.tradeNav),
      tradeFee: Number(tradeForm.value.tradeFee),
      tradeTime: localDateTime(),
      remark: tradeForm.value.remark || SIMULATED_TRADE_NOTICE
    })
    dialogOpen.value = false
    ElMessage.success('模拟同步记录已保存')
  } finally {
    tradeSaving.value = false
  }
}
</script>

<template>
  <LoadingState v-if="loading" text="正在加载持仓编辑台" />
  <EmptyState v-else-if="!activeHolding" title="未找到持仓记录" description="请先在持仓列表中选择或搜索加入一只基金。" />
  <div v-else class="screen-grid">
    <DisclaimerBar simulated />

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">持仓编辑 / 同步操作</h2>
        <div class="toolbar-row">
          <select v-model.number="selectedHoldingId" class="form-control" @change="selectHolding(selectedHoldingId!)">
            <option v-for="item in holdings" :key="item.id" :value="item.id">{{ item.fundCode }} · {{ item.fundName }}</option>
          </select>
          <button class="ghost-button" :disabled="recalculating" @click="recalculateHolding">{{ recalculating ? '重算中' : '刷新净值并重算' }}</button>
          <button class="ghost-button danger-button" :disabled="deleting" @click="deleteCurrentHolding">{{ deleting ? '删除中' : '删除持仓' }}</button>
          <button class="primary-button" :disabled="saving" @click="saveHolding">{{ saving ? '保存中' : '保存持仓' }}</button>
        </div>
      </div>
      <div class="panel-body">
        <div class="detail-hero">
          <div>
            <div class="fund-code">{{ activeHolding.fundName }} · {{ activeHolding.fundCode }}</div>
            <p>净值来自基金数据源，用户只填写真实持有信息；系统自动计算金额、收益、占比和成本。</p>
          </div>
          <div class="fund-badges">
            <span>{{ relatedThemeText(activeHolding.relatedThemeName) }}</span>
            <span>估值 {{ activeHolding.currentEstimateNav ? activeHolding.currentEstimateNav.toFixed(4) : '--' }}</span>
            <span>正式净值 {{ activeHolding.latestOfficialNav ? activeHolding.latestOfficialNav.toFixed(4) : '--' }}</span>
          </div>
        </div>

        <div class="metric-row">
          <MetricTile label="持有金额" :value="money(activeHolding.holdingAmount)" />
          <MetricTile label="持有收益" :value="signed(activeHolding.holdingProfit)" :delta="percent(activeHolding.holdingProfitRate)" :tone="metricTone(activeHolding.holdingProfit)" />
          <MetricTile label="当日收益" :value="signed(activeHolding.dailyProfit)" :delta="percent(activeHolding.currentEstimateGrowthRate || 0)" :tone="metricTone(activeHolding.dailyProfit)" />
          <MetricTile label="昨日收益" :value="signed(activeHolding.yesterdayProfit || 0)" :tone="metricTone(activeHolding.yesterdayProfit || 0)" />
          <MetricTile label="持仓占比" :value="percent(activeHolding.positionRate || 0)" />
          <MetricTile label="持有天数" :value="`${activeHolding.holdingDays} 天`" />
        </div>
      </div>
    </section>

    <div class="insight-grid two">
      <section class="panel">
        <div class="panel-header">
          <h2 class="panel-title">持仓字段</h2>
          <div class="segmented">
            <button :class="{ active: editMode === 'AMOUNT_PROFIT' }" @click="editMode = 'AMOUNT_PROFIT'">金额+收益</button>
            <button :class="{ active: editMode === 'SHARE_COST' }" @click="editMode = 'SHARE_COST'">份额+成本</button>
          </div>
        </div>
        <div class="panel-body config-grid compact-form">
          <template v-if="editMode === 'AMOUNT_PROFIT'">
            <label>持有金额<input v-model.number="form.holdingAmount" class="form-control" type="number" min="0" /></label>
            <label>持有收益<input v-model.number="form.holdingProfit" class="form-control" type="number" /></label>
          </template>
          <template v-else>
            <label>持有份额<input v-model.number="form.holdingShare" class="form-control" type="number" min="0" /></label>
            <label>持有成本<input v-model.number="form.holdingCost" class="form-control" type="number" min="0" /></label>
          </template>
          <label>原平台<input v-model="form.sourcePlatform" class="form-control" /></label>
          <label class="switch-row"><input v-model="form.regularInvestment" type="checkbox" /> 定投计划</label>
          <label class="switch-row"><input v-model="form.coreHolding" type="checkbox" /> 核心持仓</label>
          <label class="switch-row"><input v-model="form.watchFocus" type="checkbox" /> 重点关注</label>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header"><h2 class="panel-title">自动测算</h2></div>
        <div class="panel-body">
          <table class="terminal-table">
            <tbody>
              <tr><td>参考净值</td><td>{{ referenceNav ? referenceNav.toFixed(4) : '--' }}</td><td>来自盘中估值或最新正式净值</td></tr>
              <tr><td>测算金额</td><td>{{ money(previewAmount) }}</td><td>份额模式下由份额 × 净值计算</td></tr>
              <tr><td>测算成本</td><td>{{ money(previewCost) }}</td><td>金额收益模式下由金额 - 收益计算</td></tr>
              <tr><td>测算收益率</td><td :class="toneClass(previewProfitRate)">{{ percent(previewProfitRate) }}</td><td>保存后以后端真实净值重算为准</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-header"><h2 class="panel-title">同步操作</h2></div>
      <div class="panel-body operation-grid">
        <button v-for="item in operationCards" :key="item.action" class="operation-card" @click="openSyncDialog(item.action)">
          <ActionTag :action="item.action === 'SELL' || item.action === 'CONVERT_OUT' ? 'SELL' : 'BUY'" :text="item.title" />
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
        </button>
      </div>
    </section>

    <el-dialog v-model="dialogOpen" title="同步模拟交易记录" width="560px">
      <DisclaimerBar simulated />
      <div class="modal-grid compact-form">
        <label>交易类型
          <select v-model="tradeForm.tradeType" class="form-control">
            <option value="BUY">加仓</option>
            <option value="SELL">减仓</option>
            <option value="REGULAR_INVEST">定投</option>
            <option value="CONVERT_OUT">转换转出</option>
            <option value="CONVERT_IN">转换转入</option>
          </select>
        </label>
        <label>交易状态
          <select v-model="tradeForm.tradeStatus" class="form-control">
            <option value="PROCESSING">进行中</option>
            <option value="COMPLETED">已完成</option>
            <option value="CANCELLED">已取消</option>
            <option value="FAILED">失败</option>
          </select>
        </label>
        <label>交易金额<input v-model.number="tradeForm.tradeAmount" class="form-control" type="number" min="0" /></label>
        <label>成交/参考净值<input v-model.number="tradeForm.tradeNav" class="form-control" type="number" min="0" step="0.0001" /></label>
        <label>手续费<input v-model.number="tradeForm.tradeFee" class="form-control" type="number" min="0" /></label>
        <label>备注<textarea v-model="tradeForm.remark" class="form-control text-area" /></label>
      </div>
      <template #footer>
        <button class="primary-button" :disabled="tradeSaving" @click="saveTrade">{{ tradeSaving ? '保存中' : '保存模拟记录' }}</button>
      </template>
    </el-dialog>
  </div>
</template>
