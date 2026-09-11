<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { quantApi } from '@/api/quant'
import { SIMULATED_TRADE_NOTICE, type ClearHoldingRequest, type FundHolding, type FundSearchMode, type FundSearchResult, type PortfolioAccount } from '@/types/domain'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { money, percent, signed, toneClass } from '@/utils/format'

const holdings = ref<FundHolding[]>([])
const portfolios = ref<PortfolioAccount[]>([])
const keyword = ref('')
const searchKeyword = ref('')
const searchMode = ref<FundSearchMode>('FUZZY')
const activeType = ref('ALL')
const selectedAccountId = ref<number>()
const searchResults = ref<FundSearchResult[]>([])
const loading = ref(false)
const searching = ref(false)
const addingCode = ref('')
const deletingId = ref<number>()
const clearingId = ref<number>()
const clearDialogOpen = ref(false)
const clearTarget = ref<FundHolding>()
const clearForm = ref<ClearHoldingRequest>({
  tradeAmount: 0,
  tradeFee: 0,
  remark: `清仓自动生成的模拟卖出流水，${SIMULATED_TRADE_NOTICE}`
})
const router = useRouter()

const fundTypeFilters = [
  { label: '全部', value: 'ALL' },
  { label: '主动', value: 'ACTIVE' },
  { label: '指数', value: 'INDEX' },
  { label: 'ETF', value: 'ETF' },
  { label: '债券', value: 'BOND' }
]

onMounted(loadInitialData)

const filtered = computed(() => holdings.value.filter((item) => {
  const keywordHit = !keyword.value || item.fundName.includes(keyword.value) || item.fundCode.includes(keyword.value)
  const typeHit = activeType.value === 'ALL' || holdingMatchesType(item, activeType.value)
  return keywordHit && typeHit
}))

function holdingMatchesType(item: FundHolding, filterType: string) {
  return inferHoldingType(item) === filterType
}

function inferHoldingType(item: FundHolding) {
  const text = `${item.fundName || ''} ${item.fundType || ''}`.toUpperCase()
  if (text.includes('ETF')) return 'ETF'
  if (hasAny(text, ['BOND', 'FIXED_INCOME', '债', '固收', '纯债', '短债', '转债'])) return 'BOND'
  if (hasAny(text, ['INDEX', '指数', '增强', '联接', '沪深300', '中证', '创业板', '科创板', '恒生', '纳斯达克', '标普'])) return 'INDEX'
  if (hasAny(text, [
    'ACTIVE',
    'MIXED',
    'QDII',
    '混合',
    '股票',
    '智选',
    '精选',
    '成长',
    '优选',
    '远见',
    '价值',
    '优势',
    '创新',
    '核心',
    '行业',
    '主题',
    '灵活配置'
  ])) return 'ACTIVE'
  return 'UNKNOWN'
}

function hasAny(text: string, tokens: string[]) {
  return tokens.some((token) => text.includes(token.toUpperCase()))
}

async function loadInitialData() {
  loading.value = true
  try {
    const [holdingList, accountList] = await Promise.all([
      quantApi.holdings(),
      quantApi.portfolios()
    ])
    holdings.value = holdingList
    portfolios.value = accountList
    selectedAccountId.value = accountList[0]?.id
  } finally {
    loading.value = false
  }
}

function normalizeFundType(rawType: string) {
  const value = rawType.toUpperCase()
  if (value.includes('ETF')) return value.includes('LINK') || rawType.includes('联接') ? 'ETF_LINK' : 'ETF'
  if (value.includes('INDEX') || rawType.includes('指数')) return rawType.includes('增强') ? 'INDEX_ENHANCED' : 'INDEX'
  if (value.includes('BOND') || rawType.includes('债')) return 'BOND'
  if (value.includes('MONEY') || rawType.includes('货币')) return 'MONEY_MARKET'
  if (value.includes('QDII') || rawType.includes('海外') || rawType.includes('全球')) return 'QDII'
  if (value.includes('MIXED') || rawType.includes('混合')) return 'MIXED'
  if (value.includes('ACTIVE') || rawType.includes('股票') || rawType.includes('主动')) return 'ACTIVE_EQUITY'
  return 'UNKNOWN'
}

