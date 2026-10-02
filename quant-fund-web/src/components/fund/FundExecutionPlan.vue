<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { TechnicalExecution } from '@/utils/fundTechnicalAdvice'
import { executionPreview } from '@/utils/executionPreview'
import { money } from '@/utils/format'

const props = defineProps<{ plan: TechnicalExecution }>()
const draft = ref(0), clear = ref(false)
watch(() => props.plan, p => { draft.value = p.suggestedAmount; clear.value = false }, { immediate: true })
const preview = computed(() => executionPreview(props.plan, draft.value, clear.value))
const ready = computed(() => props.plan.direction !== 'NONE')
function reset() { draft.value = props.plan.suggestedAmount; clear.value = false }
function clearPosition() { draft.value = props.plan.holdingAmount; clear.value = true }
</script>

<template>
  <section class="execution-plan" :data-direction="plan.direction" aria-label="持仓与执行金额">
    <div class="execution-heading"><h3>持仓与执行金额</h3><span>本账户 · 该基金</span></div>
    <div class="execution-metrics">
      <div><span>当前持仓参考</span><strong>¥ {{ money(plan.holdingAmount) }}</strong></div>
      <div><span>基金可用现金</span><strong>¥ {{ money(plan.cashBalance) }}</strong></div>
      <div class="execution-weight"><span>当前仓位 → 策略目标</span><strong>{{ plan.currentWeight === null ? '—' : plan.currentWeight.toFixed(1) + '%' }} <small>→</small> <em>{{ plan.targetWeight }}%</em></strong></div>
    </div>
    <template v-if="ready">
      <div class="execution-editor">
        <label for="execution-amount">{{ plan.direction === 'BUY' ? '买入预算（含费用，元）' : '减仓参考金额（元）' }}</label>
        <el-input-number id="execution-amount" v-model="draft" aria-label="调整执行金额" :min="0" :max="1e12" :precision="2" :step="100" controls-position="right" @change="clear = false" />
        <button type="button" @click="reset">恢复建议金额</button>
        <button v-if="plan.direction === 'SELL'" type="button" @click="clearPosition">预览全部清仓</button>
      </div>
      <p class="execution-hint">规则建议 <b class="execution-accent">{{ plan.direction === 'BUY' ? '加仓' : '减仓约' }} ¥{{ money(plan.suggestedAmount) }}</b><template v-if="plan.direction === 'SELL'"> · {{ plan.suggestedShares.toFixed(4) }} 份</template>。修改仅用于本次预览，不自动下单或记入交易流水。</p>
      <p v-if="!preview" class="execution-warning" role="alert">请输入有效金额；减仓不能超过当前持仓。</p>
      <template v-else>
        <p v-if="preview.changed" class="execution-hint">当前为你的金额调整，原规则建议保持不变。{{ clear ? '已选择全部已确认份额清仓。' : '' }}</p>
        <p v-if="preview.extraCash > 0" class="execution-warning" role="alert">超出该基金现金 ¥{{ money(preview.extraCash) }}，需先在首页补充或重新分配资金；不会自动使用未分配现金。</p>
        <div class="execution-preview">
          <span>预计剩余持仓<strong>¥ {{ money(preview.holdingAfter) }}</strong></span>
          <span>{{ preview.cashAfter < 0 ? '尚需补充资金' : '预计基金现金' }}<strong>¥ {{ money(Math.abs(preview.cashAfter)) }}</strong></span>
          <span v-if="plan.direction === 'SELL'">参考赎回份额<strong>{{ preview.shares.toFixed(4) }} 份</strong></span>
        </div>
      </template>
    </template>
    <div v-else class="execution-wait" role="status">
      <strong>本次不调整</strong>
      <span v-if="plan.status === 'COOLDOWN'">操作间隔 <b>{{ plan.observationsSinceTrade }} / {{ plan.requiredInterval }}</b> 个净值点</span>
      <span v-else-if="plan.pendingTrades">等待 <b>{{ plan.pendingTrades }}</b> 笔交易确认</span>
      <span v-else>具体原因见上方结论</span>
    </div>
    <div class="execution-context"><span>待确认交易 <b>{{ plan.pendingTrades }} 笔</b></span><span v-if="plan.lastTradeDate">最近完成 <b>{{ plan.lastTradeDate }}</b></span><span v-else>暂无已完成交易</span></div>
    <details class="execution-details"><summary>金额估算口径</summary><p>持仓与份额按 {{ plan.navDate }} 正式净值估算；预览未扣实际费用，成交净值及可赎回份额以平台为准。金额基于本次读取的资金快照，交易前请重新分析核对。</p></details>
  </section>
