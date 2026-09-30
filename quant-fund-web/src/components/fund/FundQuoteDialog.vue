<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import FundQuoteChart from '@/components/charts/FundQuoteChart.vue'
import FundQuoteGuide from './FundQuoteGuide.vue'
import FundTechnicalAdvice from './FundTechnicalAdvice.vue'
import { formatDateTime } from '@/utils/format'
import { fundQuoteApi } from '@/api/fundQuote'
import { dateBefore, matchingTrades, mergeHistory, monthsBefore, quotePoints, type QuoteBasis, type QuoteNav, type QuotePeriod } from '@/utils/fundQuote'
import { buildQuoteChart, tradeLabel } from '@/utils/fundQuoteChart'
import type { FundBasicInfo, FundEstimate, FundHolding, TradeRecord } from '@/types/domain'

const props = defineProps<{ modelValue: boolean; holding: FundHolding | null; holdings: FundHolding[] }>()
// Keep the viewing session stable while the dashboard replaces its polled holdings.
// This snapshot is renewed only by an explicit load, reopening, or fund selection.
const viewedHolding = shallowRef<FundHolding | null>(null)
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; select: [holding: FundHolding] }>()
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const selectedId = computed({ get: () => props.holding?.id, set: id => { const h = props.holdings.find(h => h.id === id); if (h) emit('select', h) } })
const period = ref<QuotePeriod>('day')
const basis = ref<QuoteBasis>('unit')
const range = ref('6M')
const averages = ref([5, 10, 20, 60])
const showCost = ref(true), showTrades = ref(true), showGrowth = ref(true), showDrawdown = ref(false)
const indicator = ref<'none' | 'MACD' | 'RSI'>('none')
const indexCode = ref('')
const fullscreen = ref(false)
const guideOpen = ref(false)
const adviceOpen = ref(false)
const rows = ref<QuoteNav[]>([]), trades = ref<TradeRecord[]>([])
const info = ref<FundBasicInfo | null>(null), estimate = ref<FundEstimate | null>(null)
const loading = ref(false), extending = ref(false), progress = ref(''), error = ref(''), warnings = ref<string[]>([])
const loadedAt = ref(''), coverageStart = ref(''), selectedTrade = ref<TradeRecord | null>(null)
let controller: AbortController | undefined
let requestId = 0
const ranges = [{ value: '1M', label: '近1月', months: 1 }, { value: '3M', label: '近3月', months: 3 },
  { value: '6M', label: '近6月', months: 6 }, { value: '1Y', label: '近1年', months: 12 }, { value: 'ALL', label: '成立以来', months: 0 }]
const indices = [{ value: '000300', label: '沪深300' }, { value: '399006', label: '创业板指' }, { value: '000001', label: '上证指数' }, { value: '399001', label: '深证成指' }]
const indexName = computed(() => indices.find(i => i.value === indexCode.value)?.label || '参考指数')
const trackingIndex = computed(() => indices.find(i => [i.value, i.label, `${i.label}指数`].includes(info.value?.trackingIndex?.trim() || '')))
function today() { return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date()) }
const startDate = computed(() => range.value === 'ALL' ? '1990-01-01' : monthsBefore(today(), ranges.find(r => r.value === range.value)!.months))
const points = computed(() => quotePoints(rows.value, basis.value))
const latest = computed(() => rows.value[rows.value.length - 1])
const cost = computed(() => viewedHolding.value && viewedHolding.value.holdingShare > 0 && viewedHolding.value.holdingCost > 0 ? viewedHolding.value.holdingCost / viewedHolding.value.holdingShare : null)
const completedTrades = computed(() => viewedHolding.value ? matchingTrades(trades.value, viewedHolding.value) : [])
const visibleTrades = computed(() => completedTrades.value.filter(t => t.tradeTime.slice(0, 10) >= startDate.value).sort((a, b) => b.tradeTime.localeCompare(a.tradeTime)))
const chart = computed(() => buildQuoteChart({ points: points.value, period: period.value, startDate: startDate.value,
  averages: averages.value, cost: showCost.value ? cost.value : null, trades: showTrades.value ? visibleTrades.value : [],
  growth: showGrowth.value, drawdown: showDrawdown.value, indicator: indicator.value, compare: !!indexCode.value,
  indexName: indexName.value, adjusted: basis.value === 'adjusted' }))
