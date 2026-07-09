<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { quantApi } from '@/api/quant'
import type { FundHolding, FundSearchResult, TradeRecord } from '@/types/domain'
import { SIMULATED_TRADE_NOTICE } from '@/types/domain'
import ActionTag from '@/components/common/ActionTag.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import MetricTile from '@/components/common/MetricTile.vue'
import { money } from '@/utils/format'
import { forgetRecentTrade, mergeRecentTrades, rememberRecentTrades } from '@/utils/recentTrades'

type TradeFilter = {
  key: string
  label: string
  tradeTypes?: TradeRecord['tradeType'][]
  status?: TradeRecord['tradeStatus']
}

const filters: TradeFilter[] = [
  { key: 'ALL', label: '全部交易' },
  { key: 'BUY', label: '加仓', tradeTypes: ['BUY'] },
  { key: 'SELL', label: '减仓', tradeTypes: ['SELL'] },
  { key: 'REGULAR_INVEST', label: '定投', tradeTypes: ['REGULAR_INVEST'] },
  { key: 'CONVERT', label: '转换', tradeTypes: ['CONVERT_IN', 'CONVERT_OUT'] },
  { key: 'PROCESSING', label: '进行中', status: 'PROCESSING' },
  { key: 'COMPLETED', label: '已完成', status: 'COMPLETED' }
]

const trades = ref<TradeRecord[]>([])
const holdings = ref<FundHolding[]>([])
const loading = ref(true)
const filter = ref('ALL')
const dialogOpen = ref(false)
const saving = ref(false)
const settling = ref(false)
const compensatingRegularInvest = ref(false)
const deletingTradeId = ref<number | null>(null)
const convertDialogOpen = ref(false)
const convertSaving = ref(false)
const convertInAmountTouched = ref(false)
const convertSearching = ref(false)
const convertSearchKeyword = ref('')
const convertSearchResults = ref<FundSearchResult[]>([])
const tradeForm = ref({
  holdingId: undefined as number | undefined,
  fundCode: '',
  fundName: '',
  tradeAmount: 0,
  tradeShare: 0,
  tradeNav: 1,
  tradeFee: 0,
  tradeType: 'BUY' as TradeRecord['tradeType'],
  tradeStatus: 'PROCESSING' as TradeRecord['tradeStatus'],
  remark: SIMULATED_TRADE_NOTICE
})
const convertForm = ref({
  outHoldingId: undefined as number | undefined,
  outTradeAmount: 0,
  outTradeShare: 0,
  outTradeNav: 1,
  outTradeFee: 0,
  inHoldingId: undefined as number | undefined,
  inFundCode: '',
  inFundName: '',
  inTradeAmount: 0,
  inTradeShare: 0,
  inTradeNav: 1,
  inTradeFee: 0,
  tradeStatus: 'PROCESSING' as TradeRecord['tradeStatus'],
  remark: SIMULATED_TRADE_NOTICE
})

onMounted(() => {
  void loadData()
  window.addEventListener('focus', handleWindowFocus)
})
onBeforeUnmount(() => {
  window.removeEventListener('focus', handleWindowFocus)
})

function handleWindowFocus() {
  void loadData(false)
}

async function loadData(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const [tradeList, holdingList] = await Promise.all([quantApi.trades(), quantApi.holdings()])
    trades.value = mergeRecentTrades(tradeList)
    holdings.value = holdingList
  } finally {
    if (showLoading) loading.value = false
  }
}

const activeFilter = computed(() => filters.find((item) => item.key === filter.value) || filters[0])
const filtered = computed(() => {
  const current = activeFilter.value
  return trades.value.filter((item) => {
    if (current.status) return item.tradeStatus === current.status
    if (current.tradeTypes?.length) return current.tradeTypes.includes(item.tradeType)
    return true
  })
})

