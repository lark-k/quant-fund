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
import { SIMULATED_TRADE_NOTICE, type ClearHoldingRequest, type FundHolding, type FundNavPoint, type HoldingUpdateRequest, type InvestmentPlan, type InvestmentPlanRequest, type TradeRecord } from '@/types/domain'
import { metricTone, money, percent, signed, toneClass } from '@/utils/format'
import { rememberRecentTrades } from '@/utils/recentTrades'

type SyncAction = 'BUY' | 'SELL' | 'REGULAR_INVEST' | 'CONVERT_OUT'
type EditMode = 'AMOUNT_PROFIT' | 'SHARE_COST'
type TradeCutoff = 'BEFORE_15' | 'AFTER_15'
type PlanFrequency = 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY'
type WheelOption<T> = { label: string, value: T }
type WheelItem<T> = WheelOption<T> & { active: boolean, disabled: boolean }

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const deleting = ref(false)
const clearing = ref(false)
const recalculating = ref(false)
const tradeSaving = ref(false)
const settlingTrades = ref(false)
const syncBuyNavLoading = ref(false)
const dialogOpen = ref(false)
const planDialogOpen = ref(false)
const planSaving = ref(false)
const planLoading = ref(false)
const planEditMode = ref(false)
const planActionOpen = ref(false)
const planScheduleOpen = ref(false)
const planScheduleDraftFrequency = ref<PlanFrequency>('WEEKLY')
const planScheduleDraftWeekday = ref(1)
const planScheduleDraftMonthDay = ref(1)
const selectedPlan = ref<InvestmentPlan | null>(null)
const clearDialogOpen = ref(false)
const holdings = ref<FundHolding[]>([])
const investmentPlans = ref<InvestmentPlan[]>([])
const selectedHoldingId = ref<number>()
const editMode = ref<EditMode>('AMOUNT_PROFIT')
const activeHolding = computed(() => holdings.value.find((item) => item.id === selectedHoldingId.value))
const syncBuyLatestNav = ref<FundNavPoint | null>(null)

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
  tradeShare: undefined as number | undefined,
  tradeNav: 1,
  tradeFee: 0,
  tradeFeeRate: 0,
  tradeDate: todayDate(),
  tradeCutoff: 'BEFORE_15' as TradeCutoff,
  remark: SIMULATED_TRADE_NOTICE
})
const planForm = ref({
  id: undefined as number | undefined,
  amount: 0,
  frequency: 'WEEKLY' as 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY',
  nextExecuteDate: new Date().toISOString().slice(0, 10),
  status: 'ENABLED' as InvestmentPlan['status']
})
const clearForm = ref<ClearHoldingRequest>({
  tradeAmount: 0,
  tradeFee: 0,
  remark: `清仓自动生成的模拟卖出流水，${SIMULATED_TRADE_NOTICE}`
})

const operationCards = [
  { action: 'BUY', title: '同步加仓', description: '记录你在原平台完成的追加买入' },
  { action: 'SELL', title: '同步减仓', description: '记录你在原平台完成的赎回或卖出' },
  { action: 'REGULAR_INVEST', title: '同步定投', description: '设置原平台定投规则，到期后自动生成待确认加仓' },
  { action: 'CONVERT_OUT', title: '同步转换', description: '记录基金转换转出，转入可在交易页补记' }
] satisfies Array<{ action: SyncAction, title: string, description: string }>
const planFrequencyOptions: Array<WheelOption<PlanFrequency>> = [
  { label: '每周', value: 'WEEKLY' },
  { label: '每两周', value: 'BIWEEKLY' },
  { label: '每月', value: 'MONTHLY' },
  { label: '每日交易日', value: 'DAILY' }
]
const planWeekdayOptions = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 }
]
const planMonthDayOptions = Array.from({ length: 28 }, (_, index) => index + 1)

onMounted(loadHoldings)

