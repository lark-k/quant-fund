import { aggregateNav, macd, movingAverage, periodKey, quotePoints, rsi, type QuoteNav } from './fundQuote'
import type { MarketSessionStatus } from '@/types/domain'

export type TechnicalAction = 'BUY' | 'REDUCE' | 'HOLD' | 'WATCH' | 'UNAVAILABLE'
export interface TechnicalEvidence { label: string; value: string; explanation: string; tone: 'bull' | 'bear' | 'neutral' }
export interface TechnicalAdvice {
  action: TechnicalAction
  title: string
  explanation: string
  asOf: string
  evaluatedAt: string
  sampleCount: number
  source: string
  evidence: TechnicalEvidence[]
  blockers: string[]
  notes: string[]
}
export const technicalRules = [
  '固定使用最近 120 个有效日净值，以公布的日收益率反推前复权；不受主图周期、范围或均线开关影响。',
  '买入：净值 > MA20 > MA60，MA5 > MA10 > MA20，MA20 高于 5 个净值点前；DIF > DEA 且 DIF > 0；最近完整周上涨、低点及收盘抬高；RSI14 在 45–70，净值高于 MA20 不超过 5%。须全部满足。',
  '减仓：净值 < MA20 < MA60，MA5 < MA10 < MA20，MA20 低于 5 个净值点前；DIF < DEA 且 DIF < 0；最近完整周下跌、高点及收盘降低。须全部满足。',
  '趋势向上但偏热或确认不足时持有、不追涨；其余信号不一致时观望。周 K 仅比较当前自然周之前的两个相邻周，每周至少 3 个净值点；它是日净值聚合，不是盘中成交 K 线。',
  '数据门槛：120 个连续可复权净值点、最近数据距今不超过 4 个自然日且最多落后 1 个工作日、样本内间隔不超过 14 天；交易日核对采用系统 A 股日历。假期后或 QDII 披露较慢时可能保守地暂停建议。',
  '上述联合条件、120 点窗口与 5% 乖离门槛是本功能公开的启发式规则，尚未进行策略回测，不代表已验证的收益或胜率。'
]