</template>

<style scoped>
.execution-plan { --execution-accent: #ffda7a; margin: 18px 0; padding: 18px; border: 1px solid #3e556b; border-radius: 10px; background: #132231; color: #e2edf8; }
.execution-plan[data-direction=BUY] { --execution-accent: #ff9098; }.execution-plan[data-direction=SELL] { --execution-accent: #70e4b5; }
.execution-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.execution-heading h3 { font-size: 17px; margin: 0; }.execution-heading { margin-bottom: 16px; }.execution-heading > span { color: #b6cce1; font-size: 12px; }
.execution-metrics, .execution-preview { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 12px; }
.execution-metrics > div { padding: 12px; background: #0b1825; border-radius: 7px; border: 1px solid #293e51; }
.execution-metrics span, .execution-preview span { color: #b5c8d9; font-size: 12px; }
.execution-metrics strong, .execution-preview strong { display: block; margin-top: 5px; font-size: 23px; line-height: 1.4; font-weight: 700; color: #f2f7fe; font-variant-numeric: tabular-nums; }
.execution-weight small { color: #a9becf; font-size: 16px; font-weight: 400; }.execution-weight em { color: var(--execution-accent); font-style: normal; }
.execution-accent { color: var(--execution-accent); }
.execution-editor { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 18px; }
.execution-editor :deep(.el-input__wrapper) { background: #233d55; box-shadow: 0 0 0 1px #6b8ba8 inset; }
.execution-editor :deep(.el-input__inner) { color: var(--execution-accent); font-size: 19px; font-weight: 700; font-variant-numeric: tabular-nums; }
.execution-editor :deep(.el-input-number) { width: 175px; }.execution-editor :deep(.el-input__wrapper) { min-height: 38px; }
.execution-editor :deep(.el-input-number__increase), .execution-editor :deep(.el-input-number__decrease) { background: #263d51; color: #dae8f5; }
.execution-editor button { background: #254561; border: 1px solid #6b8ba8; border-radius: 5px; color: #eaf3fc; padding: 6px 9px; cursor: pointer; }
.execution-hint, .execution-context { font-size: 12px; color: #b4c5d6; margin: 12px 0 0; }
.execution-context { display: flex; flex-wrap: wrap; gap: 8px 16px; }
.execution-preview { background: #0d1b28; padding: 12px; border-radius: 6px; margin-top: 12px; }
.execution-warning { color: #ffda7a; background: #2b291f; padding: 10px 12px; border-radius: 6px; }
.execution-wait { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: baseline; gap: 8px; margin-top: 16px; padding: 12px 14px; border-left: 3px solid #ffda7a; background: #29291f; border-radius: 4px; color: #ffda7a; }
.execution-wait strong { font-size: 16px; }.execution-wait span { font-size: 13px; }.execution-wait b { font-size: 19px; font-variant-numeric: tabular-nums; }
.execution-context b { color: #d5e2ee; font-weight: 500; }
.execution-details { margin-top: 12px; padding-top: 10px; border-top: 1px solid #30475b; color: #b4c5d6; font-size: 12px; }.execution-details summary { cursor: pointer; }.execution-details p { margin-bottom: 0; }
.execution-editor button:focus-visible, .execution-details summary:focus-visible { outline: 2px solid #8dccff; outline-offset: 3px; }
@media(max-width:600px) { .execution-plan { padding: 12px; }.execution-metrics, .execution-preview { grid-template-columns: 1fr 1fr; }.execution-weight { grid-column: 1 / -1; }.execution-metrics strong, .execution-preview strong { font-size: 21px; }.execution-editor label { width: 100%; } }
</style>