const totalAmount = computed(() => filtered.value.reduce((sum, item) => sum + item.tradeAmount, 0))
const processingCount = computed(() => trades.value.filter((item) => item.tradeStatus === 'PROCESSING').length)
const completedCount = computed(() => trades.value.filter((item) => item.tradeStatus === 'COMPLETED').length)
const selectedOutHolding = computed(() => holdings.value.find((item) => item.id === convertForm.value.outHoldingId))
const estimatedOutShare = computed(() => {
  const explicitShare = Number(convertForm.value.outTradeShare)
  if (explicitShare > 0) return explicitShare
  const outNav = Number(convertForm.value.outTradeNav) || 1
  return Number(convertForm.value.outTradeAmount) / Math.max(outNav, 0.0001)
})
const convertOutOverLimit = computed(() => {
  const holding = selectedOutHolding.value
  if (!holding) return false
  return Number(convertForm.value.outTradeAmount) > holding.holdingAmount || estimatedOutShare.value > holding.holdingShare
})

function actionForTrade(tradeType: TradeRecord['tradeType']) {
  return tradeType === 'SELL' || tradeType === 'CONVERT_OUT' ? 'SELL' : 'BUY'
}

function tradeTypeLabel(tradeType: TradeRecord['tradeType']) {
  return {
    BUY: '加仓',
    SELL: '减仓',
    REGULAR_INVEST: '定投',
    CONVERT_IN: '转换转入',
    CONVERT_OUT: '转换转出'
  }[tradeType]
}

function statusLabel(status: TradeRecord['tradeStatus']) {
  return {
    PROCESSING: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    FAILED: '失败'
  }[status]
}

function canDeleteTrade(item: TradeRecord) {
  return item.tradeStatus === 'PROCESSING'
}