export function shanghaiDateTime(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).formatToParts(now)
  const p = Object.fromEntries(parts.map(part => [part.type, part.value]))
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`
}
const dayMs = 86400000
function dateNumber(date: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return NaN
  const time = Date.parse(`${date}T00:00:00Z`)
  return Number.isFinite(time) && new Date(time).toISOString().slice(0, 10) === date ? time : NaN
}
const f = (v: number) => v.toFixed(4)
const pct = (v: number) => `${v > 0 ? '+' : ''}${v.toFixed(2)}%`
const direction = (up: boolean, down: boolean): TechnicalEvidence['tone'] => up ? 'bull' : down ? 'bear' : 'neutral'

// The production API returns FundType enum codes; mock/older sources use Chinese labels.
// Accept known codes explicitly, never infer eligibility from the holding's name.
const supportedFundTypes = new Set(['ACTIVE_EQUITY', 'MIXED', 'INDEX', 'INDEX_ENHANCED', 'ETF', 'ETF_LINK', 'QDII'])
function fundTypeBlocker(raw: string) {
  const type = typeof raw === 'string' ? raw.trim() : ''
  const code = type.toUpperCase()
  if (!type || code === 'UNKNOWN') return '基金类型资料缺失或为 UNKNOWN，暂无法确认分析适用性。'
  if (/债|货币|理财|固收|FOF|REIT|BOND|FIXED_INCOME|MONEY/i.test(type)) return `基金类型「${type}」不适用当前权益趋势规则。`
  if (supportedFundTypes.has(code) || /股票|混合|指数|权益|QDII/i.test(type)) return null
  return `基金类型「${type}」尚未识别，暂无法确认分析适用性。`
}

// Pure, deterministic decision rules. No estimates, account mutations or AI text
// generation enter the calculation; every conclusion is backed by the same snapshot.
export function analyzeFundTechnicals(input: { rows: QuoteNav[]; fundType: string; market: MarketSessionStatus | null; now: Date }): TechnicalAdvice {
  const evaluatedAt = shanghaiDateTime(input.now), today = evaluatedAt.slice(0, 10), todayMs = dateNumber(today)
  const result: TechnicalAdvice = { action: 'UNAVAILABLE', title: '暂无法完成技术分析', explanation: '以下条件尚未满足，当前未生成买卖结论。', asOf: '--', evaluatedAt, sampleCount: 0, source: '--', evidence: [], blockers: [], notes: [] }
  const block = (reason: string) => result.blockers.push(reason)
  const typeBlocker = fundTypeBlocker(input.fundType)
  if (typeBlocker) block(typeBlocker)
  const market = input.market
  const aShare = market?.markets.find(item => item.market === 'A股')
  if (!market || market.updateTime.slice(0, 10) !== today || !aShare) block('今日市场日历状态不可用，无法确认建议适用日。')
  else if (aShare.statusText === '非交易日') block('系统日历显示今日为 A 股非交易日，本功能暂停今日买卖建议。')
  else if (!['A股交易中', '未开盘', '已收盘'].includes(aShare.statusText)) block('当前市场状态无法识别，暂停今日买卖建议。')
  const seen = new Map<string, QuoteNav>()
  for (const row of input.rows) {
    if (!Number.isFinite(dateNumber(row.date)) || !Number.isFinite(row.nav) || row.nav <= 0) { block('历史净值包含无效日期或数值。'); break }
    if (row.date > today) { block('历史净值含未来日期，无法形成当前建议。'); break }
    const prior = seen.get(row.date)
    if (prior && (prior.nav !== row.nav || prior.dailyGrowthRate !== row.dailyGrowthRate)) { block('同一日期的净值记录不一致，请先核对数据源。'); break }
    seen.set(row.date, row)
  }
  const rows = [...seen.values()].sort((a, b) => a.date.localeCompare(b.date)).slice(-120)
  result.sampleCount = rows.length
  const latest = rows[rows.length - 1]
  if (!latest) { block('没有可用的正式净值。'); return result }
  result.asOf = latest.date
  result.source = [...new Set(rows.map(row => row.sourceName || '基金历史净值接口'))].join(' / ')
  const lag = (todayMs - dateNumber(latest.date)) / dayMs
  let weekdays = 0
  // Weekday fallback is deliberately conservative; it never invents a holiday calendar.
  for (let t = dateNumber(latest.date) + dayMs; t <= todayMs && weekdays <= 1; t += dayMs) {
    const weekday = new Date(t).getUTCDay()
    if (weekday !== 0 && weekday !== 6) weekdays++
  }
  if (lag > 4 || weekdays > 1) block(`最新净值为 ${latest.date}，超过本功能的新鲜度门槛；刷新后再分析。`)
  if (rows.length < 120) block(`仅有 ${rows.length} 个日净值点，至少需要 120 个用于均线、MACD 与周线分析。`)
  if (rows.slice(1).some((row, i) => dateNumber(row.date) - dateNumber(rows[i].date) > 14 * dayMs)) block('分析窗口内存在超过 14 天的数据间隔，无法确认走势连续性。')
  if (rows.slice(1).some(row => row.dailyGrowthRate === null || !Number.isFinite(row.dailyGrowthRate) || row.dailyGrowthRate <= -100)) block('日收益率缺失或无效，不能可靠复权；不以原始净值跳变代替交易信号。')
  if (result.blockers.length) return result
  const points = quotePoints(rows, 'adjusted')
  if (points.some(p => p.value === null || !Number.isFinite(p.value) || p.value <= 0)) { block('复权净值计算不可用。'); return result }
  const values = points.map(p => p.value as number), n = values.length - 1
  const ma5 = movingAverage(values, 5)[n]!, ma10 = movingAverage(values, 10)[n]!, ma20s = movingAverage(values, 20), ma20 = ma20s[n]!, ma60 = movingAverage(values, 60)[n]!
  const price = values[n], slope = (ma20 / ma20s[n - 5]! - 1) * 100, bias = (price / ma20 - 1) * 100
  const m = macd(values), dif = m.dif[n]!, dea = m.dea[n]!, histogram = m.histogram[n]!, strength = rsi(values)[n]!
  if (![ma5, ma10, ma20, ma60, slope, bias, dif, dea, histogram, strength].every(Number.isFinite)) { block('指标数值异常，暂停生成建议。'); return result }
  const currentWeek = periodKey(today, 'week')
  // Exclude the current (unfinished) week and the first potentially partial week.
  const weeks = aggregateNav(points, 'week').filter(w => periodKey(w.date, 'week') < currentWeek && periodKey(w.date, 'week') > periodKey(rows[0].date, 'week'))
  const week = weeks[weeks.length - 1], previousWeek = weeks[weeks.length - 2]
  if (!week || !previousWeek || [week, previousWeek].some(w => points.filter(p => periodKey(p.date, 'week') === periodKey(w.date, 'week')).length < 3) || periodKey(week.date, 'week') !== new Date(dateNumber(currentWeek) - 7 * dayMs).toISOString().slice(0, 10) || dateNumber(periodKey(week.date, 'week')) - dateNumber(periodKey(previousWeek.date, 'week')) !== 7 * dayMs) {
    block('最近两个完整周的净值覆盖不足，无法确认周 K 趋势。'); return result
  }
  const trendUp = price > ma20 && ma20 > ma60 && ma5 > ma10 && ma10 > ma20 && slope > 0
  const trendDown = price < ma20 && ma20 < ma60 && ma5 < ma10 && ma10 < ma20 && slope < 0
  const momentumUp = dif > dea && dif > 0, momentumDown = dif < dea && dif < 0
  const weekUp = week.close! > week.open! && week.low! > previousWeek.low! && week.close! > previousWeek.close!
  const weekDown = week.close! < week.open! && week.high! < previousWeek.high! && week.close! < previousWeek.close!
  const notChasing = strength >= 45 && strength <= 70 && bias <= 5
  const cross = m.dif[n - 1]! <= m.dea[n - 1]! && dif > dea ? '最新点金叉' : m.dif[n - 1]! >= m.dea[n - 1]! && dif < dea ? '最新点死叉' : '最新点无交叉'
  result.evidence = [
    { label: '日线 / 均线', value: `净值 ${f(price)} · MA5 ${f(ma5)} · MA10 ${f(ma10)} · MA20 ${f(ma20)} · MA60 ${f(ma60)}`, tone: direction(trendUp, trendDown), explanation: `MA20 相对 5 个净值点前 ${pct(slope)}；${trendUp ? '满足多头排列与向上斜率。' : trendDown ? '满足空头排列与向下斜率。' : '未形成规则要求的完整单向趋势。'}` },
    { label: 'MACD（12, 26, 9）', value: `DIF ${f(dif)} · DEA ${f(dea)} · 柱 ${f(histogram)}`, tone: direction(momentumUp, momentumDown), explanation: `${cross}；${momentumUp ? 'DIF 在零轴上方且高于 DEA，动量偏强。' : momentumDown ? 'DIF 在零轴下方且低于 DEA，动量偏弱。' : '零轴位置与两线关系未形成同向确认。'}` },
    { label: '完整周 K（日净值聚合）', value: `${week.start} — ${week.end} · 首 ${f(week.open!)} / 末 ${f(week.close!)} / 低 ${f(week.low!)} / 高 ${f(week.high!)}`, tone: direction(weekUp, weekDown), explanation: `对比前周 ${previousWeek.start} — ${previousWeek.end}；${weekUp ? '上涨，低点与收盘抬高。' : weekDown ? '下跌，高点与收盘降低。' : '未形成周线同向确认。'}当前未结束周不参与。` },
    { label: 'RSI / 追高检查', value: `RSI14 ${strength.toFixed(2)} · 距 MA20 ${pct(bias)}`, tone: 'neutral', explanation: strength > 70 || bias > 5 ? '达到本规则的偏热门槛，暂停新增买入；不单凭超买要求卖出。' : strength < 45 ? 'RSI 尚未达到本规则的买入确认区间；超卖不代表必然反弹。' : '满足 RSI 45–70、正乖离不超过 5% 的买入过滤条件。' }
  ]
  if (trendUp && momentumUp && weekUp && notChasing) {
    result.action = 'BUY'; result.title = '可考虑分批买入'; result.explanation = '日线均线、MACD 与完整周 K 同向偏强，且未触发追高过滤。若投资期限和仓位计划允许，可考虑分批布局。'
  } else if (trendDown && momentumDown && weekDown) {
    result.action = 'REDUCE'; result.title = '可考虑减仓'; result.explanation = '均线趋势、MACD 与完整周 K 同向偏弱。已有持仓可结合风险计划考虑减仓，操作前核对持有期和赎回费用。'
  } else if (trendUp) {
    result.action = 'HOLD'; result.title = '持有观察，暂不加仓'; result.explanation = !notChasing ? '均线趋势偏强，但 RSI 或乖离未通过买入过滤；暂不追涨。' : '均线趋势偏强，但 MACD 或完整周 K 尚未共同确认，等待下一次正式净值。'
  } else {
    result.action = 'WATCH'; result.title = '观望，暂不交易'; result.explanation = '当前指标未同时满足买入或减仓规则，趋势与动量证据不够一致，不根据单一涨跌或交叉作出交易判断。'
  }
  result.notes = [`以 ${latest.date} 正式净值为依据，不含盘中估值。${latest.date !== today ? '这不是今日收盘后的信号。' : ''}`, '技术指标均来源于同一净值序列，并非独立预测；无法保证后续走势。', '未纳入个人风险承受能力、资金需求、申赎限制及费用，不给出具体金额、仓位比例或保证收益。']
  if (evaluatedAt.slice(11, 16) >= '15:00') result.notes.push('北京时间已过 15:00；今日提交可能按下一开放日受理，具体以基金和平台截止时间为准。')
  return result
}
