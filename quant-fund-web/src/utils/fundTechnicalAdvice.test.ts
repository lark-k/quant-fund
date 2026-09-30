import { describe, expect, it } from 'vitest'
import { analyzeFundTechnicals, shanghaiDateTime } from './fundTechnicalAdvice'
import type { QuoteNav } from './fundQuote'
import type { MarketSessionStatus } from '@/types/domain'
const now = new Date('2026-09-30T06:00:00Z')
const market: MarketSessionStatus = { primaryStatusText: 'A股交易中', trading: true, updateTime: '2026-09-30T14:00:00', markets: [{ market: 'A股', statusText: 'A股交易中', trading: true }] }
function history(value: (i: number) => number, count = 200): QuoteNav[] {
  const dates: string[] = [], day = new Date('2026-09-29T00:00:00Z')
  while (dates.length < count) { if (day.getUTCDay() !== 0 && day.getUTCDay() !== 6) dates.unshift(day.toISOString().slice(0, 10)); day.setUTCDate(day.getUTCDate() - 1) }
  return dates.map((date, i) => ({ date, nav: value(i), dailyGrowthRate: i ? (value(i) / value(i - 1) - 1) * 100 : null, accumulatedNav: null, indexReturnRate: null, sourceName: 'OFFICIAL_TEST' }))
}
const analyze = (rows: QuoteNav[], override = {}) => analyzeFundTechnicals({ rows, fundType: '混合型', market, now, ...override })
const rising = () => history(i => 1 + i * .001 + .012 * Math.sin(i * .7 + 2))
describe('technical decision rules', () => {
  it.each(['ACTIVE_EQUITY', 'MIXED', 'INDEX', 'INDEX_ENHANCED', 'ETF', 'ETF_LINK', 'QDII', ' mixed ', '股票型', '混合型-偏股', '指数型-股票'])('accepts production type %s with identical numerical evidence', fundType => {
    expect(analyze(rising(), { fundType })).toEqual(analyze(rising()))
    expect(analyze(history(i => 2 - i * .003), { fundType }).action).toBe('REDUCE')
    expect(analyze(rising().slice(-119), { fundType }).action).toBe('UNAVAILABLE')
  })
  it.each(['BOND', 'FIXED_INCOME_PLUS', 'MONEY_MARKET', '债券指数', 'QDII债券', '货币型', '固收+', 'FOF', 'REIT', 'ETF债券'])('keeps unsupported type %s blocked', fundType => {
    const result = analyze(rising(), { fundType })
    expect(result.action).toBe('UNAVAILABLE')
    expect(result.evidence).toEqual([])
    expect(result.blockers).toContain(`基金类型「${fundType}」不适用当前权益趋势规则。`)
  })
  it('distinguishes missing metadata from unrecognized types and trading signals', () => {
    for (const fundType of ['', '  ', 'UNKNOWN']) expect(analyze(rising(), { fundType }).blockers.join('')).toContain('资料缺失')
    const result = analyze(rising(), { fundType: 'NEW_TYPE' })
    expect(result.action).toBe('UNAVAILABLE')
    expect(result.title).toBe('暂无法完成技术分析')
    expect(result.blockers.join('')).toContain('「NEW_TYPE」尚未识别')
  })
  it('requires trend, momentum, finished week and RSI to align before buying', () => {
    const result = analyze(rising())
    expect(result.action).toBe('BUY')
    expect(result.evidence.slice(0, 3).map(e => e.tone)).toEqual(['bull', 'bull', 'bull'])
    expect(result.evidence[0].value).toContain('MA20 1.1900')
    expect(result.evidence[1].value).toBe('DIF 0.0082 · DEA 0.0074 · 柱 0.0016')
    expect(result.evidence[3].value).toContain('RSI14 59.13')
    expect(result.asOf).toBe('2026-09-29')
    expect(result.sampleCount).toBe(120)
  })
  it('recommends reduction only when bearish daily and weekly evidence agrees', () => {
    const result = analyze(history(i => 2 - i * .003))
    expect(result.action).toBe('REDUCE')
    expect(result.evidence.slice(0, 3).map(e => e.tone)).toEqual(['bear', 'bear', 'bear'])
  })
  it('does not chase an uninterrupted rise or sell simply because RSI is high', () => {
    const result = analyze(history(i => 1 + i * .004))
    expect(result.action).toBe('HOLD')
    expect(result.evidence[3].value).toContain('RSI14 100.00')
  })
  it('withholds buying when weekly confirmation is missing or momentum conflicts', () => {
    expect(analyze(history(i => 1 + i * .001 + .012 * Math.sin(i * .7 + 1))).action).toBe('HOLD')
    const conflict = analyze(history(i => 1 + i * .001 + .012 * Math.sin(i * .7 + 3)))
    expect(conflict.action).toBe('HOLD')
    expect(conflict.evidence[1].tone).toBe('neutral')
  })
  it('returns observation for flat and mixed markets without zero-division artifacts', () => {
    const flat = analyze(history(() => 1))
    expect(flat.action).toBe('WATCH')
    expect(flat.evidence[1].value).toBe('DIF 0.0000 · DEA 0.0000 · 柱 0.0000')
    expect(flat.evidence[3].value).toContain('RSI14 50.00')
    expect(analyze(history(i => 1 + i * .001 + .012 * Math.sin(i * .7))).action).toBe('WATCH')
  })
  it('uses a fixed recent window and does not mutate or depend on input order', () => {
    const rows = rising(), before = JSON.stringify(rows)
    expect(analyze(rows.slice(-120))).toEqual(analyze(rows))
    expect(analyze([...rows].reverse())).toEqual(analyze(rows))
    expect(JSON.stringify(rows)).toBe(before)
  })
  it('does not use unfinished current-week candles as weekly confirmation', () => {
    const rows = rising(), last = rows[rows.length - 1]
    const changed = rows.map(r => ({ ...r }))
    changed[changed.length - 1] = { ...last, nav: rows[rows.length - 2].nav * 1.1, dailyGrowthRate: 10 }
    expect(analyze(changed).evidence[2]).toEqual(analyze(rows).evidence[2])
    expect(analyze(rows).evidence[2].value).toContain('2026-09-21 — 2026-09-25')
  })
  it('uses published returns across a dividend/split instead of raw NAV jumps', () => {
    const rows = rising(), split = rows.map((r, i) => ({ ...r, nav: i < 190 ? r.nav * 2 : r.nav }))
    expect(analyze(split)).toEqual(analyze(rows))
  })
  it.each([null, NaN, Infinity, -100, -120])('blocks missing or invalid adjusted returns (%s)', rate => {
    const rows = rising(); rows[rows.length - 4].dailyGrowthRate = rate
    expect(analyze(rows).action).toBe('UNAVAILABLE')
    expect(analyze(rows).blockers.join('')).toContain('日收益率缺失或无效')
  })
  it('blocks insufficient history, stale data and large historical gaps', () => {
    expect(analyze(rising().slice(-119)).blockers.join('')).toContain('至少需要 120')
    expect(analyze(rising().slice(0, -4)).blockers.join('')).toContain('新鲜度')
    const rows = rising().filter((_, i) => i < 150 || i > 175)
    expect(analyze(rows).blockers.join('')).toContain('14 天')
    expect(analyze([]).action).toBe('UNAVAILABLE')
  })
  it('blocks future, invalid and conflicting records rather than silently using them', () => {
    const rows = rising()
    expect(analyze([...rows, { ...rows[0], date: '2026-10-01' }]).blockers.join('')).toContain('未来日期')
    expect(analyze([...rows, { ...rows[0], date: '2026-02-30' }]).blockers.join('')).toContain('无效日期')
    expect(analyze([...rows, { ...rows[0], nav: 5 }]).blockers.join('')).toContain('不一致')
    expect(analyze([...rows, { ...rows[0], nav: 0 }]).action).toBe('UNAVAILABLE')
  })
  it('does not infer a full weekly candle from only one available daily value', () => {
    const rows = rising().filter(row => row.date < '2026-09-21' || row.date >= '2026-09-25')
    expect(analyze(rows).action).toBe('UNAVAILABLE')
    expect(analyze(rows).blockers.join('')).toContain('周的净值覆盖不足')
  })
  it('blocks unavailable calendar, holidays and unsupported fund types', () => {
    expect(analyze(rising(), { market: null }).action).toBe('UNAVAILABLE')
    expect(analyze(rising(), { market: { ...market, updateTime: '2026-09-29T14:00:00' } }).action).toBe('UNAVAILABLE')
    expect(analyze(rising(), { market: { ...market, markets: [{ market: 'A股', statusText: '未知', trading: false }] } }).action).toBe('UNAVAILABLE')
    expect(analyze(rising(), { market: { ...market, markets: [{ market: 'A股', statusText: '非交易日', trading: false }] } }).action).toBe('UNAVAILABLE')
    for (const fundType of ['', '货币型', '债券指数', 'QDII债券', 'FOF']) expect(analyze(rising(), { fundType }).action).toBe('UNAVAILABLE')
  })
  it('labels Beijing evaluation time and post-cutoff execution limits', () => {
    expect(shanghaiDateTime(new Date('2026-09-29T18:00:00Z'))).toBe('2026-09-30 02:00:00')
    expect(analyze(rising(), { now: new Date('2026-09-30T07:00:00Z') }).notes.join('')).toContain('15:00')
    expect(analyze(rising()).notes.join('')).toContain('不是今日收盘后的信号')
  })
})