async function deleteTrade(item: TradeRecord) {
  if (!canDeleteTrade(item)) {
    ElMessage.warning('已正式执行的交易不能删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除 ${item.fundName} 的待执行交易记录吗？删除后不会影响当前持仓。`,
      '删除待执行交易',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  deletingTradeId.value = item.id
  try {
    await quantApi.deleteTrade(item.id)
    forgetRecentTrade(item.id)
    trades.value = trades.value.filter((record) => record.id !== item.id)
    ElMessage.success('待执行交易已删除')
  } finally {
    deletingTradeId.value = null
  }
}

function nullableMoney(value: number | null | undefined, digits = 2) {
  return value === null || value === undefined ? '--' : money(value, digits)
}

function nullableNav(value: number | null | undefined) {
  return value === null || value === undefined ? '--' : value.toFixed(4)
}

function localDateTime() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function displayDateTime(value?: string | null) {
  if (!value) return '--'
  const normalized = value.replace('T', ' ').replace(/\.\d+$/, '')
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(normalized)) {
    return `${normalized}:00`
  }
  return normalized
}

function simulatedRemark(value?: string) {
  const remark = value?.trim()
  if (!remark) return SIMULATED_TRADE_NOTICE
  return remark.includes(SIMULATED_TRADE_NOTICE) ? remark : `${remark}，${SIMULATED_TRADE_NOTICE}`
}

function resetTradeForm() {
  const first = holdings.value[0]
  tradeForm.value = {
    holdingId: first?.id,
    fundCode: first?.fundCode || '',
    fundName: first?.fundName || '',
    tradeAmount: 0,
    tradeShare: 0,
    tradeNav: first?.currentEstimateNav || 1,
    tradeFee: 0,
    tradeType: 'BUY',
    tradeStatus: 'PROCESSING',
    remark: SIMULATED_TRADE_NOTICE
  }
}

function openDialog() {
  resetTradeForm()
  dialogOpen.value = true
}

function applyHolding() {
  const holding = holdings.value.find((item) => item.id === tradeForm.value.holdingId)
  if (!holding) return
  tradeForm.value.fundCode = holding.fundCode
  tradeForm.value.fundName = holding.fundName
  tradeForm.value.tradeNav = holding.currentEstimateNav || holding.latestOfficialNav || 1
}

async function saveTrade() {
  if (!tradeForm.value.fundCode || tradeForm.value.tradeAmount <= 0) {
    ElMessage.warning('请选择持仓并填写有效的模拟交易金额')
    return
  }
  saving.value = true
  try {
    const holding = holdings.value.find((item) => item.id === tradeForm.value.holdingId)
    const nav = Number(tradeForm.value.tradeNav) || 1
    const isProcessing = tradeForm.value.tradeStatus === 'PROCESSING'
    const trade = await quantApi.createTrade({
      accountId: holding?.accountId || 1,
      holdingId: tradeForm.value.holdingId,
      fundCode: tradeForm.value.fundCode,
      fundName: tradeForm.value.fundName,
      tradeType: tradeForm.value.tradeType,
      tradeStatus: tradeForm.value.tradeStatus,
      tradeAmount: Number(tradeForm.value.tradeAmount),
      tradeShare: isProcessing ? undefined : Number(tradeForm.value.tradeShare) || Math.round(Number(tradeForm.value.tradeAmount) / Math.max(nav, 0.0001)),
      tradeNav: isProcessing ? undefined : nav,
      tradeFee: Number(tradeForm.value.tradeFee),
      tradeTime: localDateTime(),
      remark: simulatedRemark(tradeForm.value.remark)
    })
    rememberRecentTrades(trade)
    trades.value = [trade, ...trades.value.filter((item) => item.id !== trade.id)]
    dialogOpen.value = false
    ElMessage.success('交易记录已保存，待确认交易会在入账日自动结算')
  } finally {
    saving.value = false
  }
}

function resetConvertForm() {
  const first = holdings.value[0]
  convertInAmountTouched.value = false
  convertForm.value = {
    outHoldingId: first?.id,
    outTradeAmount: 0,
    outTradeShare: 0,
    outTradeNav: first?.currentEstimateNav || first?.latestOfficialNav || 1,
    outTradeFee: 0,
    inHoldingId: undefined,
    inFundCode: '',
    inFundName: '',
    inTradeAmount: 0,
    inTradeShare: 0,
    inTradeNav: 1,
    inTradeFee: 0,
    tradeStatus: 'PROCESSING',
    remark: SIMULATED_TRADE_NOTICE
  }
}

function openConvertDialog() {
  resetConvertForm()
  convertSearchKeyword.value = ''
  convertSearchResults.value = []
  convertDialogOpen.value = true
}

function applyOutHolding() {
  const holding = holdings.value.find((item) => item.id === convertForm.value.outHoldingId)
  if (!holding) return
  convertForm.value.outTradeNav = holding.currentEstimateNav || holding.latestOfficialNav || 1
  syncConvertInAmountFromOut()
}

function applyInHolding() {
  const holding = holdings.value.find((item) => item.id === convertForm.value.inHoldingId)
  if (!holding) return
  convertForm.value.inFundCode = holding.fundCode
  convertForm.value.inFundName = holding.fundName
  convertForm.value.inTradeNav = holding.currentEstimateNav || holding.latestOfficialNav || 1
}

function syncConvertInAmountFromOut() {
  if (convertInAmountTouched.value) return
  const outAmount = Number(convertForm.value.outTradeAmount) || 0
  const outFee = Number(convertForm.value.outTradeFee) || 0
  const inFee = Number(convertForm.value.inTradeFee) || 0
  convertForm.value.inTradeAmount = Math.max(outAmount - outFee - inFee, 0)
}

function markConvertInAmountTouched() {
  convertInAmountTouched.value = true
}

async function searchConvertFunds() {
  const keyword = convertSearchKeyword.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入转入基金名称或代码')
    return
  }
  convertSearching.value = true
  try {
    convertSearchResults.value = await quantApi.searchFunds(keyword, 'FUZZY')
    if (!convertSearchResults.value.length) {
      ElMessage.info('未搜索到转入基金')
    }
  } finally {
    convertSearching.value = false
  }
}

function chooseConvertFund(result: FundSearchResult) {
  const existing = holdings.value.find((item) => item.fundCode === result.fundCode)
  convertForm.value.inHoldingId = existing?.id
  convertForm.value.inFundCode = result.fundCode
  convertForm.value.inFundName = result.fundName
  convertForm.value.inTradeNav = existing?.latestOfficialNav || existing?.currentEstimateNav || 1
  convertSearchResults.value = []
  convertSearchKeyword.value = `${result.fundCode} ${result.fundName}`
}