const hasVisibleData = computed(() => points.value.some(p => p.date >= startDate.value && p.value !== null))
const adjustedMissing = computed(() => basis.value === 'adjusted' && points.value.some(p => p.date >= startDate.value && p.value === null))
const gapCount = computed(() => rows.value.slice(1).filter((p, i) => new Date(p.date).getTime() - new Date(rows.value[i]!.date).getTime() > 14 * 86400000).length)
const isDelayed = computed(() => latest.value && latest.value.date < dateBefore(today(), 4))
const fmt = (v: number | null | undefined, digits = 2) => v == null || !Number.isFinite(v) ? '--' : v.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
const pct = (v: number | null | undefined) => v == null ? '--' : `${v > 0 ? '+' : ''}${fmt(v)}%`
const tone = (v: number | null | undefined) => v == null || v === 0 ? '' : v > 0 ? 'quote-rise' : 'quote-fall'
function selectTrade(id: number) { selectedTrade.value = visibleTrades.value.find(t => t.id === id) || null }

async function extendHistory(id = requestId, signal = controller?.signal) {
  if (!signal || signal.aborted || !props.holding || extending.value || !coverageStart.value) return
  extending.value = true
  const code = props.holding.fundCode
  const target = info.value?.establishDate?.slice(0, 10) || '1998-01-01'
  try {
    while (range.value === 'ALL' && coverageStart.value > target && id === requestId && !signal.aborted) {
      const end = dateBefore(coverageStart.value, -14)
      const start = [monthsBefore(coverageStart.value, 12), target].sort()[1]!
      progress.value = `正在补齐 ${start} — ${end}`
      const older = await fundQuoteApi.nav(code, start, end, indexCode.value || '000300', signal)
      if (id !== requestId || signal.aborted) return
      if (!older.some(p => p.date < coverageStart.value)) {
        warnings.value.push(`${start} — ${coverageStart.value} 未返回更早净值，历史可能不完整。`)
        // Continue to earlier windows: an empty year does not prove fund inception.
      }
      rows.value = mergeHistory(rows.value, older)
      coverageStart.value = start
    }
    if (id === requestId) loadedAt.value = new Date().toLocaleString('zh-CN')
  } catch {
    if (id === requestId && !signal.aborted) error.value = '历史补齐中断，已保留成功加载的数据。点击刷新可重试。'
  } finally {
    if (id === requestId) { extending.value = false; progress.value = '' }
  }
}

async function load() {
  controller?.abort()
  const id = ++requestId
  if (!props.modelValue || !props.holding) return
  viewedHolding.value = { ...props.holding }
  controller = new AbortController()
  const signal = controller.signal, code = props.holding.fundCode
  rows.value = []; info.value = null; estimate.value = null; trades.value = []; selectedTrade.value = null
  warnings.value = []; error.value = ''; loadedAt.value = ''; coverageStart.value = ''; progress.value = ''
  loading.value = true; extending.value = false
  const start = monthsBefore(today(), 24)
  const results = await Promise.allSettled([
    fundQuoteApi.nav(code, start, today(), indexCode.value || '000300', signal),
    fundQuoteApi.info(code, signal), fundQuoteApi.estimate(code, signal), fundQuoteApi.trades(signal)
  ])
  if (id !== requestId || signal.aborted) return
  const [navResult, infoResult, estimateResult, tradeResult] = results
  if (navResult.status === 'fulfilled') { rows.value = navResult.value; coverageStart.value = start }
  else error.value = '历史净值加载失败，请点击刷新重试。'
  if (infoResult.status === 'fulfilled') info.value = infoResult.value
  else warnings.value.push('基金资料暂不可用，成立以来将从 1998 年起尝试查询。')
  if (estimateResult.status === 'fulfilled') estimate.value = estimateResult.value
  else warnings.value.push('盘中估值暂不可用。')
  if (tradeResult.status === 'fulfilled') trades.value = tradeResult.value
  else warnings.value.push('交易记录加载失败，暂不展示买卖标记。')
  loadedAt.value = new Date().toLocaleString('zh-CN')
  loading.value = false
  if (range.value === 'ALL') await extendHistory(id, signal)
}

