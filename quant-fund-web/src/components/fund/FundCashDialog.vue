<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fundCashApi, type FundCashSnapshot, type FundCashRow } from '@/api/fundCash'
import { reallocateCash } from '@/utils/fundCashAllocation'
import type { PortfolioAccount } from '@/types/domain'
import { money } from '@/utils/format'

const props = defineProps<{ modelValue: boolean; accounts: PortfolioAccount[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const accountId = ref<number>()
const draft = ref<FundCashSnapshot>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let generation = 0
let allocated = new Map<string, number>()
const cents = (value: number) => Math.round(Number(value || 0) * 100)
const total = computed(() => draft.value ? (cents(draft.value.unallocated) + draft.value.funds.reduce((sum, f) => sum + cents(f.balance), 0)) / 100 : 0)
const overall = computed(() => props.accounts.reduce((sum, a) => sum + cents(a.id === accountId.value ? total.value : a.cashAmount), 0) / 100)
const valid = computed(() => !!draft.value && [draft.value.unallocated, ...draft.value.funds.map(f => f.balance)].every(n => typeof n === 'number' && Number.isFinite(n) && n >= 0 && n <= 1e12))
watch(() => props.modelValue, open => {
  if (open) { accountId.value = props.accounts[0]?.id; void load() }
  else { generation++; draft.value = undefined }
}, { immediate: true })
async function load() {
  const id = accountId.value, request = ++generation
  draft.value = undefined; error.value = ''; loading.value = true
  if (!id) { error.value = '暂无可分配现金的账户'; loading.value = false; return }
  try {
    const value = await fundCashApi.get(id)
    if (request === generation) {
      draft.value = { ...value, unallocated: cents(value.unallocated) / 100, funds: value.funds.map(f => ({ ...f, balance: cents(f.balance) / 100 })) }
      allocated = new Map(draft.value.funds.map(f => [f.fundCode, f.balance]))
    }
  } catch (e) {
    if (request === generation) error.value = e instanceof Error ? e.message : '读取失败，请重试'
  } finally { if (request === generation) loading.value = false }
}
function redistribute(fund: FundCashRow, value: number | undefined) {
  if (!draft.value) return
  const previous = allocated.get(fund.fundCode) ?? 0
  const result = reallocateCash(draft.value.unallocated, previous, value ?? NaN)
  if (!result.ok) {
    fund.balance = previous
    ElMessage.warning(result.reason === 'insufficient' ? '未分配现金不足，请先补充未分配现金，或调减其他基金的分配。' : '请输入有效的非负现金金额')
    return
  }
  draft.value.unallocated = result.unallocated
  fund.balance = result.balance
  allocated.set(fund.fundCode, result.balance)
}
async function save() {
  if (!draft.value || !accountId.value || !valid.value) return
  saving.value = true; error.value = ''
  try {
    await fundCashApi.save(accountId.value, { version: draft.value.version, unallocated: draft.value.unallocated, funds: draft.value.funds.map(f => ({ fundCode: f.fundCode, balance: f.balance })) })
    emit('saved'); emit('update:modelValue', false); ElMessage.success('基金可用现金已保存，首页总现金已汇总')
  } catch (e) { error.value = e instanceof Error ? e.message : '保存失败，请重新读取后重试' }
  finally { saving.value = false }
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="分配基金可用现金" width="min(820px, 94vw)" class="fund-cash-dialog"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @update:model-value="emit('update:modelValue', $event)">
    <div class="cash-content" :aria-busy="loading || saving">
      <label v-if="accounts.length > 1" class="account-picker">账户
        <el-select v-model="accountId" aria-label="现金所属账户" :disabled="saving || loading" @change="load">
          <el-option v-for="account in accounts" :key="account.id" :label="account.accountName" :value="account.id" />
        </el-select>
      </label>
      <p v-if="loading" role="status">正在读取全部持仓与基金现金…</p>
      <div v-if="error" class="cash-error" role="alert">{{ error }} <button type="button" class="secondary-button" :disabled="saving" @click="load">重新读取</button></div>
      <template v-if="draft">
        <div class="cash-summary"><span>本账户现金合计<strong>¥ {{ money(total) }}</strong></span><span v-if="accounts.length > 1">首页总现金<strong>¥ {{ money(overall) }}</strong></span><span>{{ draft.funds.length }} 只基金</span></div>
        <div class="fund-cash-list">
          <div class="cash-row cash-unallocated">
            <label for="cash-unallocated">未分配现金<small>分配给基金时自动扣除，调减基金现金时自动退回。直接修改此项表示补充或取出现金。</small></label>
            <el-input-number id="cash-unallocated" v-model="draft.unallocated" aria-label="未分配现金（元）" :min="0" :max="1e12" :precision="2" :step="100" :disabled="saving" controls-position="right" />
          </div>
          <div v-for="fund in draft.funds" :key="fund.fundCode" class="cash-row">
            <label :for="`fund-cash-${fund.fundCode}`">{{ fund.fundName }}<small>{{ fund.fundCode }}<span v-if="fund.archived"> · 已移除持仓，现金保留</span></small>
              <small v-if="fund.pendingBuy || fund.pendingSell">待确认买入 ¥{{ money(fund.pendingBuy) }} · 待确认卖出约 ¥{{ money(fund.pendingSell) }}</small>
              <small v-if="fund.balance < 0" class="cash-error">已完成买入超出分配资金，请核对并补充余额。</small>
            </label>
            <el-input-number :id="`fund-cash-${fund.fundCode}`" v-model="fund.balance" :aria-label="`${fund.fundName}可用现金（元）`" :max="1e12" :precision="2" :step="100" :disabled="saving" controls-position="right" @change="redistribute(fund, $event)" />
          </div>
          <p v-if="!draft.funds.length">当前账户没有持仓，可先保留未分配现金。</p>
        </div>
        <p class="cash-note">交易确认完成后：买入扣除金额及费用，卖出计入扣费后回款。待确认交易尚未计入，请预留所需资金。现金调整不会改动持仓成本或收益。</p>
        <p v-if="!valid" class="cash-error">请将现金填写为 0 至 1 万亿元之间的有效金额；负数表示待补资金。</p>
      </template>
    </div>
    <template #footer><button class="secondary-button" type="button" :disabled="saving" @click="emit('update:modelValue', false)">取消</button><button class="primary-button" type="button" :disabled="loading || saving || !valid" @click="save">{{ saving ? '保存中…' : '保存现金分配' }}</button></template>
  </el-dialog>
</template>

<style scoped>
.cash-content { color: #e4edf8; font-size: 15px; line-height: 1.6; }
.account-picker { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.cash-summary { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 18px 20px; background: #203247; border: 1px solid #526d88; border-radius: 10px; margin-bottom: 16px; }
.cash-summary strong { display: block; font-size: 25px; color: #f3d88f; font-variant-numeric: tabular-nums; }
.fund-cash-list { max-height: 49vh; overflow: auto; padding-right: 6px; }
.cash-row { display: grid; grid-template-columns: minmax(0, 1fr) 190px; align-items: center; gap: 20px; padding: 15px 0; border-bottom: 1px solid #374b60; }
.cash-row label { font-weight: 600; overflow-wrap: anywhere; }
.cash-row small { display: block; color: #bacbdf; font-weight: 400; font-size: 13px; margin-top: 3px; }
.cash-row :deep(.el-input-number) { width: 100%; }
.cash-row :deep(.el-input__wrapper) { background: #223247; box-shadow: 0 0 0 1px #6a829a inset; }
.cash-row :deep(.el-input__inner) { color: #f3f7fd; font-size: 16px; font-weight: 600; }
.cash-note { color: #c3d2e4; font-size: 13px; margin: 16px 0 0; }
.cash-error, .cash-row .cash-error { color: #ffb4ab; }
@media(max-width: 600px) { .cash-row { grid-template-columns: minmax(0, 1fr) 145px; gap: 10px; } .cash-summary { flex-wrap: wrap; padding: 12px; } .cash-summary strong { font-size: 21px; } }
</style>