async function saveConvertPair() {
  const outHolding = holdings.value.find((item) => item.id === convertForm.value.outHoldingId)
  if (!outHolding || convertForm.value.outTradeAmount <= 0 || !convertForm.value.inFundCode || !convertForm.value.inFundName || convertForm.value.inTradeAmount <= 0) {
    ElMessage.warning('请选择转出持仓并填写有效的转入基金和金额')
    return
  }
  if (convertOutOverLimit.value) {
    ElMessage.warning('转出金额或份额不能超过当前持仓')
    return
  }

  convertSaving.value = true
  try {
    const outNav = Number(convertForm.value.outTradeNav) || 1
    const inNav = Number(convertForm.value.inTradeNav) || 1
    const isProcessing = convertForm.value.tradeStatus === 'PROCESSING'
    const pair = await quantApi.createConvertPair({
      accountId: outHolding.accountId,
      outHoldingId: outHolding.id,
      outTradeAmount: Number(convertForm.value.outTradeAmount),
      outTradeShare: isProcessing ? undefined : Number(convertForm.value.outTradeShare) || Math.round(Number(convertForm.value.outTradeAmount) / Math.max(outNav, 0.0001)),
      outTradeNav: isProcessing ? undefined : outNav,
      outTradeFee: Number(convertForm.value.outTradeFee),
      inHoldingId: convertForm.value.inHoldingId,
      inFundCode: convertForm.value.inFundCode,
      inFundName: convertForm.value.inFundName,
      inTradeAmount: Number(convertForm.value.inTradeAmount),
      inTradeShare: isProcessing ? undefined : Number(convertForm.value.inTradeShare) || Math.round(Number(convertForm.value.inTradeAmount) / Math.max(inNav, 0.0001)),
      inTradeNav: isProcessing ? undefined : inNav,
      inTradeFee: Number(convertForm.value.inTradeFee),
      tradeStatus: convertForm.value.tradeStatus,
      tradeTime: localDateTime(),
      remark: simulatedRemark(convertForm.value.remark)
    })
    rememberRecentTrades(pair)
    trades.value = [...pair, ...trades.value.filter((item) => !pair.some((trade) => trade.id === item.id))]
    filter.value = 'CONVERT'
    convertDialogOpen.value = false
    ElMessage.success('成对转换记录已保存')
  } finally {
    convertSaving.value = false
  }
}

async function settleDueTrades() {
  settling.value = true
  try {
    trades.value = await quantApi.settleDueTrades()
    holdings.value = await quantApi.holdings()
    ElMessage.success('已尝试结算到期交易')
  } finally {
    settling.value = false
  }
}

async function compensateDueRegularInvestTrades() {
  compensatingRegularInvest.value = true
  try {
    const result = await quantApi.compensateDueRegularInvestTrades()
    const [tradeList, holdingList] = await Promise.all([quantApi.trades(), quantApi.holdings()])
    trades.value = mergeRecentTrades(tradeList)
    holdings.value = holdingList
    filter.value = 'REGULAR_INVEST'
    if (result.failureCount > 0) {
      ElMessage.warning(`已补跑 ${result.successCount} 笔定投，${result.failureCount} 笔失败：${result.errorSummary || '请查看任务日志'}`)
    } else if (result.successCount > 0) {
      ElMessage.success(`已补跑 ${result.successCount} 笔到期定投`)
    } else {
      ElMessage.info('没有需要补跑的到期定投')
    }
  } finally {
    compensatingRegularInvest.value = false
  }
}
</script>

