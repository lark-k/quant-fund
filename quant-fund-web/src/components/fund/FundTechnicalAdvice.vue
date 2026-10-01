<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { navTechnicalApi } from '@/api/navTechnical'
import FundRuleBacktest from './FundRuleBacktest.vue'
import { USE_MOCK } from '@/api/http'
import { shanghaiDateTime, technicalRules, currentTechnicalVersion, type TechnicalAdvice } from '@/utils/fundTechnicalAdvice'
import type { FundHolding } from '@/types/domain'

const props = defineProps<{ modelValue: boolean; holding: FundHolding | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const loading = ref(false), error = ref('')
const backtestOpen = ref(false)
const advice = shallowRef<TechnicalAdvice | null>(null)
const clock = ref(Date.now()), evaluatedMs = ref(0)
const expired = computed(() => !!advice.value && (clock.value - evaluatedMs.value >= 15 * 60 * 1000 || shanghaiDateTime(new Date(clock.value)).slice(0, 10) !== advice.value.evaluatedAt.slice(0, 10)))
let controller: AbortController | undefined, requestId = 0
let timer: ReturnType<typeof setInterval> | undefined
function updateClock() { clock.value = Date.now() }
function stop() { controller?.abort(); requestId++; loading.value = false; clearInterval(timer); timer = undefined; document.removeEventListener('visibilitychange', updateClock) }
async function analyze() {
  controller?.abort()
  const id = ++requestId, code = props.holding?.fundCode
  if (!props.modelValue || !code) return
  controller = new AbortController()
  const signal = controller.signal
  loading.value = true; error.value = ''; advice.value = null
  try {
    const result = await navTechnicalApi.analyze(props.holding!.id, signal)
    if (signal.aborted || id !== requestId) return
    const now = new Date()
    advice.value = result
    evaluatedMs.value = now.getTime(); updateClock()
  } catch (e) {
    if (!signal.aborted && id === requestId) error.value = e instanceof Error ? e.message : '技术分析服务读取失败，暂不提供建议。请重试。'
  } finally {
    if (id === requestId) loading.value = false
  }
}
watch([() => props.modelValue, () => props.holding?.fundCode, () => props.holding?.id], () => {
  stop()
  backtestOpen.value = false
  if (props.modelValue) {
    void analyze()
    timer = setInterval(updateClock, 30000)
    document.addEventListener('visibilitychange', updateClock)
  } else advice.value = null
}, { immediate: true })
onBeforeUnmount(stop)
</script>

<template>
  <el-dialog v-model="open" title="今日交易推荐" class="fund-technical-dialog" width="min(900px, 94vw)" top="4vh" append-to-body destroy-on-close :close-on-click-modal="false">
    <template #header><div class="technical-heading"><span>正式净值 · 技术分析参考</span><h2>今日交易推荐</h2><p>{{ holding?.fundName }} <span>{{ holding?.fundCode }}</span></p></div></template>
    <div class="technical-body" :aria-busy="loading">
      <p v-if="USE_MOCK" class="technical-alert">演示模式：使用模拟数据，不对应真实基金建议。</p>
      <div v-if="loading" class="technical-loading" role="status">正在读取正式净值，核对 MA70 趋势、回撤刹车与恢复条件…</div>
      <p v-else-if="error" class="technical-alert" role="alert">{{ error }}</p>
      <template v-else-if="advice">
        <div class="technical-result" :data-action="expired ? 'UNAVAILABLE' : advice.action">
          <span class="technical-kicker">{{ advice.evaluatedAt.slice(0, 10) }} · {{ expired ? '结果待更新' : '技术规则参考 · 可查看历史回测' }}</span>
          <h3>{{ expired ? '结果已过期，请重新分析' : advice.title }}</h3>
          <p>{{ expired ? '结果已超过 15 分钟或日期已变化。点击重新分析，读取最新正式净值后再判断。' : advice.explanation }}</p>
        </div>
        <div class="technical-meta"><span>规则 <b>{{ advice.ruleVersion || 'NAV-TA v1' }}</b></span><span v-if="advice.ruleId">{{ advice.ruleId }}</span><span>净值截至 <b>{{ advice.asOf }}</b></span><span>分析样本 <b>{{ advice.sampleCount }} 个净值点</b></span><span>生成于 {{ advice.evaluatedAt }}（北京时间）</span></div>
        <ul v-if="advice.blockers.length" class="technical-blockers"><li v-for="reason in advice.blockers" :key="reason">{{ reason }}</li></ul>
        <template v-else>
          <h3 class="technical-section-title">判断依据</h3>
          <div class="technical-evidence">
            <article v-for="item in advice.evidence" :key="item.label">
              <div><h4>{{ item.label }}</h4><span :class="`technical-tone-${item.tone}`">{{ item.tone === 'bull' ? '偏强' : item.tone === 'bear' ? '偏弱' : '观察' }}</span></div>
              <p class="technical-values">{{ item.value }}</p><p>{{ item.explanation }}</p>
            </article>
          </div>
          <div class="technical-notes"><p v-for="note in advice.notes" :key="note">{{ note }}</p></div>
        </template>
        <p class="technical-source">数据来源：{{ advice.source }}</p>
      </template>
      <details class="technical-method"><summary>计算口径与判断规则 · {{ advice?.ruleVersion || currentTechnicalVersion }}</summary><ol><li v-for="rule in (advice?.rules || (advice && advice.ruleVersion !== currentTechnicalVersion ? technicalRules : []))" :key="rule">{{ rule }}</li></ol><p>日线、均线、MACD、RSI 采用前复权口径。主图若选单位净值或不同周期，数值可能不同。本弹窗只输出建议，不写入策略信号或交易记录。</p><p>指标学习：<a href="https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/macd" target="_blank" rel="noopener noreferrer">MACD</a> · <a href="https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/rsi" target="_blank" rel="noopener noreferrer">RSI</a></p></details>
    </div>
    <template #footer><div class="technical-footer"><span>场外基金按适用开放日净值确认，申赎时间与费用以平台为准。</span><button type="button" @click="backtestOpen = true">回测此规则</button><button type="button" :disabled="loading" @click="analyze">{{ loading ? '分析中…' : '重新分析' }}</button><button type="button" @click="open = false">返回图表</button></div></template>
  </el-dialog>
  <FundRuleBacktest v-model="backtestOpen" :holding="holding" />
</template>

<style>
.fund-technical-dialog.el-dialog { background: #101c28; color: #dce8f4; border: 1px solid #58748d; border-radius: 12px; padding: 22px; --el-text-color-primary: #edf4fb; --el-text-color-regular: #dce8f4; }
.fund-technical-dialog .el-dialog__header { padding: 0 32px 15px 0; }
.fund-technical-dialog .el-dialog__body { padding: 0; }
.fund-technical-dialog .el-dialog__close { color: #dce8f4; }
.technical-heading > span, .technical-heading p span { color: #b4c7d8; font-size: 13px; }
.technical-heading h2 { color: #edf4fb; font-size: 22px; margin: 4px 0; }
.technical-heading p { color: #dce8f4; font-size: 15px; margin: 0; }
.technical-body { color: #dce8f4; max-height: calc(92dvh - 210px); overflow: auto; padding-right: 8px; font: 14px/1.65 "Segoe UI", "Microsoft YaHei", sans-serif; scrollbar-width: thin; scrollbar-color: #58748d transparent; }
.technical-result { border: 1px solid #537c9f; border-left: 4px solid #8dccff; background: #193047; padding: 16px 20px; border-radius: 8px; }
.technical-result[data-action=BUY] { border-left-color: #ff858b; }
.technical-result[data-action=REDUCE] { border-left-color: #57dfb3; }
.technical-result[data-action=UNAVAILABLE], .technical-result[data-action=WATCH] { border-left-color: #f2cf7c; background: #25303b; }
.technical-kicker { color: #c1d4e6; font-size: 13px; }
.technical-result h3 { margin: 5px 0; font-size: 24px; color: #f2f7fc; }
.technical-result p { margin: 5px 0 0; }
.technical-meta { display: flex; flex-wrap: wrap; gap: 6px 18px; margin: 12px 0 18px; color: #b4c7d8; font-size: 13px; }
.technical-meta b { color: #edf4fb; font-weight: 500; }
.technical-section-title { font-size: 16px; margin: 16px 0 8px; color: #edf4fb; }
.technical-evidence { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.technical-evidence article { background: #0b1520; border: 1px solid #3b5267; border-radius: 7px; padding: 13px; }
.technical-evidence article > div { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.technical-evidence h4 { margin: 0; color: #edf4fb; font-size: 14px; }
.technical-evidence article > div span { white-space: nowrap; font-size: 13px; }
.technical-tone-bull { color: #ff858b; } .technical-tone-bear { color: #57dfb3; } .technical-tone-neutral { color: #f2cf7c; }
.technical-evidence p { color: #b4c7d8; margin: 7px 0 0; }
.technical-evidence p.technical-values { color: #e0edf9; font-variant-numeric: tabular-nums; }
.technical-notes { border-left: 2px solid #58748d; padding-left: 12px; margin-top: 18px; color: #b4c7d8; font-size: 13px; }
.technical-notes p { margin: 5px 0; }
.technical-source { color: #b4c7d8; font-size: 12px; }
.technical-blockers { padding-left: 24px; color: #f2cf7c; }
.technical-blockers li { margin: 8px 0; }
.technical-alert { padding: 12px; color: #f2cf7c; background: #303238; border-radius: 6px; }
.technical-loading { padding: 50px 16px; text-align: center; color: #c1d4e6; }
.technical-method { border-top: 1px solid #3b5267; margin-top: 16px; padding-top: 12px; color: #b4c7d8; font-size: 13px; }
.technical-method summary { cursor: pointer; color: #dce8f4; }
.technical-method li { margin: 8px 0; }
.technical-method a { color: #8dccff; }
.technical-footer { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.technical-footer span { flex: 1; text-align: left; color: #b4c7d8; font-size: 12px; }
.technical-footer button { padding: 8px 12px; color: #edf4fb; border: 1px solid #58748d; border-radius: 5px; background: #214461; cursor: pointer; }
.fund-technical-dialog button:focus-visible, .technical-method summary:focus-visible { outline: 2px solid #8dccff; outline-offset: 3px; }
@media (max-width: 600px) { .fund-technical-dialog.el-dialog { padding: 16px 12px; } .technical-evidence { grid-template-columns: 1fr; } .technical-footer span { flex-basis: 100%; } .technical-body { color: #dce8f4; max-height: calc(92dvh - 235px); } }
</style>
