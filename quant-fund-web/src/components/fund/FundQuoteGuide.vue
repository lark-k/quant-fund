<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const section = ref('candles')
const scenario = ref<'buy' | 'sell' | 'wait'>('buy')
const sections = [{ id: 'candles', title: '01 读懂 K 线' }, { id: 'indicators', title: '02 看懂指标' }, { id: 'decisions', title: '03 买卖怎么判断' }]
const scenarios = {
  buy: { title: '考虑分批买入前', color: '#8dccff', path: 'M30 172 L65 150 L100 158 L135 123 L170 131 L205 100 L240 112 L275 82 L310 90 L345 55 L380 65 L425 33',
    caption: '低点逐步抬高 → 回落后企稳 → 再评估',
    checks: ['投资目标与期限仍匹配，资金近期不用。', '净值回落后企稳，均线方向与趋势相互支持。', '仓位低于自己的计划上限，预先确定分批金额。'],
    caution: '只是观察条件。单次金叉或一天上涨，不能确认后续一定上涨。' },
  sell: { title: '考虑减仓或赎回前', color: '#57dfb3', path: 'M30 40 L65 55 L100 35 L135 65 L170 55 L205 95 L240 78 L275 120 L310 108 L345 153 L380 145 L425 178',
    caption: '反弹乏力 → 低点下移 → 检查风险计划',
    checks: ['需要用钱、仓位超标，或原有投资理由发生变化。', '趋势转弱且反弹未修复，结合净值而非盘中估值确认。', '达到自己预先设定的风险或再平衡条件，检查费用后执行。'],
    caution: '跌破均线也可能是假信号。不要为了等回本而忽视资金需求与风险承受能力。' },
  wait: { title: '适合先观察的情况', color: '#f2cf7c', path: 'M30 108 L65 70 L100 133 L135 88 L170 123 L205 67 L240 118 L275 80 L310 130 L345 87 L380 117 L425 94',
    caption: '来回震荡 → 交叉频繁 → 等待更清晰的证据',
    checks: ['均线缠绕，MACD 反复交叉，方向不清楚。', '短期涨幅很大，买入理由仅是怕错过。', '净值披露延迟、数据缺失，或近期有分红与份额折算。'],
    caution: '不操作也是选择。先核对数据、投资理由与交易计划，再决定是否行动。' }
}
const lesson = computed(() => scenarios[scenario.value])
watch(() => props.modelValue, value => { if (value) { section.value = 'candles'; scenario.value = 'buy' } })
</script>