<template>
  <div class="screen-grid">
    <DisclaimerBar simulated />

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">交易记录</h2>
        <div class="toolbar-row">
          <div class="segmented trade-filter">
            <button v-for="item in filters" :key="item.key" :class="{ active: filter === item.key }" @click="filter = item.key">{{ item.label }}</button>
          </div>
          <button class="ghost-button" :disabled="settling" title="手动触发到期交易结算，用于补偿错过自动任务的情况" @click="settleDueTrades">
            {{ settling ? '补偿中' : '补偿结算' }}
          </button>
          <button class="ghost-button" :disabled="compensatingRegularInvest" title="手动生成今天到期但错过自动任务的定投流水" @click="compensateDueRegularInvestTrades">
            {{ compensatingRegularInvest ? '补跑中' : '补跑定投' }}
          </button>
        </div>
      </div>
      <div class="panel-body">
        <LoadingState v-if="loading" text="正在加载模拟交易记录" />
        <template v-else>
          <div class="metric-row trade-summary">
            <MetricTile label="当前筛选金额" :value="money(totalAmount)" />
            <MetricTile label="筛选记录数" :value="`${filtered.length} 笔`" />
            <MetricTile label="进行中交易" :value="`${processingCount} 笔`" tone="warning" />
            <MetricTile label="已完成交易" :value="`${completedCount} 笔`" tone="info" />
          </div>

          <table v-if="filtered.length" class="terminal-table">
            <thead>
              <tr><th>时间</th><th>基金</th><th>类型</th><th>状态</th><th>金额</th><th>份额</th><th>净值</th><th>手续费</th><th>备注</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in filtered" :key="item.id">
                <td>{{ displayDateTime(item.tradeTime) }}</td>
                <td>{{ item.fundCode }} · {{ item.fundName }}</td>
                <td><ActionTag :action="actionForTrade(item.tradeType)" :text="tradeTypeLabel(item.tradeType)" /></td>
                <td><span class="status-pill" :class="item.tradeStatus.toLowerCase()">{{ statusLabel(item.tradeStatus) }}</span></td>
                <td>{{ money(item.tradeAmount) }}</td>
                <td>{{ nullableMoney(item.tradeShare, 0) }}</td>
                <td>{{ nullableNav(item.tradeNav) }}</td>
                <td>{{ money(item.tradeFee) }}</td>
                <td>{{ item.remark }}</td>
                <td>
                  <button
                    v-if="canDeleteTrade(item)"
                    class="ghost-button table-button danger-button"
                    :disabled="deletingTradeId === item.id"
                    @click="deleteTrade(item)"
                  >
                    {{ deletingTradeId === item.id ? '删除中' : '删除' }}
                  </button>
                  <span v-else class="muted-text">--</span>
                </td>
              </tr>
            </tbody>
          </table>
          <EmptyState v-else title="暂无交易记录" description="当前筛选条件下还没有模拟交易记录。" />
        </template>
      </div>
    </section>

    <el-dialog v-model="dialogOpen" title="添加模拟交易记录" width="620px">
      <DisclaimerBar simulated />
      <div class="modal-grid compact-form">
        <label>选择持仓
          <select v-model.number="tradeForm.holdingId" class="form-control" @change="applyHolding">
            <option v-for="item in holdings" :key="item.id" :value="item.id">{{ item.fundCode }} · {{ item.fundName }}</option>
          </select>
        </label>
        <label>基金代码<input v-model="tradeForm.fundCode" class="form-control" /></label>
        <label>基金名称<input v-model="tradeForm.fundName" class="form-control" /></label>
        <label>交易类型
          <select v-model="tradeForm.tradeType" class="form-control">
            <option value="BUY">加仓</option>
            <option value="SELL">减仓</option>
            <option value="REGULAR_INVEST">定投</option>
            <option value="CONVERT_IN">转换转入</option>
            <option value="CONVERT_OUT">转换转出</option>
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
        <label>交易份额<input v-model.number="tradeForm.tradeShare" class="form-control" type="number" min="0" /></label>
        <label>参考净值<input v-model.number="tradeForm.tradeNav" class="form-control" type="number" min="0" step="0.0001" /></label>
        <label>手续费<input v-model.number="tradeForm.tradeFee" class="form-control" type="number" min="0" /></label>
        <label class="full-span">备注<textarea v-model="tradeForm.remark" class="form-control text-area"></textarea></label>
      </div>
      <template #footer>
        <button class="primary-button" :disabled="saving" @click="saveTrade">{{ saving ? '保存中' : '保存模拟记录' }}</button>
      </template>
    </el-dialog>

    <el-dialog v-model="convertDialogOpen" title="成对转换" width="720px">
      <DisclaimerBar simulated />
      <div class="modal-grid compact-form">
        <label>转出持仓
          <select v-model.number="convertForm.outHoldingId" class="form-control" @change="applyOutHolding">
            <option v-for="item in holdings" :key="item.id" :value="item.id">{{ item.fundCode }} · {{ item.fundName }}</option>
          </select>
        </label>
        <label>转出金额<input v-model.number="convertForm.outTradeAmount" class="form-control" type="number" min="0" @input="syncConvertInAmountFromOut" /></label>
        <label>转出份额<input v-model.number="convertForm.outTradeShare" class="form-control" type="number" min="0" /></label>
        <label>转出净值<input v-model.number="convertForm.outTradeNav" class="form-control" type="number" min="0" step="0.0001" /></label>
        <label>转出手续费<input v-model.number="convertForm.outTradeFee" class="form-control" type="number" min="0" @input="syncConvertInAmountFromOut" /></label>
        <p v-if="selectedOutHolding" class="form-hint full-span">当前可转出：{{ money(selectedOutHolding.holdingAmount) }} / {{ money(selectedOutHolding.holdingShare, 0) }} 份</p>
        <p v-if="convertOutOverLimit" class="form-warning full-span">转出金额或份额不能超过当前持仓。</p>
        <label>转入持仓
          <select v-model.number="convertForm.inHoldingId" class="form-control" @change="applyInHolding">
            <option :value="undefined">新基金</option>
            <option v-for="item in holdings" :key="item.id" :value="item.id">{{ item.fundCode }} · {{ item.fundName }}</option>
          </select>
        </label>
        <div class="full-span convert-search">
          <label>搜索转入基金
            <input v-model="convertSearchKeyword" class="form-control" placeholder="输入基金名称、代码或拼音" @keydown.enter.prevent="searchConvertFunds" />
          </label>
          <button class="ghost-button" :disabled="convertSearching" @click="searchConvertFunds">{{ convertSearching ? '搜索中' : '搜索' }}</button>
        </div>
        <div v-if="convertSearchResults.length" class="full-span search-results">
          <button v-for="result in convertSearchResults" :key="result.fundCode" class="search-result-row" @click="chooseConvertFund(result)">
            <span>{{ result.fundCode }} · {{ result.fundName }}</span>
            <small>{{ holdings.some((item) => item.fundCode === result.fundCode) ? '已在持仓，直接转入' : '新基金，结算时创建持仓' }}</small>
          </button>
        </div>
        <label>转入基金代码<input v-model="convertForm.inFundCode" class="form-control" /></label>
        <label>转入基金名称<input v-model="convertForm.inFundName" class="form-control" /></label>
        <label>转入金额<input v-model.number="convertForm.inTradeAmount" class="form-control" type="number" min="0" @input="markConvertInAmountTouched" /></label>
        <label>转入份额<input v-model.number="convertForm.inTradeShare" class="form-control" type="number" min="0" /></label>
        <label>转入净值<input v-model.number="convertForm.inTradeNav" class="form-control" type="number" min="0" step="0.0001" /></label>
        <label>转入手续费<input v-model.number="convertForm.inTradeFee" class="form-control" type="number" min="0" @input="syncConvertInAmountFromOut" /></label>
        <label>交易状态
          <select v-model="convertForm.tradeStatus" class="form-control">
            <option value="PROCESSING">进行中</option>
            <option value="COMPLETED">已完成</option>
          </select>
        </label>
        <label class="full-span">备注<textarea v-model="convertForm.remark" class="form-control text-area"></textarea></label>
      </div>
      <template #footer>
        <button class="primary-button" :disabled="convertSaving" @click="saveConvertPair">{{ convertSaving ? '保存中' : '保存成对转换' }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.convert-search {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: end;
}

.search-results {
  display: grid;
  max-height: 180px;
  overflow: auto;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
}

.search-result-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  color: var(--text);
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--line-soft);
}

.search-result-row:last-child {
  border-bottom: 0;
}

.search-result-row:hover {
  background: var(--surface-2);
}

.search-result-row small {
  color: var(--muted);
  white-space: nowrap;
}
</style>