const referenceNav = computed(() => activeHolding.value?.latestOfficialNav || 0)
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
const previewCostNav = computed(() => {
  if (editMode.value === 'AMOUNT_PROFIT') {
    const factor = 1 + previewProfitRate.value / 100
    return referenceNav.value > 0 && factor > 0 ? referenceNav.value / factor : 0
  }
  const share = Number(form.value.holdingShare || 0)
  return share > 0 ? Number(form.value.holdingCost || 0) / share : 0
})
const previewShare = computed(() => {
  if (editMode.value === 'AMOUNT_PROFIT') {
    return previewCostNav.value > 0 ? previewCost.value / previewCostNav.value : 0
  }
  return Number(form.value.holdingShare || 0)
})
const isNewHoldingDraft = computed(() => {
  const holding = activeHolding.value
  if (!holding) return false
  return holding.holdingAmount <= 0 && holding.holdingShare <= 0 && holding.holdingCost <= 0
})
const shareModeMissingNav = computed(() => editMode.value === 'SHARE_COST' && referenceNav.value <= 0)
const isDecreaseTrade = computed(() => ['SELL', 'CONVERT_OUT'].includes(tradeForm.value.tradeType))
const isBuyTrade = computed(() => tradeForm.value.tradeType === 'BUY')
const isSellTrade = computed(() => tradeForm.value.tradeType === 'SELL')
const estimatedTradeShare = computed(() => {
  const explicitShare = Number(tradeForm.value.tradeShare || 0)
  if (explicitShare > 0) return explicitShare
  return Number(tradeForm.value.tradeAmount || 0) / Math.max(Number(tradeForm.value.tradeNav || 0), 0.0001)
})
const estimatedSellAmount = computed(() => Math.max(Number(tradeForm.value.tradeShare || 0), 0) * Math.max(Number(tradeForm.value.tradeNav || 0), 0))
const estimatedBuyFee = computed(() => {
  const amount = Number(tradeForm.value.tradeAmount || 0)
  const feeRate = Number(tradeForm.value.tradeFeeRate || 0)
  if (!Number.isFinite(amount) || !Number.isFinite(feeRate)) return 0
  return Math.max(Math.round(amount * feeRate) / 100, 0)
})
const tradeOverLimit = computed(() => {
  const holding = activeHolding.value
  if (!holding || !isDecreaseTrade.value) return false
  return Number(tradeForm.value.tradeAmount) > holding.holdingAmount || estimatedTradeShare.value > holding.holdingShare
})
const syncBuyNavValue = computed(() => syncBuyLatestNav.value?.nav ?? activeHolding.value?.latestOfficialNav ?? null)
const syncBuyNavDateText = computed(() => shortDate(syncBuyLatestNav.value?.date || activeHolding.value?.officialNavDate))
const syncBuyGrowthRate = computed(() => syncBuyLatestNav.value?.dailyGrowthRate ?? null)
const tradeCutoffText = computed(() => tradeForm.value.tradeCutoff === 'BEFORE_15' ? '下午3点前' : '下午3点后')
const maxSellShareText = computed(() => (activeHolding.value?.holdingShare || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2
}))
const syncDialogTitle = computed(() => {
  if (isBuyTrade.value) return '同步加仓'
  if (isSellTrade.value) return '同步减仓'
  return '同步模拟交易记录'
})
const syncDialogWidth = computed(() => isBuyTrade.value || isSellTrade.value ? '720px' : '560px')
const activeInvestmentPlans = computed(() => {
  const holding = activeHolding.value
  if (!holding) return []
  return investmentPlans.value.filter((plan) => plan.accountId === holding.accountId && plan.fundCode === holding.fundCode)
})
const investmentPlanSummary = computed(() => {
  const plans = activeInvestmentPlans.value
  const totalAmount = plans.reduce((sum, plan) => sum + Number(plan.amount || 0), 0)
  return {
    totalAmount,
    planCount: plans.length
  }
})
const planScheduleText = computed(() => {
  if (planForm.value.frequency === 'DAILY') return '每日交易日'
  if (planForm.value.frequency === 'MONTHLY') return `每月 ${dayOfMonthFromDate(planForm.value.nextExecuteDate)}日`
  return `${frequencyLabel(planForm.value.frequency)} ${weekdayLabel(weekdayFromDate(planForm.value.nextExecuteDate))}`
})
const planScheduleDraftText = computed(() => {
  if (planScheduleDraftFrequency.value === 'DAILY') return '每日交易日'
  if (planScheduleDraftFrequency.value === 'MONTHLY') return `每月 ${planScheduleDraftMonthDay.value}日`
  return `${frequencyLabel(planScheduleDraftFrequency.value)} ${weekdayLabel(planScheduleDraftWeekday.value)}`
})
const planFrequencyWheelItems = computed(() => wheelItems(planFrequencyOptions, planScheduleDraftFrequency.value))
const planWeekdayWheelItems = computed(() => wheelItems(planWeekdayOptions, planScheduleDraftWeekday.value))
const planMonthDayWheelItems = computed(() => wheelItems(
  planMonthDayOptions.map((day) => ({ label: `${day}日`, value: day })),
  planScheduleDraftMonthDay.value
))

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
  tradeForm.value.tradeShare = undefined
  tradeForm.value.tradeFee = 0
  tradeForm.value.tradeFeeRate = 0
  tradeForm.value.tradeDate = todayDate()
  tradeForm.value.tradeCutoff = 'BEFORE_15'
  tradeForm.value.remark = SIMULATED_TRADE_NOTICE
  syncBuyLatestNav.value = null
}

