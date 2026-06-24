<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { quantApi } from '@/api/quant'
import type { FundHolding, TradeRecord } from '@/types/domain'
import { SIMULATED_TRADE_NOTICE } from '@/types/domain'
import ActionTag from '@/components/common/ActionTag.vue'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import MetricTile from '@/components/common/MetricTile.vue'
import { money } from '@/utils/format'

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
const filter = ref('ALL')
const dialogOpen = ref(false)
const saving = ref(false)
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

onMounted(loadData)

async function loadData() {
  const [tradeList, holdingList] = await Promise.all([quantApi.trades(), quantApi.holdings()])
  trades.value = tradeList
  holdings.value = holdingList
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

function localDateTime() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
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
    const trade = await quantApi.createTrade({
      accountId: holding?.accountId || 1,
      holdingId: tradeForm.value.holdingId,
      fundCode: tradeForm.value.fundCode,
      fundName: tradeForm.value.fundName,
      tradeType: tradeForm.value.tradeType,
      tradeStatus: tradeForm.value.tradeStatus,
      tradeAmount: Number(tradeForm.value.tradeAmount),
      tradeShare: Number(tradeForm.value.tradeShare) || Math.round(Number(tradeForm.value.tradeAmount) / Math.max(nav, 0.0001)),
      tradeNav: nav,
      tradeFee: Number(tradeForm.value.tradeFee),
      tradeTime: localDateTime(),
      remark: tradeForm.value.remark || SIMULATED_TRADE_NOTICE
    })
    trades.value = [trade, ...trades.value.filter((item) => item.id !== trade.id)]
    dialogOpen.value = false
    ElMessage.success('模拟交易记录已保存')
  } finally {
    saving.value = false
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
          <button class="primary-button" @click="openDialog">添加交易记录</button>
        </div>
      </div>
      <div class="panel-body">
        <div class="metric-row trade-summary">
          <MetricTile label="当前筛选金额" :value="money(totalAmount)" />
          <MetricTile label="筛选记录数" :value="`${filtered.length} 笔`" />
          <MetricTile label="进行中交易" :value="`${processingCount} 笔`" tone="warning" />
          <MetricTile label="已完成交易" :value="`${completedCount} 笔`" tone="info" />
        </div>

        <table v-if="filtered.length" class="terminal-table">
          <thead>
            <tr><th>时间</th><th>基金</th><th>类型</th><th>状态</th><th>金额</th><th>份额</th><th>净值</th><th>手续费</th><th>备注</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in filtered" :key="item.id">
              <td>{{ item.tradeTime }}</td>
              <td>{{ item.fundCode }} · {{ item.fundName }}</td>
              <td><ActionTag :action="actionForTrade(item.tradeType)" :text="tradeTypeLabel(item.tradeType)" /></td>
              <td><span class="status-pill" :class="item.tradeStatus.toLowerCase()">{{ statusLabel(item.tradeStatus) }}</span></td>
              <td>{{ money(item.tradeAmount) }}</td>
              <td>{{ money(item.tradeShare, 0) }}</td>
              <td>{{ item.tradeNav.toFixed(4) }}</td>
              <td>{{ money(item.tradeFee) }}</td>
              <td>{{ item.remark }}</td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无交易记录" description="当前筛选条件下还没有模拟交易记录。" />
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
  </div>
</template>