// Multiple primitive sources compare their values individually. A getter returning
// a new array would retrigger whenever the parent replaces the holding object.
watch([() => props.modelValue, () => props.holding?.id, () => props.holding?.fundCode, () => props.holding?.accountId, indexCode], () => {
  if (props.modelValue) void load()
  else { controller?.abort(); requestId++; loading.value = false; extending.value = false; guideOpen.value = false; adviceOpen.value = false }
}, { immediate: true })
watch(range, () => { selectedTrade.value = null; if (range.value === 'ALL' && !loading.value) void extendHistory() })
onBeforeUnmount(() => { controller?.abort(); requestId++ })
</script>

<template>
  <el-dialog v-model="open" class="fund-quote-dialog" width="min(1480px, 96vw)" top="3vh" :fullscreen="fullscreen" append-to-body destroy-on-close :close-on-click-modal="false" title="基金行情">
    <template #header>
      <div class="quote-header">
        <div><span class="quote-eyebrow">基金行情 / 场外净值</span><h2>{{ viewedHolding?.fundName }} <small>{{ viewedHolding?.fundCode }}</small></h2></div>
        <div class="quote-header-actions">
          <el-select v-model="selectedId" aria-label="切换持仓基金" class="quote-fund-select" filterable>
            <el-option v-for="item in holdings" :key="item.id" :value="item.id" :label="`${item.fundCode} ${item.fundName} · ${item.sourcePlatform || '账户 ' + item.accountId}`" />
          </el-select>
          <button type="button" class="quote-button" @click="fullscreen = !fullscreen">{{ fullscreen ? '退出全屏' : '全屏' }}</button>
        </div>
      </div>
    </template>
    <div class="quote-content" :aria-busy="loading || extending">
      <div class="quote-summary">
        <div class="quote-nav"><span>最新正式净值</span><strong :class="tone(latest?.dailyGrowthRate)">{{ fmt(latest?.nav, 4) }}</strong><b :class="tone(latest?.dailyGrowthRate)">{{ pct(latest?.dailyGrowthRate) }}</b><small>净值日期 {{ latest?.date || '--' }}</small></div>
        <div><span>持仓金额</span><strong>¥ {{ fmt(viewedHolding?.holdingAmount) }}</strong><small>持仓份额 {{ fmt(viewedHolding?.holdingShare, 4) }}</small></div>
        <div><span>持有收益 / 收益率</span><strong :class="tone(viewedHolding?.holdingProfit)">{{ fmt(viewedHolding?.holdingProfit) }} / {{ pct(viewedHolding?.holdingProfitRate) }}</strong><small>当日收益 {{ fmt(viewedHolding?.dailyProfit) }}</small></div>
        <div><span>当前单位成本</span><strong>{{ fmt(cost, 4) }}</strong><small>持仓总成本 ¥ {{ fmt(viewedHolding?.holdingCost) }}</small></div>
        <div class="quote-estimate"><span>盘中估算 · 非正式净值</span><strong>{{ fmt(estimate?.estimateNav, 4) }} <b :class="tone(estimate?.estimateGrowthRate)">{{ pct(estimate?.estimateGrowthRate) }}</b></strong><small>{{ estimate?.estimateTime ? formatDateTime(estimate.estimateTime) : '暂无估值' }}{{ estimate?.delayed ? ' · 延迟数据' : '' }}</small></div>
      </div>
      <div class="quote-toolbar">
        <div class="quote-segments" aria-label="图表周期">
          <button v-for="item in ([['day', '净值日线'], ['week', '周净值K线'], ['month', '月净值K线']] as const)" :key="item[0]" type="button" :aria-pressed="period === item[0]" :class="{ active: period === item[0] }" @click="period = item[0]">{{ item[1] }}</button>
        </div>
        <div class="quote-segments" aria-label="时间范围">
          <button v-for="item in ranges" :key="item.value" type="button" :aria-pressed="range === item.value" :class="{ active: range === item.value }" @click="range = item.value">{{ item.label }}</button>
        </div>
        <label>净值口径 <select v-model="basis" aria-label="净值口径"><option value="unit">单位净值</option><option value="adjusted">前复权净值</option></select></label>
        <button class="quote-button" type="button" :disabled="loading || extending" @click="load">{{ loading ? '加载中…' : extending ? '补齐中…' : '刷新 / 补齐' }}</button>
      </div>
      <div class="quote-toolbar quote-options">
        <label v-for="n in [5, 10, 20, 60]" :key="n"><input v-model="averages" type="checkbox" :value="n" />MA{{ n }}</label>
        <label><input v-model="showCost" type="checkbox" />成本线</label>
        <label><input v-model="showTrades" type="checkbox" />买卖点</label>
        <label><input v-model="showGrowth" type="checkbox" />涨跌幅</label>
        <label><input v-model="showDrawdown" type="checkbox" />回撤</label>
        <label>指标 <select v-model="indicator" aria-label="技术指标"><option value="none">无</option><option>MACD</option><option>RSI</option></select></label>
        <label>参考对比 <select v-model="indexCode" aria-label="参考指数"><option value="">不对比</option><option v-for="item in indices" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <div class="quote-help-actions">
        <button type="button" class="quote-button quote-guide-trigger" aria-haspopup="dialog" @click="adviceOpen = true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M3 3v18h18M6 16l5-6 4 3 6-8m-5 0h5v5" /></svg>
          今日交易推荐
        </button>
        <button type="button" class="quote-button quote-guide-trigger" aria-haspopup="dialog" @click="guideOpen = true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 3v18h17M8 13v5m0-13v4m-2 0h4v4H6zm9 0v5m0-15v5m-2 0h4v5h-4z" /></svg>
          看图指南
        </button>
        </div>
      </div>
      <details v-if="error || warnings.length || isDelayed || adjustedMissing || gapCount" class="quote-notices" role="status">
        <summary>{{ error || (adjustedMissing ? '部分区间无法复权' : isDelayed ? '净值披露有延迟' : '部分数据待补齐') }} <span>查看详情</span></summary>
        <p v-if="error">{{ error }}</p><p v-for="warning in warnings" :key="warning">{{ warning }}</p>
        <p v-if="isDelayed">最新净值距今超过 4 个自然日，请结合基金披露安排与节假日核对；图表未填入估值。</p>
        <p v-if="adjustedMissing">部分日涨跌幅缺失，无法连续复权；相应区间留空，可切换单位净值查看。</p>
        <p v-if="gapCount">存在 {{ gapCount }} 处超过 14 天的净值间隔，可能涉及披露安排或数据缺失，请核对。</p>
      </details>
      <div v-if="loading" class="quote-empty" role="status">正在加载正式净值与持仓记录…</div>
      <template v-else>
        <p v-if="extending" class="quote-progress" role="status">{{ progress }}，当前图表可继续查看。</p>
        <div v-if="hasVisibleData" class="quote-chart-surface"><FundQuoteChart :option="chart.option" :height="chart.height" @trade="selectTrade" /></div>
        <div v-else class="quote-empty">当前区间没有可展示的净值，请切换范围或刷新重试。</div>
        <div class="quote-data-strip">
          <span :title="`读取时间 ${loadedAt} · 来源 ${latest?.sourceName || '基金历史净值接口'}`"><i></i>{{ latest?.date || '--' }} <span class="quote-data-count">· {{ rows.length }} 条净值</span></span>
          <span v-if="showTrades && visibleTrades.length" class="quote-marker-key"><b class="quote-rise">▲</b> 买入 <b class="quote-fall">▼</b> 卖出</span>
          <span>滚轮缩放 · 拖动平移</span>
        </div>
        <details class="quote-footnotes">
          <summary>数据口径</summary>
          <p>{{ rows[0]?.date || '--' }} — {{ latest?.date || '--' }} · {{ latest?.sourceName || '基金历史净值接口' }} · 读取 {{ loadedAt || '--' }}</p>
          <p>周/月K线由日净值聚合；指标按当前周期计算，回撤基于已加载历史高点。</p>
          <p>{{ basis === 'adjusted' ? '前复权按日收益率反推并锚定最新净值，缺失区间留空。' : '单位净值含分红、折算跳变，可切换前复权。' }}成本线为当前单位成本。</p>
          <p v-if="indexCode">{{ indexName }} 为参考指数，价格口径不含分红，休市日可能沿用前值。{{ !rows.some(p => p.indexReturnRate !== null) ? '参考行情暂不可用。' : '' }}</p>
          <p v-if="info?.trackingIndex">资料中的跟踪指数：{{ info.trackingIndex }}
            <button v-if="trackingIndex" type="button" class="quote-trade-link" @click="indexCode = trackingIndex.value">对比该跟踪指数</button>
            <span v-else> · 暂无匹配行情</span>
          </p>
          <p v-if="range === 'ALL'">成立 {{ info?.establishDate || '日期未知' }} · 展示可获取历史。</p>
        </details>
        <details v-if="showTrades" class="quote-trades" :open="!!selectedTrade">
          <summary>买卖记录 <span>{{ visibleTrades.length }} 笔</span></summary>
          <p>按记录日期定位，非净值确认日；{{ chart.markerCount }} 笔已标注，其余仅列明细。</p>
          <div v-if="selectedTrade" class="quote-selected-trade" role="status">{{ tradeLabel(selectedTrade.tradeType) }} · {{ selectedTrade.tradeTime }} · 金额 ¥ {{ fmt(selectedTrade.tradeAmount) }} · 记录净值 {{ fmt(selectedTrade.tradeNav, 4) }} · 份额 {{ fmt(selectedTrade.tradeShare, 4) }} · 费用 ¥ {{ fmt(selectedTrade.tradeFee) }}</div>
          <div class="quote-trade-scroll"><table v-if="visibleTrades.length"><thead><tr><th>记录日期</th><th>类型</th><th>金额</th><th>份额</th><th>记录净值</th><th>费用</th></tr></thead><tbody>
            <tr v-for="trade in visibleTrades" :key="trade.id" :class="{ selected: selectedTrade?.id === trade.id }"><td><button type="button" class="quote-trade-link" @click="selectedTrade = trade">{{ trade.tradeTime }}</button></td><td>{{ tradeLabel(trade.tradeType) }}</td><td>{{ fmt(trade.tradeAmount) }}</td><td>{{ fmt(trade.tradeShare, 4) }}</td><td>{{ fmt(trade.tradeNav, 4) }}</td><td>{{ fmt(trade.tradeFee) }}</td></tr>
          </tbody></table><p v-else>当前区间暂无已完成的买卖记录。</p></div>
        </details>
      </template>
    </div>
    <FundQuoteGuide v-model="guideOpen" />
    <FundTechnicalAdvice v-model="adviceOpen" :holding="viewedHolding" />
  </el-dialog>