function displayFundType(type: string) {
  const labels: Record<string, string> = {
    ACTIVE_EQUITY: '主动权益',
    INDEX: '指数',
    ETF: 'ETF',
    ETF_LINK: 'ETF联接',
    INDEX_ENHANCED: '指数增强',
    BOND: '债券',
    FIXED_INCOME_PLUS: '固收+',
    MONEY_MARKET: '货币',
    QDII: 'QDII',
    MIXED: '混合',
    UNKNOWN: '未知'
  }
  return labels[type] || type
}

function relatedThemeText(theme?: string | null) {
  return theme && theme !== '主动权益' ? theme : '重仓板块待同步'
}

function updatedBadgeText(date?: string | null) {
  if (!date) return '已更新'
  const today = new Date().toISOString().slice(0, 10)
  return date === today ? '已更新' : `已更新至 ${date.slice(5)}`
}

function navText(value: number | null | undefined) {
  return value === null || value === undefined ? '--' : value.toFixed(4)
}

async function searchFunds() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入基金名称、代码或拼音')
    return
  }
  searching.value = true
  try {
    searchResults.value = await quantApi.searchFunds(keyword, searchMode.value)
    if (!searchResults.value.length) {
      ElMessage.info('未搜索到匹配基金，请换一个关键词试试')
    }
  } finally {
    searching.value = false
  }
}

async function ensureAccount() {
  if (selectedAccountId.value) return selectedAccountId.value
  let account = portfolios.value[0]
  if (!account) {
    account = await quantApi.createPortfolio({
      accountName: '手动基金账户',
      platformType: 'MANUAL',
      maxSingleFundPositionRate: 25
    })
    portfolios.value = [account]
  }
  selectedAccountId.value = account.id
  return account.id
}

async function addFund(result: FundSearchResult) {
  const existing = holdings.value.find((item) => item.fundCode === result.fundCode)
  if (existing) {
    ElMessage.info('该基金已在自选/持仓中，可继续完善持有信息')
    router.push({ path: '/holding-edit', query: { holdingId: existing.id, fundCode: existing.fundCode } })
    return
  }
  addingCode.value = result.fundCode
  try {
    const accountId = await ensureAccount()
    const fundType = normalizeFundType(result.fundType)
    const saved = await quantApi.createHolding({
      accountId,
      fundCode: result.fundCode,
      fundName: result.fundName,
      fundType,
      activeFund: fundType === 'ACTIVE_EQUITY' || fundType === 'MIXED',
      holdingAmount: 0,
      holdingShare: 0,
      holdingCost: 0,
      sourcePlatform: '手动添加',
      regularInvestment: false,
      coreHolding: false,
      watchFocus: true
    })
    holdings.value = [saved, ...holdings.value]
    ElMessage.success('已加入持仓，请继续填写持有金额/收益或份额/成本')
    router.push({ path: '/holding-edit', query: { holdingId: saved.id, fundCode: saved.fundCode } })
  } finally {
    addingCode.value = ''
  }
}

