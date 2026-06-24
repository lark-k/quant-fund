<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { quantApi } from '@/api/quant'
import type { FundHolding, FundSearchMode, FundSearchResult, PortfolioAccount } from '@/types/domain'
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
  const typeHit = activeType.value === 'ALL' || item.fundType.includes(activeType.value)
  return keywordHit && typeHit
}))

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
  if (holdings.value.some((item) => item.fundCode === result.fundCode)) {
    ElMessage.info('该基金已在自选/持仓中')
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
    ElMessage.success('已加入自选/持仓，可继续填写份额、成本或收益')
  } finally {
    addingCode.value = ''
  }
}

async function deleteHolding(item: FundHolding) {
  await ElMessageBox.confirm(`确认删除 ${item.fundName} 的持有记录？`, '删除持仓', {
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
          v-else-if="!holdings.length"
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
        <table v-if="!loading && filtered.length" class="terminal-table">
          <thead>
            <tr>
              <th>代码</th>
              <th>基金名称</th>
              <th>当日收益</th>
              <th>关联板块/收益率</th>
              <th>持有收益/收益率</th>
              <th>持仓占比</th>
              <th>估值/净值</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filtered" :key="item.id" class="clickable-row" @click="openDetail(item)">
              <td>{{ item.fundCode }}</td>
              <td>{{ item.fundName }}</td>
              <td :class="toneClass(item.dailyProfit)">{{ signed(item.dailyProfit) }}</td>
              <td>
                <div class="metric-pair">
                  <strong>{{ relatedThemeText(item.relatedThemeName) }}</strong>
                  <span :class="toneClass(item.relatedThemeRate)">{{ percent(item.relatedThemeRate || 0) }}</span>
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
                  <button class="ghost-button table-button danger-button" :disabled="deletingId === item.id" @click.stop="deleteHolding(item)">
                    {{ deletingId === item.id ? '删除中' : '删除' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else-if="loading" title="正在加载持仓" />
        <EmptyState v-else title="暂无匹配持仓" description="可以先在上方搜索基金并加入持仓。" />
      </div>
    </section>
    <DisclaimerBar simulated />
  </div>
</template>