async function saveHolding() {
  if (!activeHolding.value) return
  if (shareModeMissingNav.value) {
    ElMessage.warning('份额+总成本模式需要先从数据源同步到最新正式净值')
    return
  }
  saving.value = true
  try {
    const amount = Number(form.value.holdingAmount || 0)
    const profit = Number(form.value.holdingProfit || 0)
    const cost = Number(form.value.holdingCost || 0)
    const share = Number(form.value.holdingShare || 0)
    if (editMode.value === 'AMOUNT_PROFIT' && amount > 0 && referenceNav.value <= 0) {
      ElMessage.warning('金额+收益模式需要先同步到最新正式净值，不能使用盘中估值反推持有份额')
      return
    }
    const saved = await quantApi.updateHolding(activeHolding.value.id, {
      ...form.value,
      fundType: normalizeFundType(form.value.fundType),
      holdingAmount: editMode.value === 'AMOUNT_PROFIT' ? amount : previewAmount.value,
      holdingShare: editMode.value === 'AMOUNT_PROFIT' ? 0 : share,
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
  await ElMessageBox.confirm(`确认删除 ${activeHolding.value.fundName} 的持有记录？删除后不再展示该持仓；如只是已经卖完，请使用清仓。`, '删除持仓', {
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

function clearRemark(tradeFee: number) {
  const feeText = tradeFee > 0 ? `，手续费 ${tradeFee.toFixed(2)}` : ''
  return `清仓自动生成的模拟卖出流水${feeText}，${SIMULATED_TRADE_NOTICE}`
}

function openClearDialog() {
  if (!activeHolding.value) return
  clearForm.value = {
    tradeAmount: Number(activeHolding.value.holdingAmount.toFixed(2)),
    tradeFee: 0,
    remark: clearRemark(0)
  }
  clearDialogOpen.value = true
}

function syncClearRemark() {
  clearForm.value.remark = clearRemark(Number(clearForm.value.tradeFee || 0))
}

async function saveClearHolding() {
  if (!activeHolding.value) return
  const tradeAmount = Number(clearForm.value.tradeAmount)
  const tradeFee = Number(clearForm.value.tradeFee || 0)
  if (!Number.isFinite(tradeAmount) || tradeAmount < 0 || !Number.isFinite(tradeFee) || tradeFee < 0) {
    ElMessage.warning('请填写有效的到账金额和手续费')
    return
  }
  const request: ClearHoldingRequest = {
    tradeAmount,
    tradeFee,
    remark: clearForm.value.remark || clearRemark(tradeFee)
  }
  clearing.value = true
  try {
    const saved = await quantApi.clearHolding(activeHolding.value.id, request)
    holdings.value = holdings.value.map((item) => item.id === saved.id ? saved : item)
    selectHolding(saved.id)
    clearDialogOpen.value = false
    ElMessage.success('持仓已清仓，历史记录已保留')
  } finally {
    clearing.value = false
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
  if (action === 'REGULAR_INVEST') {
    planEditMode.value = false
    selectedPlan.value = null
    resetPlanForm()
    planDialogOpen.value = true
    void loadSyncBuyLatestNav(activeHolding.value)
    void loadInvestmentPlans()
    return
  }
  tradeForm.value = {
    tradeType: action,
    tradeStatus: 'PROCESSING',
    tradeAmount: 0,
    tradeShare: undefined,
    tradeNav: activeHolding.value.latestOfficialNav || 1,
    tradeFee: 0,
    tradeFeeRate: 0,
    tradeDate: todayDate(),
    tradeCutoff: 'BEFORE_15',
    remark: SIMULATED_TRADE_NOTICE
  }
  dialogOpen.value = true
  if (action === 'BUY' || action === 'SELL') void loadSyncBuyLatestNav(activeHolding.value)
}

async function loadInvestmentPlans() {
  const holding = activeHolding.value
  if (!holding) return
  planLoading.value = true
  try {
    investmentPlans.value = await quantApi.investmentPlans(holding.accountId)
  } finally {
    planLoading.value = false
  }
}

function resetPlanForm() {
  planForm.value = {
    id: undefined,
    amount: 0,
    frequency: 'BIWEEKLY',
    nextExecuteDate: nextDefaultPlanDate(),
    status: 'ENABLED'
  }
  syncPlanScheduleDraft()
}

function nextDefaultPlanDate() {
  return nextTradingDateOnOrAfter(todayDate())
}

function frequencyLabel(frequency: InvestmentPlan['frequency']) {
  return {
    DAILY: '每日',
    WEEKLY: '每周',
    BIWEEKLY: '每两周',
    EVERY_TWO_WEEKS: '每两周',
    MONTHLY: '每月'
  }[frequency] || '每周'
}

function wheelItems<T>(options: Array<WheelOption<T>>, activeValue: T): Array<WheelItem<T>> {
  const activeIndex = Math.max(options.findIndex((option) => option.value === activeValue), 0)
  return [-2, -1, 0, 1, 2].map((offset) => {
    const option = options[activeIndex + offset]
    return option
      ? { ...option, active: offset === 0, disabled: false }
      : { label: '', value: activeValue, active: false, disabled: true }
  })
}

function weekdayLabel(weekday: number) {
  return planWeekdayOptions.find((item) => item.value === weekday)?.label || '周一'
}

function parseLocalDate(value?: string | null) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return new Date()
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

function formatLocalDate(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function weekdayFromDate(value?: string | null) {
  const weekday = parseLocalDate(value).getDay()
  return weekday === 0 ? 7 : weekday
}

function dayOfMonthFromDate(value?: string | null) {
  return parseLocalDate(value).getDate()
}

function isWeekendDate(date: Date) {
  const weekday = date.getDay()
  return weekday === 0 || weekday === 6
}

function nextTradingDateOnOrAfter(value: string) {
  const date = parseLocalDate(value)
  while (isWeekendDate(date)) {
    date.setDate(date.getDate() + 1)
  }
  return formatLocalDate(date)
}

function nextWeekdayDate(weekday: number) {
  const date = parseLocalDate(todayDate())
  const current = weekdayFromDate(todayDate())
  const diff = (weekday - current + 7) % 7
  date.setDate(date.getDate() + diff)
  return nextTradingDateOnOrAfter(formatLocalDate(date))
}

function nextMonthDayDate(day: number) {
  const today = parseLocalDate(todayDate())
  const safeDay = Math.min(Math.max(Math.round(day), 1), 28)
  const target = new Date(today.getFullYear(), today.getMonth(), safeDay)
  if (target < today) {
    target.setMonth(target.getMonth() + 1)
  }
  return nextTradingDateOnOrAfter(formatLocalDate(target))
}

function syncPlanScheduleDraft() {
  planScheduleDraftFrequency.value = planForm.value.frequency
  planScheduleDraftWeekday.value = Math.min(weekdayFromDate(planForm.value.nextExecuteDate), 5)
  planScheduleDraftMonthDay.value = Math.min(Math.max(dayOfMonthFromDate(planForm.value.nextExecuteDate), 1), 28)
}

function openPlanSchedulePicker() {
  syncPlanScheduleDraft()
  planScheduleDraftFrequency.value = 'BIWEEKLY'
  planScheduleOpen.value = true
}

function selectPlanDraftFrequency(frequency: PlanFrequency) {
  planScheduleDraftFrequency.value = frequency
}

function wheelDirection(event: WheelEvent) {
  return event.deltaY > 0 || event.deltaX > 0 ? 1 : -1
}

function shiftWheelValue<T>(options: Array<WheelOption<T>>, activeValue: T, direction: number) {
  const activeIndex = Math.max(options.findIndex((option) => option.value === activeValue), 0)
  const nextIndex = Math.min(Math.max(activeIndex + direction, 0), options.length - 1)
  return options[nextIndex].value
}

function onPlanFrequencyWheel(event: WheelEvent) {
  planScheduleDraftFrequency.value = shiftWheelValue(
    planFrequencyOptions,
    planScheduleDraftFrequency.value,
    wheelDirection(event)
  )
}

function onPlanWeekdayWheel(event: WheelEvent) {
  planScheduleDraftWeekday.value = shiftWheelValue(
    planWeekdayOptions,
    planScheduleDraftWeekday.value,
    wheelDirection(event)
  )
}

function onPlanMonthDayWheel(event: WheelEvent) {
  planScheduleDraftMonthDay.value = shiftWheelValue(
    planMonthDayOptions.map((day) => ({ label: `${day}日`, value: day })),
    planScheduleDraftMonthDay.value,
    wheelDirection(event)
  )
}

function confirmPlanSchedule() {
  planForm.value.frequency = planScheduleDraftFrequency.value
  if (planScheduleDraftFrequency.value === 'DAILY') {
    planForm.value.nextExecuteDate = nextTradingDateOnOrAfter(todayDate())
  } else if (planScheduleDraftFrequency.value === 'MONTHLY') {
    planForm.value.nextExecuteDate = nextMonthDayDate(planScheduleDraftMonthDay.value)
  } else {
    planForm.value.nextExecuteDate = nextWeekdayDate(planScheduleDraftWeekday.value)
  }
  planScheduleOpen.value = false
}

function planStatusLabel(status: InvestmentPlan['status']) {
  return status === 'PAUSED' ? '暂停' : '执行中'
}

function openPlanAction(plan: InvestmentPlan) {
  selectedPlan.value = plan
  planActionOpen.value = true
}

function openCreatePlanForm() {
  selectedPlan.value = null
  resetPlanForm()
  planEditMode.value = true
}

function openEditPlanForm(plan: InvestmentPlan) {
  selectedPlan.value = plan
  planForm.value = {
    id: plan.id,
    amount: plan.amount,
    frequency: plan.frequency === 'EVERY_TWO_WEEKS' ? 'BIWEEKLY' : plan.frequency,
    nextExecuteDate: plan.nextExecuteDate,
    status: plan.status
  }
  syncPlanScheduleDraft()
  planActionOpen.value = false
  planEditMode.value = true
}

async function toggleSelectedPlanStatus() {
  const plan = selectedPlan.value
  if (!plan) return
  planSaving.value = true
  try {
    const nextStatus = plan.status === 'PAUSED' ? 'ENABLED' : 'PAUSED'
    const saved = await quantApi.updateInvestmentPlanStatus(plan.id, nextStatus)
    investmentPlans.value = investmentPlans.value.map((item) => item.id === saved.id ? saved : item)
    selectedPlan.value = saved
    planActionOpen.value = false
    ElMessage.success(nextStatus === 'ENABLED' ? '定投计划已恢复' : '定投计划已暂停')
  } finally {
    planSaving.value = false
  }
}

async function deleteSelectedPlan() {
  const plan = selectedPlan.value
  if (!plan) return
  await ElMessageBox.confirm(`确认删除 ${plan.planName}？删除后不会再自动生成定投加仓。`, '删除定投计划', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  planSaving.value = true
  try {
    await quantApi.deleteInvestmentPlan(plan.id)
    investmentPlans.value = investmentPlans.value.filter((item) => item.id !== plan.id)
    selectedPlan.value = null
    planActionOpen.value = false
    ElMessage.success('定投计划已删除')
  } finally {
    planSaving.value = false
  }
}

function applySellShareRatio(ratio: number) {
  const holding = activeHolding.value
  if (!holding) return
  tradeForm.value.tradeShare = ratio === 1
    ? holding.holdingShare
    : Number((holding.holdingShare * ratio).toFixed(2))
}

function syncSellAmountFromShare() {
  tradeForm.value.tradeAmount = Number(estimatedSellAmount.value.toFixed(2))
}

function todayDate() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function localDateTime() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function dateDaysAgo(days: number) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

async function loadSyncBuyLatestNav(holding: FundHolding) {
  syncBuyLatestNav.value = null
  syncBuyNavLoading.value = true
  try {
    const points = await quantApi.fundNav(holding.fundCode, {
      startDate: dateDaysAgo(30),
      endDate: todayDate()
    })
    const sorted = points
      .slice()
      .sort((left, right) => left.date.localeCompare(right.date))
    syncBuyLatestNav.value = sorted.length ? sorted[sorted.length - 1] : null
  } catch {
    syncBuyLatestNav.value = null
  } finally {
    syncBuyNavLoading.value = false
  }
}

function selectedTradeDateTime() {
  if (!tradeForm.value.tradeDate) return localDateTime()
  const time = tradeForm.value.tradeCutoff === 'BEFORE_15' ? '14:59:00' : '15:01:00'
  return `${tradeForm.value.tradeDate}T${time}`
}

function shortDate(value?: string | null) {
  if (!value) return '--'
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return value
  return `${match[2]}-${match[3]}`
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
  if (!holding) return
  if (isSellTrade.value && Number(tradeForm.value.tradeShare || 0) <= 0) {
    ElMessage.warning('请填写有效的卖出份额')
    return
  }
  const effectiveTradeAmount = isSellTrade.value ? Number(estimatedSellAmount.value.toFixed(2)) : Number(tradeForm.value.tradeAmount)
  if (effectiveTradeAmount <= 0) {
    ElMessage.warning('请填写有效的模拟交易金额')
    return
  }
  if (tradeOverLimit.value) {
    ElMessage.warning('卖出或转出金额不能超过当前持仓')
    return
  }
  if ((isBuyTrade.value || isSellTrade.value) && !tradeForm.value.tradeDate) {
    ElMessage.warning(isSellTrade.value ? '请选择原平台卖出日期' : '请选择原平台买入日期')
    return
  }
  tradeSaving.value = true
  try {
    const isProcessing = tradeForm.value.tradeStatus === 'PROCESSING'
    const referenceTradeNav = Number(tradeForm.value.tradeNav) || Number(holding.latestOfficialNav) || 1
    const effectiveTradeFee = isBuyTrade.value ? estimatedBuyFee.value : Number(tradeForm.value.tradeFee || 0)
    const trade = await quantApi.createTrade({
      accountId: holding.accountId,
      holdingId: holding.id,
      fundCode: holding.fundCode,
      fundName: holding.fundName,
      tradeType: tradeForm.value.tradeType,
      tradeStatus: tradeForm.value.tradeStatus,
      tradeAmount: effectiveTradeAmount,
      tradeShare: isSellTrade.value
        ? Number(tradeForm.value.tradeShare)
        : (isProcessing ? undefined : Math.round(Number(tradeForm.value.tradeAmount) / Math.max(referenceTradeNav, 0.0001))),
      tradeNav: isProcessing ? undefined : referenceTradeNav,
      tradeFee: effectiveTradeFee,
      tradeTime: (isBuyTrade.value || isSellTrade.value) ? selectedTradeDateTime() : localDateTime(),
      remark: tradeForm.value.remark || SIMULATED_TRADE_NOTICE
    })
    rememberRecentTrades(trade)
    dialogOpen.value = false
    ElMessage.success('同步记录已保存，待确认净值后自动入账')
  } finally {
    tradeSaving.value = false
  }
}

async function saveInvestmentPlan() {
  const holding = activeHolding.value
  if (!holding || Number(planForm.value.amount) <= 0) {
    ElMessage.warning('请填写有效的定投金额')
    return
  }
  planSaving.value = true
  try {
    const payload: InvestmentPlanRequest = {
      accountId: holding.accountId,
      fundCode: holding.fundCode,
      fundName: holding.fundName,
      planName: `${holding.fundName}定投`,
      amount: Number(planForm.value.amount),
      frequency: planForm.value.frequency,
      nextExecuteDate: planForm.value.nextExecuteDate,
      status: 'ENABLED'
    }
    const saved = planForm.value.id
      ? await quantApi.updateInvestmentPlan(planForm.value.id, { ...payload, status: planForm.value.status })
      : await quantApi.createInvestmentPlan(payload)
    investmentPlans.value = investmentPlans.value.some((item) => item.id === saved.id)
      ? investmentPlans.value.map((item) => item.id === saved.id ? saved : item)
      : [...investmentPlans.value, saved]
    planEditMode.value = false
    selectedPlan.value = null
    ElMessage.success('定投计划已保存，到期后会自动生成待确认加仓记录')
  } finally {
    planSaving.value = false
  }
}

async function settleDueTrades() {
  settlingTrades.value = true
  try {
    await quantApi.settleDueTrades()
    holdings.value = await quantApi.holdings()
    if (selectedHoldingId.value) selectHolding(selectedHoldingId.value)
    ElMessage.success('已尝试结算到期交易，未到确认日或缺少正式净值的交易会继续等待')
  } finally {
    settlingTrades.value = false
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
            <p v-if="isNewHoldingDraft" class="item-meta">新加入基金待完善：请选择一种录入方式，填写后保存即可生成真实持仓口径。</p>
          </div>
          <div class="fund-badges">
            <span>{{ relatedThemeText(activeHolding.relatedThemeName) }}</span>
          <span>盘中估值 {{ activeHolding.currentEstimateNav ? activeHolding.currentEstimateNav.toFixed(4) : '--' }}</span>
          <span>最新正式净值 {{ activeHolding.latestOfficialNav ? activeHolding.latestOfficialNav.toFixed(4) : '--' }}</span>
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
            <button :class="{ active: editMode === 'SHARE_COST' }" @click="editMode = 'SHARE_COST'">份额+总成本</button>
          </div>
        </div>
        <div class="panel-body config-grid compact-form">
          <p class="form-hint full-span">
            只需二选一填写：金额+收益适合从原平台抄当前持有金额和累计收益，系统用最新正式净值反推份额；份额+总成本适合从确认份额和总投入成本录入。
          </p>
          <p class="form-hint full-span">
            净值、持仓金额、收益率和当日收益由数据源与后端统一重算，不在这里手动维护净值。
          </p>
          <p v-if="shareModeMissingNav" class="form-warning full-span">
            当前没有最新正式净值，暂不能测算金额、份额和成本价；请先点击“刷新净值并重算”，盘中估值不会参与持仓份额计算。
          </p>
          <template v-if="editMode === 'AMOUNT_PROFIT'">
            <label>持有金额<input v-model.number="form.holdingAmount" class="form-control" type="number" min="0" /></label>
            <label>持有收益<input v-model.number="form.holdingProfit" class="form-control" type="number" /></label>
          </template>
          <template v-else>
            <label>持有份额<input v-model.number="form.holdingShare" class="form-control" type="number" min="0" /></label>
            <label>总成本<input v-model.number="form.holdingCost" class="form-control" type="number" min="0" /></label>
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
              <tr><td>参考净值</td><td>{{ referenceNav ? referenceNav.toFixed(4) : '--' }}</td><td>仅使用最新正式净值，不使用盘中估值</td></tr>
              <tr><td>测算金额</td><td>{{ money(previewAmount) }}</td><td>份额模式下由份额 × 净值计算</td></tr>
              <tr><td>测算份额</td><td>{{ money(previewShare, 2) }}</td><td>金额收益模式下由总成本 ÷ 成本价计算</td></tr>
              <tr><td>测算成本</td><td>{{ money(previewCost) }}</td><td>金额收益模式下由金额 - 收益计算</td></tr>
              <tr><td>测算成本价</td><td>{{ previewCostNav ? previewCostNav.toFixed(4) : '--' }}</td><td>由最新净值 ÷ (1 + 持有收益率) 反推</td></tr>
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

    <el-dialog v-model="clearDialogOpen" title="清仓持仓" width="560px">
      <DisclaimerBar simulated />
      <div class="modal-grid compact-form">
        <p class="form-hint full-span">
          清仓会把当前持仓金额、份额和成本归零，但保留持仓记录，并生成一条已完成的模拟卖出流水。
        </p>
        <p class="form-hint full-span">
          当前持仓：{{ money(activeHolding.holdingAmount) }} / {{ money(activeHolding.holdingShare, 0) }} 份
        </p>
        <label>到账金额<input v-model.number="clearForm.tradeAmount" class="form-control" type="number" min="0" /></label>
        <label>手续费<input v-model.number="clearForm.tradeFee" class="form-control" type="number" min="0" @change="syncClearRemark" /></label>
        <label>备注<textarea v-model="clearForm.remark" class="form-control text-area" /></label>
      </div>
      <template #footer>
        <button class="ghost-button" @click="clearDialogOpen = false">取消</button>
        <button class="primary-button" :disabled="clearing" @click="saveClearHolding">{{ clearing ? '清仓中' : '确认清仓' }}</button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogOpen" :title="syncDialogTitle" :width="syncDialogWidth">
      <div v-if="isBuyTrade" class="sync-buy-form">
        <div class="sync-buy-fund">
          <strong>{{ activeHolding.fundName }} <span>{{ activeHolding.fundCode }}</span></strong>
          <p v-if="syncBuyNavLoading">
            最新净值同步中...
          </p>
          <p v-else>
            最新净值（{{ syncBuyNavDateText }}）：{{ syncBuyNavValue ? syncBuyNavValue.toFixed(4) : '--' }}
            <b v-if="syncBuyGrowthRate !== null" :class="toneClass(syncBuyGrowthRate)">{{ percent(syncBuyGrowthRate) }}</b>
            <b v-else class="text-muted">--</b>
          </p>
        </div>
        <div class="sync-buy-amount">
          <label>同步加仓金额</label>
          <div class="sync-money-input">
            <span>￥</span>
            <input v-model.number="tradeForm.tradeAmount" type="number" min="0" placeholder="已买入金额" />
          </div>
          <div class="sync-fee-row">
            <span>估算手续费<strong>{{ money(estimatedBuyFee) }}元</strong></span>
            <span>买入费率<input v-model.number="tradeForm.tradeFeeRate" type="number" min="0" step="0.001" />%</span>
          </div>
        </div>
        <div class="sync-buy-time">
          <span>原平台买入时间</span>
          <div>
            <input v-model="tradeForm.tradeDate" class="form-control" type="date" />
            <select v-model="tradeForm.tradeCutoff" class="form-control">
              <option value="BEFORE_15">下午3点前</option>
              <option value="AFTER_15">下午3点后</option>
            </select>
          </div>
        </div>
        <p class="form-hint full-span">将按 {{ tradeForm.tradeDate || '--' }} {{ tradeCutoffText }} 作为原平台购买时间入账；系统会根据 T+1/T+2 规则在确认日使用真实正式净值回填份额。</p>
        <DisclaimerBar simulated />
      </div>
      <div v-else-if="isSellTrade" class="sync-sell-form">
        <div class="sync-buy-fund">
          <strong>{{ activeHolding.fundName }} <span>{{ activeHolding.fundCode }}</span></strong>
          <p v-if="syncBuyNavLoading">
            最新净值同步中...
          </p>
          <p v-else>
            最新净值（{{ syncBuyNavDateText }}）：{{ syncBuyNavValue ? syncBuyNavValue.toFixed(4) : '--' }}
            <b v-if="syncBuyGrowthRate !== null" :class="toneClass(syncBuyGrowthRate)">{{ percent(syncBuyGrowthRate) }}</b>
            <b v-else class="text-muted">--</b>
          </p>
        </div>
        <div class="sync-sell-share">
          <label>同步卖出份额</label>
          <div class="sync-share-input">
            <input v-model.number="tradeForm.tradeShare" type="number" min="0" :placeholder="`最多可选${maxSellShareText}份`" @input="syncSellAmountFromShare" />
            <span>份</span>
          </div>
          <div class="sync-ratio-row">
            <button type="button" @click="applySellShareRatio(0.25)">1/4</button>
            <button type="button" @click="applySellShareRatio(1 / 3)">1/3</button>
            <button type="button" @click="applySellShareRatio(0.5)">1/2</button>
            <button type="button" @click="applySellShareRatio(1)">全部</button>
          </div>
          <div class="sync-fee-row">
            <span>估算卖出金额<strong>{{ money(estimatedSellAmount) }}</strong></span>
            <span>手续费<input v-model.number="tradeForm.tradeFee" type="number" min="0" step="0.01" />元</span>
          </div>
        </div>
        <div class="sync-buy-time">
          <span>原平台卖出时间</span>
          <div>
            <input v-model="tradeForm.tradeDate" class="form-control" type="date" />
            <select v-model="tradeForm.tradeCutoff" class="form-control">
              <option value="BEFORE_15">下午3点前</option>
              <option value="AFTER_15">下午3点后</option>
            </select>
          </div>
        </div>
        <p class="form-hint full-span">将按 {{ tradeForm.tradeDate || '--' }} {{ tradeCutoffText }} 作为原平台卖出时间入账；进行中交易不会立刻改变持仓，系统会在入账日使用真实正式净值结算。</p>
        <p class="form-hint full-span">当前可卖出：{{ money(activeHolding.holdingAmount) }} / {{ money(activeHolding.holdingShare, 2) }} 份</p>
        <p v-if="tradeOverLimit" class="form-warning full-span">卖出份额不能超过当前持仓。</p>
        <DisclaimerBar simulated />
      </div>
      <div v-else class="modal-grid compact-form">
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
        <label>参考净值<input v-model.number="tradeForm.tradeNav" class="form-control" type="number" min="0" step="0.0001" /></label>
        <label>手续费<input v-model.number="tradeForm.tradeFee" class="form-control" type="number" min="0" /></label>
        <p class="form-hint full-span">进行中交易不会立刻改变持仓；系统会在入账日使用真实正式净值回填份额和净值。</p>
        <p v-if="isDecreaseTrade" class="form-hint full-span">当前可卖出/转出：{{ money(activeHolding.holdingAmount) }} / {{ money(activeHolding.holdingShare, 0) }} 份</p>
        <p v-if="tradeOverLimit" class="form-warning full-span">卖出或转出金额不能超过当前持仓。</p>
        <label>备注<textarea v-model="tradeForm.remark" class="form-control text-area" /></label>
      </div>
      <template #footer>
        <button class="primary-button" :disabled="tradeSaving" @click="saveTrade">{{ tradeSaving ? '保存中' : (isBuyTrade ? '确认同步加仓' : (isSellTrade ? '保存减仓记录' : '保存模拟记录')) }}</button>
      </template>
    </el-dialog>

    <el-dialog v-model="planDialogOpen" title="同步定投" width="720px">
      <div class="sync-plan-form">
        <DisclaimerBar simulated />
        <div class="sync-buy-fund">
          <strong>{{ activeHolding.fundName }} <span>{{ activeHolding.fundCode }}</span></strong>
          <p v-if="syncBuyNavLoading">
            最新净值同步中...
          </p>
          <p v-else>
            最新净值（{{ syncBuyNavDateText }}）：{{ syncBuyNavValue ? syncBuyNavValue.toFixed(4) : '--' }}
            <b v-if="syncBuyGrowthRate !== null" :class="toneClass(syncBuyGrowthRate)">{{ percent(syncBuyGrowthRate) }}</b>
            <b v-else class="text-muted">--</b>
          </p>
        </div>

        <template v-if="!planEditMode">
          <div class="sync-plan-list">
            <div class="sync-plan-section-title">定投计划</div>
            <LoadingState v-if="planLoading" text="正在加载定投计划" />
            <div v-else-if="activeInvestmentPlans.length" class="sync-plan-items">
              <button v-for="plan in activeInvestmentPlans" :key="plan.id" class="sync-plan-card" type="button" @click="openPlanAction(plan)">
                <div>
                  <strong>{{ plan.planName }}</strong>
                  <span>{{ frequencyLabel(plan.frequency) }}定投{{ money(plan.amount) }}元</span>
                </div>
                <div>
                  <em :class="{ paused: plan.status === 'PAUSED' }">{{ planStatusLabel(plan.status) }}</em>
                  <small>下次 {{ plan.nextExecuteDate }}</small>
                </div>
              </button>
            </div>
            <EmptyState v-else title="暂无定投计划" description="添加计划后，系统会按设定周期自动生成待确认加仓。" />
            <button class="sync-plan-add" type="button" @click="openCreatePlanForm">+ 添加定投计划</button>
          </div>
        </template>

        <template v-else>
          <div class="sync-buy-amount">
            <label>同步定投金额</label>
            <div class="sync-money-input">
              <span>￥</span>
              <input v-model.number="planForm.amount" type="number" min="0" placeholder="输入已定投金额" />
            </div>
            <div class="sync-fee-row">
              <span>定投会按 15:00 前操作生成，入账逻辑与同步加仓一致</span>
            </div>
          </div>
          <div class="sync-plan-edit-grid">
            <button class="sync-plan-picker-card" type="button" @click="openPlanSchedulePicker">
              <span>原平台定投周期</span>
              <strong>{{ planScheduleText }}</strong>
              <small>下次定投 {{ planForm.nextExecuteDate }}</small>
            </button>
            <label v-if="planForm.id">计划状态
              <select v-model="planForm.status" class="form-control">
                <option value="ENABLED">执行中</option>
                <option value="PAUSED">暂停</option>
              </select>
            </label>
          </div>
          <p class="form-hint full-span">计划到期后，系统会在 09:05 自动生成一笔 15:00 前的待确认加仓交易；后续按 T+1/T+2 正式净值入账。</p>
        </template>
      </div>
      <template #footer>
        <button v-if="planEditMode" class="ghost-button" @click="planEditMode = false">返回列表</button>
        <button v-if="planEditMode" class="primary-button" :disabled="planSaving" @click="saveInvestmentPlan">{{ planSaving ? '保存中' : '保存定投计划' }}</button>
      </template>
    </el-dialog>

    <el-dialog v-model="planActionOpen" width="420px" title="" class="plan-action-dialog">
      <div class="plan-action-sheet">
        <button type="button" :disabled="planSaving" @click="toggleSelectedPlanStatus">{{ selectedPlan?.status === 'PAUSED' ? '恢复' : '暂停' }}</button>
        <button type="button" @click="selectedPlan && openEditPlanForm(selectedPlan)">修改</button>
        <button type="button" class="danger" :disabled="planSaving" @click="deleteSelectedPlan">删除</button>
      </div>
    </el-dialog>

    <el-dialog v-model="planScheduleOpen" width="640px" title="" append-to-body class="plan-schedule-dialog">
      <div class="plan-schedule-picker">
        <div class="plan-schedule-header">
          <button type="button" @click="planScheduleOpen = false">取消</button>
          <strong>定投周期</strong>
          <button type="button" @click="confirmPlanSchedule">确认</button>
        </div>
        <div class="plan-schedule-body">
          <div class="plan-schedule-column" @wheel.prevent="onPlanFrequencyWheel">
            <button
              v-for="(option, index) in planFrequencyWheelItems"
              :key="`${option.value}-${index}`"
              type="button"
              :class="{ active: option.active, placeholder: option.disabled }"
              :disabled="option.disabled"
              @click="selectPlanDraftFrequency(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
          <div v-if="planScheduleDraftFrequency === 'WEEKLY' || planScheduleDraftFrequency === 'BIWEEKLY'" class="plan-schedule-column" @wheel.prevent="onPlanWeekdayWheel">
            <button
              v-for="(option, index) in planWeekdayWheelItems"
              :key="`${option.value}-${index}`"
              type="button"
              :class="{ active: option.active, placeholder: option.disabled }"
              :disabled="option.disabled"
              @click="planScheduleDraftWeekday = option.value"
            >
              {{ option.label }}
            </button>
          </div>
          <div v-else-if="planScheduleDraftFrequency === 'MONTHLY'" class="plan-schedule-column scrollable" @wheel.prevent="onPlanMonthDayWheel">
            <button
              v-for="(day, index) in planMonthDayWheelItems"
              :key="`${day.value}-${index}`"
              type="button"
              :class="{ active: day.active, placeholder: day.disabled }"
              :disabled="day.disabled"
              @click="planScheduleDraftMonthDay = day.value"
            >
              {{ day.label }}
            </button>
          </div>
          <div v-else class="plan-schedule-column single">
            <button type="button" class="active">每日交易日</button>
          </div>
        </div>
        <p>{{ planScheduleDraftText }}</p>
      </div>
    </el-dialog>
  </div>
</template>