</template>

<style>
.fund-quote-dialog.el-dialog { background: #101c28; color: #edf4fb; font-family: "Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif; font-size: 14px; line-height: 1.5; border: 1px solid #476078; border-radius: 14px; padding: 18px 22px; box-shadow: 0 32px 100px #0009; --el-text-color-primary: #edf4fb; --el-text-color-regular: #d4e1ed; --el-text-color-secondary: #b4c7d8; --el-dialog-bg-color: #101c28; }
.fund-quote-dialog .el-dialog__header { margin-right: 0; padding: 0 30px 12px 0; border-bottom: 1px solid #3b5267; }
.fund-quote-dialog .el-dialog__headerbtn .el-dialog__close { color: #b4c7d6; }
.fund-quote-dialog .el-dialog__body { padding: 0; }
.fund-quote-dialog .quote-content { max-height: calc(94dvh - 100px); overflow: auto; scrollbar-gutter: stable; scrollbar-width: thin; scrollbar-color: #314b60 transparent; }
.fund-quote-dialog.is-fullscreen .quote-content { max-height: calc(100dvh - 100px); }
.quote-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.quote-eyebrow { color: #b4c7d8; font-size: 12px; font-weight: 600; letter-spacing: 1.5px; }
.quote-header h2 { margin: 3px 0 0; font-size: 20px; color: #f4f8fc; line-height: 1.5; font-weight: 600; }
.quote-header h2 small { font-weight: 500; font-size: 14px; color: #c3d4e3; }
.quote-header-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.quote-fund-select { width: 260px; }
.quote-fund-select .el-select__wrapper { background: #1c3042; min-height: 36px; font-size: 14px; box-shadow: 0 0 0 1px #58748d inset; }
.quote-fund-select .el-select__selected-item, .quote-fund-select .el-select__caret { color: #edf4fb; }
.quote-summary { display: grid; grid-template-columns: 1.15fr 1fr 1.3fr 1fr 1.5fr; gap: 18px; padding: 16px 4px; }
.quote-summary > div { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.quote-summary span, .quote-summary small { color: #b4c7d8; font-size: 13px; font-weight: 500; overflow-wrap: anywhere; }
.quote-summary strong { color: #edf4fb; font-size: 17px; font-variant-numeric: tabular-nums; font-weight: 600; }
.quote-summary .quote-nav strong { font-size: 32px; font-weight: 700; letter-spacing: -1px; line-height: 1; }
.quote-summary .quote-nav { display: grid; grid-template-columns: auto 1fr; align-items: center; }
.quote-nav span, .quote-nav small { grid-column: 1 / -1; }
.quote-rise { color: #ff858b !important; } .quote-fall { color: #57dfb3 !important; }
.quote-estimate { border-left: 1px solid #304756; padding-left: 18px; }
.quote-toolbar { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; padding: 9px 2px; border-top: 1px solid #3b5267; }
.quote-toolbar label { display: inline-flex; align-items: center; gap: 5px; font-size: 14px; font-weight: 500; color: #d4e1ed; }
.quote-toolbar input { accent-color: #69b6ff; }
.quote-toolbar select { background: #1b3042; color: #edf4fb; font: inherit; border: 1px solid #58748d; border-radius: 5px; padding: 5px; max-width: 160px; }
.quote-button, .quote-segments button { border: 1px solid #58748d; border-radius: 5px; padding: 7px 11px; color: #dce9f5; background: #1b3042; cursor: pointer; font: inherit; font-size: 14px; font-weight: 500; transition: background .15s, color .15s; }
.quote-button:hover, .quote-segments button:hover { color: #ffffff; background: #2b4862; }
.quote-button:disabled { opacity: .5; cursor: wait; }
.quote-segments { display: flex; flex-wrap: wrap; gap: 3px; }
.quote-segments button { border-color: transparent; background: transparent; }
.quote-segments button.active { background: #285477; border-color: #7fbbe8; color: #ffffff; font-weight: 600; }
.quote-options { gap: 8px; margin-bottom: 0; border-bottom: 1px solid #3b5267; }
.quote-help-actions { margin-left: auto; display: flex; flex-wrap: wrap; gap: 8px; }
.quote-guide-trigger { display: inline-flex; align-items: center; gap: 7px; white-space: nowrap; }
.quote-options label:has(input) { padding: 4px 7px; border: 1px solid transparent; border-radius: 4px; cursor: pointer; }
.quote-options label:has(input:checked) { background: #223f56; border-color: #567a98; color: #edf6ff; }
.quote-options input { width: 14px; height: 14px; margin: 0 2px 0 0; }
.quote-notices { background: #40362133; color: #d6b882; border: 1px solid #76603b66; padding: 7px 10px; margin: 8px 0; border-radius: 5px; font-size: 13px; }
.quote-notices summary { cursor: pointer; }
.quote-notices summary span { margin-left: 12px; color: #b4c7d8; }
.quote-notices p { margin: 4px 0; }
.quote-empty { min-height: 340px; display: grid; place-content: center; color: #b4c7d8; }
.quote-progress { color: #8cc7ff; padding: 6px 12px; }
.quote-chart-surface { background: #0b1520; border: 1px solid #405a70; border-radius: 8px; margin-top: 10px; padding-top: 3px; overflow: hidden; }
.quote-data-strip { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; padding: 9px 6px; color: #b4c7d8; font-size: 13px; font-variant-numeric: tabular-nums; }
.quote-data-strip i { display: inline-block; width: 5px; height: 5px; margin-right: 7px; border-radius: 50%; background: #5d8fa8; }
.quote-marker-key { display: inline-flex; align-items: center; gap: 6px; }
.quote-marker-key b { font-size: 11px; margin-left: 6px; }
.quote-footnotes { color: #b4c7d8; padding: 7px 6px; font-size: 13px; line-height: 1.7; }
.quote-footnotes summary { width: fit-content; color: #d4e1ed; cursor: pointer; }
.quote-footnotes p { margin: 6px 0; }
.quote-trades { border-top: 1px solid #3b5267; margin-top: 6px; padding: 11px 6px; font-size: 13px; }
.quote-trades summary span { color: #b4c7d8; margin-left: 8px; }
.quote-trades summary { cursor: pointer; color: #afcee6; }
.quote-trades p { color: #b4c7d8; line-height: 1.7; }
.quote-trade-scroll { overflow: auto; max-height: 260px; }
.quote-trades table { width: 100%; border-collapse: collapse; white-space: nowrap; }
.quote-trades td, .quote-trades th { text-align: left; padding: 9px; border-bottom: 1px solid #293e4c; }
.quote-trades tr.selected { background: #264358; }
.quote-trade-link { color: #89c7ff; background: transparent; border: 0; cursor: pointer; }
.quote-selected-trade { background: #203c51; padding: 12px; margin-bottom: 12px; }
.fund-quote-dialog button:focus-visible, .fund-quote-dialog select:focus-visible, .fund-quote-dialog summary:focus-visible { outline: 2px solid #8dc8ff; outline-offset: 3px; }
@media (max-width: 900px) {
  .quote-header { align-items: flex-start; flex-direction: column; }
  .quote-summary { grid-template-columns: repeat(3, 1fr); }
  .quote-estimate { border-left: 0; padding-left: 0; }
  .fund-quote-dialog .quote-content { max-height: calc(94dvh - 160px); }
}
@media (max-width: 560px) {
  .fund-quote-dialog.el-dialog { padding: 14px 8px; }
  .quote-header h2 { font-size: 18px; }
  .quote-header-actions { width: 100%; }
  .quote-fund-select { flex: 1; width: 0; }
  .quote-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px 10px; }
  .quote-summary strong { font-size: 15px; }
  .quote-estimate { grid-column: 1 / -1; }
  .quote-toolbar { gap: 8px; }
  .quote-button, .quote-segments button { padding: 7px 8px; }
}
</style>