async function deleteHolding(item: FundHolding) {
  await ElMessageBox.confirm(`确认删除 ${item.fundName} 的持有记录？删除后不再展示该持仓；如只是已经卖完，请使用清仓。`, '删除持仓', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  deletingId.value = item.id
  try {
    await quantApi.deleteHolding(item.id)
    holdings.value = holdings.value.filter((holding) => holding.id !== item.id)
    ElMessage.success('持仓已删除')
  } finally {
    deletingId.value = undefined
  }
}

function clearRemark(tradeFee: number) {
  const feeText = tradeFee > 0 ? `，手续费 ${tradeFee.toFixed(2)}` : ''
  return `清仓自动生成的模拟卖出流水${feeText}，${SIMULATED_TRADE_NOTICE}`
}

function openClearDialog(item: FundHolding) {
  clearTarget.value = item
  clearForm.value = {
    tradeAmount: Number(item.holdingAmount.toFixed(2)),
    tradeFee: 0,
    remark: clearRemark(0)
  }
  clearDialogOpen.value = true
}

function syncClearRemark() {
  clearForm.value.remark = clearRemark(Number(clearForm.value.tradeFee || 0))
}

async function saveClearHolding() {
  const item = clearTarget.value
  if (!item) return
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
  clearingId.value = item.id
  try {
    const saved = await quantApi.clearHolding(item.id, request)
    holdings.value = holdings.value.map((holding) => holding.id === saved.id ? saved : holding)
    clearDialogOpen.value = false
    clearTarget.value = undefined
    ElMessage.success('持仓已清仓，历史记录已保留')
  } finally {
    clearingId.value = undefined
  }
}

function openDetail(item: FundHolding) {
  router.push({ path: '/fund-detail', query: { fundCode: item.fundCode, holdingId: item.id } })
}

function openEdit(item: FundHolding) {
  router.push({ path: '/holding-edit', query: { fundCode: item.fundCode, holdingId: item.id } })
}
</script>

<template>
  <div class="screen-grid">
    <section class="panel fund-search-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">搜索并加入基金</h2>
          <p class="panel-subtitle">支持基金名称、代码或拼音搜索真实基金数据；加入后可填写持有金额/收益或份额/成本。</p>
        </div>
        <select v-if="portfolios.length" v-model.number="selectedAccountId" class="form-control account-select">
          <option v-for="account in portfolios" :key="account.id" :value="account.id">{{ account.accountName }}</option>
        </select>
      </div>
      <div class="panel-body fund-search-body">
        <div class="fund-search-input">
          <input
            v-model="searchKeyword"
            class="form-control"
            placeholder="输入基金代码 / 名称 / 拼音，例如 161725、白酒"
            @keyup.enter="searchFunds"
          />
          <div class="segmented search-mode">
            <button :class="{ active: searchMode === 'FUZZY' }" @click="searchMode = 'FUZZY'">模糊搜索</button>
            <button :class="{ active: searchMode === 'EXACT' }" @click="searchMode = 'EXACT'">精确搜索</button>
          </div>
          <button class="primary-button" :disabled="searching" @click="searchFunds">{{ searching ? '搜索中' : '搜索基金' }}</button>
        </div>
        <div v-if="searchResults.length" class="fund-result-grid">
          <article v-for="result in searchResults" :key="result.fundCode" class="fund-result-card">
            <div>
              <strong>{{ result.fundName }}</strong>
              <span>{{ result.fundCode }} · {{ displayFundType(normalizeFundType(result.fundType)) }} · {{ result.sourceName }}</span>
            </div>
            <button class="ghost-button" :disabled="addingCode === result.fundCode" @click="addFund(result)">
              {{ addingCode === result.fundCode ? '加入中' : '加入持仓' }}
            </button>
          </article>
        </div>
        <EmptyState
          v-else-if="!loading && !holdings.length"
          title="还没有持仓基金"
          description="先搜索基金名称或代码，把关注基金加入自选/持仓。"
        />
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">自选/持仓列表</h2>
        <div class="toolbar-row">
          <input v-model="keyword" class="form-control" placeholder="搜索基金代码 / 名称" />
          <div class="segmented">
            <button
              v-for="type in fundTypeFilters"
              :key="type.value"
              :class="{ active: activeType === type.value }"
              @click="activeType = type.value"
            >
              {{ type.label }}
            </button>
          </div>
        </div>
      </div>
      <div class="panel-body">
        <div v-if="loading" class="holding-table-skeleton" aria-label="正在加载持仓列表">
          <div class="skeleton-toolbar">
            <span></span>
            <span></span>
          </div>
          <div class="skeleton-table">
            <div class="skeleton-row skeleton-head">
              <i v-for="item in 8" :key="`head-${item}`"></i>
            </div>
            <div v-for="row in 5" :key="`row-${row}`" class="skeleton-row">
              <i v-for="cell in 8" :key="`cell-${row}-${cell}`"></i>
            </div>
          </div>
          <div class="skeleton-hint">
            <strong>正在同步持仓、估值与净值</strong>
          </div>
        </div>
        <table v-else-if="filtered.length" class="terminal-table">
          <thead>
            <tr>
              <th>代码</th>
              <th>基金名称</th>
              <th>当日收益</th>
              <th>关联板块/当日估值</th>
              <th>持有收益/收益率</th>
              <th>持仓占比</th>
              <th>估值/净值</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filtered" :key="item.id" class="clickable-row" @click="openDetail(item)">
              <td>{{ item.fundCode }}</td>
              <td>
                <div class="holding-name-cell">
                  <span>{{ item.fundName }}</span>
                  <div class="holding-meta-row">
                    <strong v-if="item.officialNavUpdated" class="updated-badge">{{ updatedBadgeText(item.officialNavDate) }}</strong>
                    <strong class="holding-amount-badge">￥{{ money(item.holdingAmount) }}</strong>
                  </div>
                </div>
              </td>
              <td :class="toneClass(item.dailyProfit)">{{ signed(item.dailyProfit) }}</td>
              <td>
                <div class="metric-pair">
                  <strong>{{ relatedThemeText(item.relatedThemeName) }}</strong>
                  <span :class="toneClass(item.currentEstimateGrowthRate)">{{ percent(item.currentEstimateGrowthRate || 0) }}</span>
                </div>
              </td>
              <td>
                <div class="metric-pair">
                  <strong :class="toneClass(item.holdingProfit)">{{ signed(item.holdingProfit) }}</strong>
                  <span :class="toneClass(item.holdingProfitRate)">{{ percent(item.holdingProfitRate) }}</span>
                </div>
              </td>
              <td>{{ percent(item.positionRate || 0) }}</td>
              <td>{{ navText(item.currentEstimateNav) }} / {{ navText(item.latestOfficialNav) }}</td>
              <td>
                <div class="table-actions">
                  <button class="ghost-button table-button" @click.stop="openDetail(item)">详情</button>
                  <button class="primary-button table-button" @click.stop="openEdit(item)">编辑</button>
                  <button class="ghost-button table-button" :disabled="clearingId === item.id" @click.stop="openClearDialog(item)">
                    {{ clearingId === item.id ? '清仓中' : '清仓' }}
                  </button>
                  <button class="ghost-button table-button danger-button" :disabled="deletingId === item.id" @click.stop="deleteHolding(item)">
                    {{ deletingId === item.id ? '删除中' : '删除' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无匹配持仓" description="可以先在上方搜索基金并加入持仓。" />
      </div>
    </section>
    <el-dialog v-model="clearDialogOpen" title="清仓持仓" width="560px">
      <DisclaimerBar simulated />
      <div v-if="clearTarget" class="modal-grid compact-form">
        <p class="form-hint full-span">
          清仓会把当前持仓金额、份额和成本归零，但保留持仓记录，并生成一条已完成的模拟卖出流水。
        </p>
        <p class="form-hint full-span">
          当前持仓：{{ money(clearTarget.holdingAmount) }} / {{ money(clearTarget.holdingShare, 0) }} 份
        </p>
        <label>到账金额<input v-model.number="clearForm.tradeAmount" class="form-control" type="number" min="0" /></label>
        <label>手续费<input v-model.number="clearForm.tradeFee" class="form-control" type="number" min="0" @change="syncClearRemark" /></label>
        <label>备注<textarea v-model="clearForm.remark" class="form-control text-area" /></label>
      </div>
      <template #footer>
        <button class="ghost-button" @click="clearDialogOpen = false">取消</button>
        <button class="primary-button" :disabled="clearTarget ? clearingId === clearTarget.id : false" @click="saveClearHolding">
          {{ clearTarget && clearingId === clearTarget.id ? '清仓中' : '确认清仓' }}
        </button>
      </template>
    </el-dialog>
    <DisclaimerBar simulated />
  </div>
</template>