<template>
  <el-dialog v-model="open" class="fund-quote-guide" title="看图指南" width="min(880px, 94vw)" top="5vh" append-to-body destroy-on-close :close-on-click-modal="false">
    <template #header><div class="guide-heading"><span>场外基金 · 图解入门</span><h2>看懂走势，再做决定</h2></div></template>
    <div class="guide-body">
      <nav class="guide-tabs" aria-label="指南章节">
        <button v-for="item in sections" :key="item.id" type="button" :aria-pressed="section === item.id" :class="{ active: section === item.id }" @click="section = item.id">{{ item.title }}</button>
      </nav>

      <section v-if="section === 'candles'" aria-label="读懂 K 线">
        <p class="guide-intro">日线连接每日正式净值；本页的周 / 月 K 线，把一周 / 一月的日净值压缩成一根蜡烛。</p>
        <div class="guide-figure">
          <svg viewBox="0 0 700 245" role="img" aria-label="红色上涨蜡烛：期末高于期初；绿色下跌蜡烛：期末低于期初。上下影线分别到达周期内最高与最低日净值。">
            <g stroke="#344c63" stroke-dasharray="4 5"><path d="M35 46H670M35 210H670" /></g>
            <g stroke="#ff858b" stroke-width="3"><path d="M215 46V210" /><rect x="195" y="88" width="40" height="85" fill="#ff858b" /></g>
            <g stroke="#57dfb3" stroke-width="3"><path d="M480 46V210" /><rect x="460" y="88" width="40" height="85" fill="#57dfb3" /></g>
            <g fill="#edf4fb" font-size="16"><text x="174" y="25">上涨周期</text><text x="440" y="25">下跌周期</text>
              <text x="40" y="51">最高日净值</text><text x="40" y="215">最低日净值</text>
              <text x="250" y="94">期末</text><text x="250" y="178">期初</text>
              <text x="518" y="94">期初</text><text x="518" y="178">期末</text>
            </g>
            <g fill="#bacbdb" font-size="14"><text x="171" y="239">期末 &gt; 期初</text><text x="436" y="239">期末 &lt; 期初</text></g>
          </svg>
        </div>
        <div class="guide-cards">
          <article><h3>实体：看方向</h3><p>上下边缘是周期内首个、末个日净值。实体越长，首末净值差越大。</p></article>
          <article><h3>影线：看区间</h3><p>上下端是周期内最高、最低日净值。长影线说明首末值之外还有明显波动。</p></article>
          <article><h3>连续几根：看趋势</h3><p>观察高点、低点是否逐步抬高或降低，单根红绿不能决定买卖。</p></article>
        </div>
        <p class="guide-note">这里的 K 线由日净值聚合，不是盘中开盘、收盘与最高最低成交价。下方柱形是涨跌幅，不是成交量。示意图仅用于教学。</p>
      </section>

      <section v-else-if="section === 'indicators'" aria-label="看懂指标">
        <p class="guide-intro">先看趋势，再用指标辅助确认。切换到周 K 后，MA5 代表 5 个周周期，不再是 5 天。</p>
        <div class="guide-figure">
          <svg viewBox="0 0 700 215" role="img" aria-label="示意净值波动较快，短期均线跟随更快，长期均线更平缓但更滞后。">
            <g stroke="#344c63" stroke-dasharray="4 5"><path d="M25 80H675M25 150H675" /></g>
            <path d="M25 178L65 149L105 162L145 106L185 123L225 80L265 105L305 68L345 88L385 34L425 63L465 94L505 68L545 116L585 138L625 111L675 145" fill="none" stroke="#8dccff" stroke-width="3" />
            <path d="M25 182C130 173 140 130 225 117S355 55 405 64S525 91 675 136" fill="none" stroke="#f2cf7c" stroke-width="2.5" />
            <path d="M25 190C190 190 240 151 340 133S510 102 675 110" fill="none" stroke="#eeaad1" stroke-width="2.5" />
            <g font-size="15" font-weight="600"><text x="30" y="25" fill="#8dccff">净值</text><text x="120" y="25" fill="#f2cf7c">短期均线</text><text x="265" y="25" fill="#eeaad1">长期均线</text></g>
            <text x="30" y="211" fill="#bacbdb" font-size="13">教学示意 · 均线平滑历史波动，也会滞后于变化</text>
          </svg>
        </div>
        <div class="guide-cards guide-two-cols">
          <article><h3>MA：方向与节奏</h3><p>短期线上穿长期线叫“金叉”，下穿叫“死叉”。方向转变值得观察，但震荡时容易反复失效。</p></article>
          <article><h3>MACD：动量变化</h3><p>DIF 与 DEA 的交叉、柱体收缩或扩大，辅助观察动量；它也来自净值，不能预测确定的买卖点。</p></article>
          <article><h3>RSI：强弱位置</h3><p>常见参考区间为 30 / 70。低于 30 不等于必涨，高于 70 不等于必跌，强趋势可能持续很久。</p></article>
          <article><h3>回撤与成本：风险视角</h3><p>回撤反映相对已加载历史高点的跌幅；成本线是个人持仓成本，不是市场支撑位。</p></article>
        </div>
      </section>

      <section v-else aria-label="买卖怎么判断">
        <p class="guide-intro">图表提供观察线索。真正的决定还要结合投资期限、基金变化、仓位与费用。</p>
        <div class="guide-scenarios" aria-label="选择教学情景">
          <button v-for="(label, key) in { buy: '买入前', sell: '卖出前', wait: '先观察' }" :key="key" type="button" :aria-pressed="scenario === key" :class="{ active: scenario === key }" @click="scenario = key">{{ label }}</button>
        </div>
        <div class="guide-decision">
          <div class="guide-figure">
            <svg viewBox="0 0 455 220" role="img" :aria-label="lesson.caption">
              <path d="M25 60H430M25 115H430M25 175H430" stroke="#344c63" stroke-dasharray="4 5" />
              <path :d="lesson.path" :stroke="lesson.color" fill="none" stroke-width="3.5" stroke-linejoin="round" />
              <text x="25" y="211" fill="#bacbdb" font-size="13">示意走势，不对应当前基金</text>
            </svg>
            <p>{{ lesson.caption }}</p>
          </div>
          <article class="guide-checks"><h3>{{ lesson.title }}</h3><ol><li v-for="check in lesson.checks" :key="check">{{ check }}</li></ol></article>
        </div>
        <p class="guide-note">{{ lesson.caution }}</p>
        <article class="guide-fund-rule"><h3>场外基金：看到信号 ≠ 按图上价格成交</h3><p>一般按适用开放日的净值确认，提交时成交净值尚未确定。看完已公布净值再下单，也不能回到该净值成交。截止时间、确认日、到账与赎回费以该基金及平台规则为准，QDII 等产品可能有不同安排。</p></article>
      </section>

      <details class="guide-sources"><summary>学习资料与适用范围</summary><p>以上为通用教学，不是针对当前基金的买卖建议；指标会滞后并可能失效。</p><a href="https://edu.efunds.com.cn/c/18/18848.shtml" target="_blank" rel="noopener noreferrer">易方达投教：未知价成交原则</a><a href="https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide" target="_blank" rel="noopener noreferrer">Fidelity：技术指标学习指南</a></details>
    </div>
    <template #footer><button type="button" class="guide-done" @click="open = false">知道了，返回图表</button></template>
  </el-dialog>
