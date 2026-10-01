<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import BaseChart from '@/components/charts/BaseChart.vue'
import { navTechnicalApi, type NavBacktestParams, type NavBacktestResult, type NavBacktestSummary } from '@/api/navTechnical'
import { monthsBefore } from '@/utils/fundQuote'
import { shanghaiDateTime, currentTechnicalVersion, currentTechnicalRuleId } from '@/utils/fundTechnicalAdvice'
import type { FundHolding } from '@/types/domain'

const props = defineProps<{ modelValue: boolean; holding: FundHolding | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const today = () => shanghaiDateTime().slice(0, 10)
const params = reactive<NavBacktestParams>({ startDate: monthsBefore(today(), 12), endDate: today(), initialCash: 10000, initialPositionPercent: 100, ruleVersion: currentTechnicalVersion, buyPercent: 50, sellPercent: 50, buyFee: .15, shortSellFee: 1.5, mediumSellFee: .5, sellFee: 0, disclosureDelay: 1, confirmDelay: 1, settlementDelay: 3 })
const busy = ref(false), status = ref(''), error = ref(''), selected = ref(''), tradePage = ref(1)
const result = shallowRef<NavBacktestResult | null>(null), history = ref<NavBacktestSummary[]>([])
let controller: AbortController | undefined, generation = 0
const pct = (v: number | null) => v == null ? '不足一年，不年化' : `${v.toFixed(2)}%`
const money = (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const actionNames: Record<string, string> = { BUY: '买入', REDUCE: '减仓', HOLD: '持有', WATCH: '观望', UNAVAILABLE: '无法分析', INITIAL_BUY: '期初建仓' }
const signalKeys = ['BUY', 'REDUCE', 'HOLD', 'WATCH', 'UNAVAILABLE']
const segmentNames: Record<string, string> = { FULL: '全区间', REFERENCE: '前 70% · 参考段', HOLDOUT: '后 30% · 留出段' }
const compatibleParams = (value: NavBacktestParams) => ({ ...value, initialPositionPercent: value.initialPositionPercent ?? 0, ruleVersion: value.ruleVersion ?? 'NAV-TA v1' })
const signalTradeCount = computed(() => result.value?.metrics.signalTradeCount ?? result.value?.metrics.tradeCount ?? 0)
const noTradeMessage = computed(() => {
  if (!result.value) return ''
  if ((result.value.parameters.initialPositionPercent ?? 0) > 0) return '没有规则触发的成交；收益来自期初模拟持仓。期初建仓不代表规则买入信号。'
  return `期初全现金；买入信号 ${result.value.signalCounts.BUY ?? 0} 日，减仓信号 ${result.value.signalCounts.REDUCE ?? 0} 日。没有可卖份额时减仓不会成交，因此资金仍为现金。零成交不能证明规则有效。`
})
const comparisonVerdict = computed(() => {
  const c = result.value?.comparison
  if (!c) return ''
  if (c.status !== 'AVAILABLE') return '区间过短，无法形成至少 40 个观察日的后段验证；仅展示全区间结果。'
  const tail = c.results.filter(r => r.segment === 'HOLDOUT')
  if (tail.some(r => r.validationStatus !== 'SIMULATED')) return '后段存在无法分析的规则版本，尚不能证明候选规则更优。'
  if (tail.some(r => (r.metrics.signalTradeCount ?? r.metrics.tradeCount) < 3)) return '后段交易样本不足（至少一个版本少于 3 笔规则成交），不能凭零回撤或全区间高收益认定改进有效。'
  const active = tail.find(r => r.ruleVersion === currentTechnicalVersion)
  const original = tail.find(r => r.ruleVersion === (active ? 'NAV-TA v2-balanced' : 'NAV-TA v1')), candidate = active || tail.find(r => r.ruleVersion === 'NAV-TA v2-balanced')
  if (original && candidate && candidate.metrics.totalReturn > original.metrics.totalReturn && candidate.metrics.maxDrawdown >= original.metrics.maxDrawdown) return '候选规则在该基金后段取得更高收益且回撤未扩大；这只是单区间对照，仍需更多基金与时间区间验证。'
  return '候选规则在后段未同时改善收益和回撤，此处对照不改变已启用的今日推荐版本。'
})
const historicalReason = (reason: string) => reason.replace('刷新后再分析。', '该历史观察日暂停分析。').replace(/今日/g, '该历史观察日')
const conditionNames: Record<string, string> = { trendConfirmed: '进攻趋势确认', defenseConfirmed: '防守 / 回撤刹车', recoveryConfirmed: '刹车后恢复确认', buyTrend: '买入 · 均线趋势', buyMomentum: '买入 · MACD', buyWeek: '买入 · 周线确认', buyFilter: '买入 · RSI / 乖离', sellTrend: '减仓 · 均线趋势', sellMomentum: '减仓 · MACD', sellWeek: '减仓 · 周线确认' }
const trades = computed(() => result.value?.trades.slice((tradePage.value - 1) * 20, tradePage.value * 20) || [])
const changed = computed(() => result.value && Object.keys(params).some(k => params[k as keyof NavBacktestParams] !== compatibleParams(result.value!.parameters)[k as keyof NavBacktestParams]))
function stop() { controller?.abort(); generation++; busy.value = false }
async function perform(task: (id: number, signal: AbortSignal) => Promise<void>, message: string) {
  stop(); const id = props.holding?.id
  if (!id || !props.modelValue) return
  controller = new AbortController(); const signal = controller.signal, version = generation
  busy.value = true; status.value = message; error.value = ''
  try { await task(id, signal) } catch (e) { if (!signal.aborted && version === generation) error.value = e instanceof Error ? e.message : '回测请求失败，请重试。' }
  finally { if (version === generation) busy.value = false }
}
function accept(value: NavBacktestResult) { result.value = value; selected.value = value.id; tradePage.value = 1 }
function restoreParams(value: NavBacktestParams) { const compatible = compatibleParams(value); for (const key of Object.keys(params) as Array<keyof NavBacktestParams>) Object.assign(params, { [key]: compatible[key] }) }
async function loadHistory() {
  await perform(async (id, signal) => {
    const list = await navTechnicalApi.history(id, signal)
    if (signal.aborted) return
    history.value = list

  }, '正在读取已保存的回测…')
}
function resetApproved() {
  Object.assign(params, { ruleVersion: currentTechnicalVersion, initialCash: 10000, initialPositionPercent: 100, buyPercent: 50, sellPercent: 50, buyFee: .15, shortSellFee: 1.5, mediumSellFee: .5, sellFee: 0, confirmDelay: 1, settlementDelay: 3, disclosureDelay: /QDII/i.test(`${props.holding?.fundType} ${props.holding?.fundName}`) ? 2 : 1 })
}
async function selectHistory() {
  const target = selected.value
  await perform(async (id, signal) => {
    const value = await navTechnicalApi.result(id, target, signal)
    if (!signal.aborted) { accept(value); restoreParams(value.parameters) }
  }, '正在读取回测结果…')
}
async function run() {
  if (params.startDate >= params.endDate || params.endDate > today()) { error.value = '请选择有效的历史区间，截止日不能在未来。'; return }
  const snapshot = { ...params }
  await perform(async (id, signal) => {
    const value = await navTechnicalApi.run(id, snapshot, signal)
    if (signal.aborted) return
    accept(value)
    history.value = [{ id: value.id, createdAt: value.createdAt, ruleVersion: value.ruleVersion }, ...history.value].slice(0, 30)
  }, '正在读取历史净值、逐日模拟并保存结果，请稍候…')
}
watch([() => props.modelValue, () => props.holding?.id], () => {
  stop(); result.value = null; history.value = []; selected.value = ''; error.value = ''
  if (props.modelValue) {
    resetApproved();
    Object.assign(params, { startDate: monthsBefore(today(), 12), endDate: today(), disclosureDelay: /QDII/i.test(`${props.holding?.fundType} ${props.holding?.fundName}`) ? 2 : 1 })
    void loadHistory()
  }
}, { immediate: true })
onBeforeUnmount(stop)
function chart(drawdown = false) {
  const points = result.value?.curve || [], colors = ['#86c6ff', '#f5d178']
  return { backgroundColor: '#0d1722', color: colors, animation: false,
    tooltip: { trigger: 'axis', valueFormatter: (v: number) => `${Number(v).toFixed(2)}%` },
    legend: { top: 8, textStyle: { color: '#e0ebf6', fontSize: 13 }, data: ['NAV-TA 规则', '买入持有'] },
    grid: { left: 64, right: 24, top: 46, bottom: 35 },
    xAxis: { type: 'category', data: points.map(p => p.date), boundaryGap: false, axisLabel: { color: '#b7cadd', hideOverlap: true, showMaxLabel: false } },
    yAxis: { type: 'value', axisLabel: { color: '#b7cadd', formatter: '{value}%' }, splitLine: { lineStyle: { color: '#293b4e', type: 'dashed' } } },
    series: ['NAV-TA 规则', '买入持有'].map((name, i) => ({ name, type: 'line', showSymbol: false, lineStyle: { width: i ? 2 : 3 }, data: points.map(p => drawdown ? i ? p.benchmarkDrawdown : p.drawdown : i ? p.benchmarkReturnRate : p.returnRate) })) }
}
const returnsOption = computed(() => chart()), drawdownOption = computed(() => chart(true))
</script>

<template>
  <el-dialog v-model="open" title="规则历史回测" class="nav-backtest-dialog" width="min(1280px, 96vw)" top="3vh" append-to-body destroy-on-close :close-on-click-modal="false">
    <template #header><div class="backtest-heading"><span>NAV-TA · 历史模拟</span><h2>规则历史回测 <small>{{ holding?.fundName }} {{ holding?.fundCode }}</small></h2></div></template>
    <div class="backtest-body" :aria-busy="busy">
      <form @submit.prevent="run">
        <fieldset :disabled="busy" class="backtest-params">
          <label>开始日期<input v-model="params.startDate" type="date" required :max="today()"></label>
          <label>结束日期<input v-model="params.endDate" type="date" required :max="today()"></label>
          <label>初始资金（元）<input v-model.number="params.initialCash" type="number" min="100" max="100000000" required></label>
          <label>回测规则<select v-model="params.ruleVersion" aria-label="回测规则"><option value="NAV-TA v1">v1 · 原规则</option><option value="NAV-TA v2-balanced">v2 · 平衡候选（实验）</option><option value="NAV-TA v3.1-trend">v3.1 · 趋势恢复（当前推荐）</option></select></label>
          <label>期初模拟建仓比例 %<input v-model.number="params.initialPositionPercent" type="number" min="0" max="100" step="1" required></label>
          <label>每次买入现金比例 %<input v-model.number="params.buyPercent" type="number" min="0.01" max="100" step="0.01" required></label>
          <label>每次减仓份额比例 %<input v-model.number="params.sellPercent" type="number" min="0.01" max="100" step="0.01" required></label>
        </fieldset>
        <p class="backtest-meta">期初比例 0% 表示全现金；大于 0% 时按起点净值模拟建仓、扣费并计算确认期。选择 v3.1 时同时对照 v1 / v2 / v3.1，不修改实际持仓或今日推荐规则。</p>
        <p v-if="params.ruleVersion === currentTechnicalVersion" class="backtest-message">{{ currentTechnicalRuleId }} · MA70 ±2% · 3 点确认 · 防守目标 20% · 回撤刹车 18% · MA20 恢复确认 · 调仓偏离 &gt;5 个百分点 · 成交间隔 ≥3 点。买卖比例为单次上限。</p>
        <button type="button" :disabled="busy" @click="resetApproved">使用已审核 v3.1 参数</button>
        <details class="backtest-details"><summary>费用与成交假设 · 运行前请核对</summary><p>下列费率是可编辑的模拟假设，不是该基金实际费率。延迟按净值观察日计；QDII 披露延迟至少 2 日。</p>
          <fieldset :disabled="busy" class="backtest-params">
            <label>申购费 %<input v-model.number="params.buyFee" type="number" min="0" max="5" step="0.01" required></label>
            <label>持有不足 7 天赎回费 %<input v-model.number="params.shortSellFee" type="number" min="0" max="5" step="0.01" required></label>
            <label>持有 7–29 天赎回费 %<input v-model.number="params.mediumSellFee" type="number" min="0" max="5" step="0.01" required></label>
            <label>持有 ≥30 天赎回费 %<input v-model.number="params.sellFee" type="number" min="0" max="5" step="0.01" required></label>
            <label>净值披露延迟<input v-model.number="params.disclosureDelay" type="number" min="1" max="5" required></label>
            <label>申购确认延迟<input v-model.number="params.confirmDelay" type="number" min="1" max="10" required></label>
            <label>赎回到账延迟<input v-model.number="params.settlementDelay" type="number" min="1" max="20" required></label>
          </fieldset>
        </details>
        <div class="backtest-toolbar"><button type="submit" :disabled="busy">{{ busy ? '处理中…' : '运行并保存回测' }}</button><label>历史结果<select aria-label="历史结果" v-model="selected" :disabled="busy || !history.length" @change="selectHistory"><option value="" disabled>{{ history.length ? '选择历史结果' : '暂无历史结果' }}</option><option v-for="item in history" :key="item.id" :value="item.id">{{ item.createdAt }} · {{ item.ruleVersion }}</option></select></label></div>
      </form>
      <p v-if="busy" role="status" class="backtest-message">{{ status }}</p>
      <p v-if="error" role="alert" class="backtest-error">{{ error }}</p>
      <template v-if="result">
        <p class="backtest-meta">{{ result.ruleVersion }} · {{ result.ruleId || '' }} · {{ result.engineVersion }} · 实际区间 {{ result.startDate }} — {{ result.endDate }} · 保存于 {{ result.createdAt }}</p>
        <p v-if="changed" class="backtest-error">参数已改变，下方仍是上次保存结果。重新运行后才会更新。</p>
        <div class="backtest-metrics">
          <article><span>策略收益 · 扣已发生费用</span><strong>{{ pct(result.metrics.totalReturn) }}</strong><small>买入持有 {{ pct(result.metrics.benchmarkReturn) }}</small></article>
          <article><span>最大回撤</span><strong>{{ pct(result.metrics.maxDrawdown) }}</strong><small>买入持有 {{ pct(result.metrics.benchmarkMaxDrawdown) }}</small></article>
          <article><span>相对买入持有</span><strong>{{ pct(result.metrics.excessReturn) }}</strong><small>年化 {{ pct(result.metrics.annualReturn) }}</small></article>
          <article><span>成交次数 / 总费用</span><strong>{{ result.metrics.tradeCount }} 次</strong><small>¥ {{ money(result.metrics.totalFees) }} · 平均仓位 {{ pct(result.metrics.exposure) }}</small></article>
        </div>
        <p v-if="result.validationStatus === 'NO_ELIGIBLE_DAYS'" class="backtest-error">本区间没有满足数据门槛的分析日，不能验证规则有效性。曲线仅反映初始资金配置与基准；请查看“数据阻断与未成交原因”。</p>
        <p v-else-if="signalTradeCount === 0" class="backtest-message">{{ noTradeMessage }}</p>
        <p v-if="result.metrics.initialTradeCount" class="backtest-meta">成交包含 {{ result.metrics.initialTradeCount }} 笔期初建仓，规则触发 {{ signalTradeCount }} 笔。</p>
        <section v-if="result.comparison" class="backtest-comparison"><h3>规则对照 · 收益与回撤平衡</h3><p class="backtest-message">{{ comparisonVerdict }}</p><p>{{ result.comparison.adoption }}</p>
          <div class="backtest-table"><table><thead><tr><th>区间 / 版本</th><th>扣费收益</th><th>最大回撤</th><th>买入持有收益 / 回撤</th><th>规则成交</th><th>可分析日</th></tr></thead><tbody><tr v-for="row in result.comparison.results" :key="row.segment + row.ruleVersion"><td>{{ segmentNames[row.segment] }} · {{ row.ruleVersion }}<br><small>{{ row.startDate }} — {{ row.endDate }}</small></td><td>{{ pct(row.metrics.totalReturn) }}</td><td>{{ pct(row.metrics.maxDrawdown) }}</td><td>{{ pct(row.metrics.benchmarkReturn) }} / {{ pct(row.metrics.benchmarkMaxDrawdown) }}</td><td>{{ row.metrics.signalTradeCount ?? row.metrics.tradeCount }}</td><td>{{ row.metrics.analyzedDays }} / {{ row.metrics.totalDays }}<small v-if="row.validationStatus !== 'SIMULATED'"> · 无法验证</small></td></tr></tbody></table></div>
          <details class="backtest-details"><summary>候选规则与验证方法</summary><p>{{ result.comparison.candidate }}</p><p>{{ result.comparison.method }}</p><p>QDII 的改动同时涉及披露门槛，原版若无可分析日，不能把候选版收益归因于交易规则更优。3 笔仅用于提示样本过少，不代表统计显著。</p></details>
        </section>
        <p v-else class="backtest-meta">这是旧版保存结果，重新运行后可查看规则对照与后段验证。</p>
        <section class="backtest-chart"><h3>累计收益对比 %</h3><BaseChart :option="returnsOption" :height="310" /></section>
        <section class="backtest-chart"><h3>回撤对比 %</h3><BaseChart :option="drawdownOption" :height="190" /></section>
        <section><h3>信号与未触发条件</h3><p>可分析 {{ result.metrics.analyzedDays }} / {{ result.metrics.totalDays }} 个观察日。各条件可能同时未满足，次数不能相加作为天数。</p><div class="backtest-signals"><span v-for="key in signalKeys" :key="key">{{ actionNames[key] }} <b>{{ result.signalCounts[key] ?? 0 }}</b></span></div>
          <div class="backtest-condition-grid"><div v-for="(count, key) in result.unmetConditions" :key="key">{{ conditionNames[key] || key }}<b>{{ count }} 日未满足</b></div></div>
          <details v-if="Object.keys(result.blockedReasons).length || Object.keys(result.skippedTrades).length" class="backtest-details"><summary>数据阻断与未成交原因</summary><p v-for="(count, reason) in result.blockedReasons" :key="reason">{{ historicalReason(String(reason)) }} · {{ count }} 次</p><p v-for="(count, reason) in result.skippedTrades" :key="reason">{{ historicalReason(String(reason)) }} · {{ count }} 次</p></details>
        </section>
        <section><h3>交易明细 <small>{{ result.trades.length }} 笔</small></h3><div class="backtest-table"><table><thead><tr><th>信号日 / 净值依据日</th><th>模拟成交日</th><th>动作</th><th>金额 / 费用</th><th>成交单位净值</th><th>触发依据</th></tr></thead><tbody><tr v-for="(trade, i) in trades" :key="i"><td>{{ trade.signalDate }}<br><small>依据 {{ trade.navAsOf || '期初假设' }}</small></td><td>{{ trade.executionDate }}<br><small v-if="trade.action === 'REDUCE'">到账 {{ trade.settlementDate || '区间结束后' }}</small></td><td>{{ actionNames[trade.action] }}</td><td>{{ money(trade.amount) }}<br><small>费用 {{ money(trade.fee) }}</small></td><td>{{ trade.unitNav.toFixed(4) }}</td><td><details><summary>查看依据</summary><p>{{ trade.reason }}</p><p v-for="e in trade.evidence" :key="e.label">{{ e.label }}：{{ e.value }}。{{ e.explanation }}</p></details></td></tr><tr v-if="!trades.length"><td colspan="6">无成交记录</td></tr></tbody></table></div><div v-if="result.trades.length > 20" class="backtest-toolbar"><button :disabled="tradePage === 1" @click="tradePage--">上一页</button><span>{{ tradePage }} / {{ Math.ceil(result.trades.length / 20) }}</span><button :disabled="tradePage * 20 >= result.trades.length" @click="tradePage++">下一页</button></div></section>
        <details class="backtest-details"><summary>本次参数、数据来源与模拟限制</summary><p>初始资金 {{ result.parameters.initialCash }} 元；买入 {{ result.parameters.buyPercent }}%；减仓 {{ result.parameters.sellPercent }}%；申购费 {{ result.parameters.buyFee }}%；赎回费（不足 7 天 / 7–29 天 / ≥30 天）{{ result.parameters.shortSellFee }}% / {{ result.parameters.mediumSellFee }}% / {{ result.parameters.sellFee }}%。</p><p v-for="note in result.assumptions" :key="note">{{ note }}</p><p>来源 {{ result.source }} · 数据指纹 {{ result.dataHash }}</p></details>
      </template>
      <p v-else-if="!busy && !error" class="backtest-empty">选择区间后运行回测。起点之前需至少 120 个净值点预热；结果将保存，可在此处重新查看。</p>
    </div>
    <template #footer><span class="backtest-footer-note">历史模拟不会修改实际持仓、交易或原量化策略。</span><button type="button" class="backtest-close" @click="open = false">返回今日推荐</button></template>
  </el-dialog>
</template>

<style>
.nav-backtest-dialog.el-dialog { background:#101c28; color:#dce8f4; border:1px solid #58748d; border-radius:12px; padding:22px; --el-text-color-primary:#edf4fb; }
.nav-backtest-dialog .el-dialog__body { padding:0; color:#dce8f4; --el-text-color-regular:#dce8f4; --el-text-color-secondary:#b8cadd; }
.nav-backtest-dialog .el-dialog__close { color:#dce8f4; }
.backtest-heading span,.backtest-meta,.backtest-footer-note { color:#b8cadd; font-size:13px; }
.backtest-heading h2 { margin:6px 0 16px; font-size:23px; color:#f0f6ff; }
.backtest-heading small { font-size:15px; font-weight:500; margin-left:12px; }
.backtest-body { max-height:calc(94dvh - 155px); overflow:auto; padding-right:10px; font:14px/1.6 "Segoe UI","Microsoft YaHei",sans-serif; scrollbar-color:#58748d transparent; }
.backtest-params { border:0; padding:0; margin:0; display:grid; grid-template-columns:repeat(auto-fit,minmax(165px,1fr)); gap:12px; }
.backtest-params label { display:flex; flex-direction:column; gap:5px; color:#cddfed; }
.backtest-body input,.backtest-body select,.backtest-body button,.backtest-close { background:#22364a; color:#eef5fc; border:1px solid #57748d; border-radius:6px; padding:8px 10px; font:inherit; min-width:0; color-scheme:dark; }
.backtest-body button,.backtest-close { cursor:pointer; }
.backtest-body button:disabled,.backtest-body select:disabled { opacity:.55; cursor:default; }
.backtest-toolbar { display:flex; align-items:center; flex-wrap:wrap; gap:14px; margin:16px 0; }
.backtest-toolbar label { display:flex; align-items:center; flex-wrap:wrap; gap:8px; }
.backtest-details { margin:14px 0; padding:12px; border:1px solid #395269; border-radius:6px; overflow-wrap:anywhere; }
.backtest-details summary,td summary { cursor:pointer; color:#b5dcff; }
.backtest-message { border-left:3px solid #83caff; padding:10px; background:#1d3143; }
.backtest-error { color:#ffd18b; padding:10px; background:#332d24; border-radius:6px; }
.backtest-metrics { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; }
.backtest-metrics article { padding:14px; background:#1a2d40; border:1px solid #3a546d; border-radius:8px; display:flex; flex-direction:column; gap:5px; }
.backtest-metrics strong { color:#edf6ff; font-size:25px; }
.backtest-metrics article > span,.backtest-toolbar > label { color:#c4d9eb; }
.backtest-metrics small,.backtest-body td small { color:#b8ccde; }
.backtest-chart { border:1px solid #31495e; background:#0d1722; border-radius:8px; margin:16px 0; overflow:hidden; }
.backtest-body h3 { color:#edf5fc; margin:18px 0 8px; font-size:16px; }
.backtest-chart h3 { margin:12px 18px 0; }
.backtest-signals { display:flex; gap:12px; flex-wrap:wrap; }
.backtest-signals span { background:#243c52; color:#e5f1fc; padding:6px 12px; border-radius:5px; }
.backtest-condition-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(245px,1fr)); gap:8px; margin-top:12px; }
.backtest-condition-grid div { display:flex; justify-content:space-between; gap:12px; padding:8px; border-bottom:1px solid #334c62; }
.backtest-condition-grid b { color:#f2cd8c; }
.backtest-table { overflow:auto; }
.backtest-table table { border-collapse:collapse; width:100%; min-width:780px; text-align:left; }
.backtest-table td,.backtest-table th { padding:10px; border-bottom:1px solid #31495e; vertical-align:top; }
.backtest-table th { background:#203447; color:#deecf8; }
.backtest-table td:last-child { max-width:330px; }
.backtest-empty { text-align:center; padding:70px 15px; color:#c0d4e6; }
.backtest-footer-note { margin-right:15px; }
@media(max-width:700px) { .nav-backtest-dialog.el-dialog { padding:14px; }.backtest-metrics { grid-template-columns:repeat(2,1fr); }.backtest-heading small { display:block; margin:5px 0; }.backtest-body { max-height:calc(94dvh - 205px); }.backtest-footer-note { display:block; margin:5px 0; }.backtest-toolbar select { max-width:100%; } }
</style>