</template>

<style>
.fund-quote-guide.el-dialog { background: #101c28; color: #dce8f4; border: 1px solid #58748d; border-radius: 12px; padding: 22px; --el-text-color-primary: #edf4fb; --el-text-color-regular: #dce8f4; }
.fund-quote-guide .el-dialog__header { padding: 0 32px 16px 0; }
.fund-quote-guide .el-dialog__close { color: #dce8f4; }
.fund-quote-guide .el-dialog__body { padding: 0; }
.guide-heading span { color: #b4c7d8; font-size: 13px; }
.guide-heading h2 { margin: 4px 0 0; color: #edf4fb; font-size: 22px; }
.guide-body { max-height: calc(90dvh - 160px); overflow: auto; padding-right: 8px; font: 14px/1.7 "Segoe UI", "Microsoft YaHei", sans-serif; scrollbar-width: thin; scrollbar-color: #58748d transparent; }
.guide-tabs, .guide-scenarios { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.guide-tabs button, .guide-scenarios button, .guide-done { padding: 8px 13px; border: 1px solid #58748d; border-radius: 6px; background: #1b3042; color: #dce8f4; font: inherit; cursor: pointer; }
.guide-tabs button.active, .guide-scenarios button.active, .guide-done { background: #285477; color: #fff; border-color: #8dccff; }
.fund-quote-guide button:focus-visible, .fund-quote-guide summary:focus-visible, .fund-quote-guide a:focus-visible { outline: 2px solid #8dccff; outline-offset: 3px; }
.guide-intro { margin: 0 0 14px; color: #d4e1ed; }
.guide-figure { background: #0b1520; border: 1px solid #3b5267; border-radius: 8px; padding: 12px; }
.guide-figure svg { display: block; width: 100%; height: auto; }
.guide-figure p { color: #dce8f4; margin: 8px 0 0; font-size: 13px; }
.guide-cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-top: 18px; }
.guide-two-cols { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.guide-body h3 { font-size: 15px; color: #edf4fb; margin: 0 0 6px; }
.guide-cards p, .guide-fund-rule p { margin: 0; color: #bacbdb; }
.guide-note { padding: 10px 13px; border-left: 3px solid #f2cf7c; background: #24303a; color: #e9d8b4; margin: 18px 0; }
.guide-decision { display: grid; grid-template-columns: 1.1fr 1fr; gap: 18px; align-items: center; }
.guide-checks ol { margin: 0; padding-left: 22px; }
.guide-checks li { padding: 4px 0; }
.guide-fund-rule { margin: 16px 0; }
.guide-sources { margin-top: 20px; padding-top: 12px; border-top: 1px solid #3b5267; color: #b4c7d8; font-size: 13px; }
.guide-sources summary { cursor: pointer; }
.guide-sources a { display: block; color: #8dccff; }
@media (max-width: 600px) {
  .fund-quote-guide.el-dialog { padding: 16px 12px; }
  .guide-tabs { gap: 5px; }
  .guide-tabs button { padding: 7px 8px; font-size: 13px; }
  .guide-cards, .guide-decision { grid-template-columns: 1fr; }
  .guide-figure { overflow-x: auto; }
  .guide-figure svg { min-width: 420px; }
}
</style>
