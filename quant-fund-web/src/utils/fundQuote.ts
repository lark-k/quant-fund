import type { TradeRecord } from '@/types/domain'

export interface QuoteNav {
  date: string
  nav: number
  accumulatedNav: number | null
  dailyGrowthRate: number | null
  indexReturnRate: number | null
  indexName?: string
  sourceName?: string
}
export type QuotePeriod = 'day' | 'week' | 'month'
export type QuoteBasis = 'unit' | 'adjusted'
export interface QuotePoint extends QuoteNav { value: number | null; factor: number | null }
export interface QuoteBar {
  date: string
  start: string
  end: string
  open: number | null
  close: number | null
  low: number | null
  high: number | null
  growth: number | null
  drawdown: number | null
  index: number | null
}

export function numeric(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null
  const result = Number(value)
  return Number.isFinite(result) ? result : null
}

export function normalizeNav(rows: Array<Record<string, unknown>>): QuoteNav[] {
  const unique = new Map<string, QuoteNav>()
  for (const row of rows) {
    const date = String(row.date || row.navDate || '').slice(0, 10)
    const nav = numeric(row.nav ?? row.unitNav)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || nav === null || nav <= 0) continue
    unique.set(date, { date, nav, accumulatedNav: numeric(row.accumulatedNav),
      dailyGrowthRate: numeric(row.dailyGrowthRate), indexReturnRate: numeric(row.indexReturnRate),
      indexName: row.indexName ? String(row.indexName) : undefined,
      sourceName: row.sourceName ? String(row.sourceName) : undefined })
  }
  return [...unique.values()].sort((a, b) => a.date.localeCompare(b.date))
}

// Each server request has its own index baseline. Join only with a shared observation;
// without an overlap, keep the older benchmark unknown rather than invent continuity.
export function mergeHistory(current: QuoteNav[], older: QuoteNav[]): QuoteNav[] {
  const byDate = new Map(current.map(p => [p.date, p]))
  const overlap = older.find(p => p.indexReturnRate !== null && byDate.get(p.date)?.indexReturnRate != null)
  const denominator = overlap ? 1 + overlap.indexReturnRate! / 100 : 0
  const ratio = overlap && denominator > 0 ? (1 + byDate.get(overlap.date)!.indexReturnRate! / 100) / denominator : null
  for (const point of older) {
    if (!byDate.has(point.date)) byDate.set(point.date, { ...point,
      indexReturnRate: ratio !== null && point.indexReturnRate !== null ? ((1 + point.indexReturnRate / 100) * ratio - 1) * 100 : null })
  }
  return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date))
}

// Forward-adjusted NAV anchored at the latest official unit NAV, using published
// daily returns (dividend reinvestment basis). Accumulated NAV is NOT adjusted NAV.
export function quotePoints(rows: QuoteNav[], basis: QuoteBasis): QuotePoint[] {
  if (basis === 'unit') return rows.map(p => ({ ...p, value: p.nav, factor: 1 }))
  let value: number | null = rows[rows.length - 1]?.nav ?? null
  const result: QuotePoint[] = []
  for (let i = rows.length - 1; i >= 0; i--) {
    const point = rows[i]
    result.push({ ...point, value, factor: value === null ? null : value / point.nav })
    const rate = point.dailyGrowthRate
    value = value !== null && rate !== null && rate > -100 ? value / (1 + rate / 100) : null
  }
  return result.reverse()
}

export function periodKey(date: string, period: QuotePeriod): string {
  if (period === 'day') return date
  if (period === 'month') return date.slice(0, 7)
  const day = new Date(`${date}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() - (day.getUTCDay() + 6) % 7)
  return day.toISOString().slice(0, 10)
}

export function aggregateNav(points: QuotePoint[], period: QuotePeriod): QuoteBar[] {
  const groups = new Map<string, QuotePoint[]>()
  points.forEach(p => { const key = periodKey(p.date, period); groups.set(key, [...(groups.get(key) || []), p]) })
  let peak = 0
  return [...groups.values()].map(group => {
    const first = group[0]!, last = group[group.length - 1]!
    const values = group.map(p => p.value)
    const complete = values.every((v): v is number => v !== null)
    let drawdown: number | null = null
    group.forEach(p => { if (p.value !== null) { peak = Math.max(peak, p.value); drawdown = (p.value / peak - 1) * 100 } })
    const growth = group.every(p => p.dailyGrowthRate !== null)
      ? (group.reduce((factor, p) => factor * (1 + p.dailyGrowthRate! / 100), 1) - 1) * 100 : null
    return { date: last.date, start: first.date, end: last.date,
      open: complete ? first.value : null, close: complete ? last.value : null,
      low: complete ? Math.min(...values as number[]) : null, high: complete ? Math.max(...values as number[]) : null,
      growth, drawdown: complete ? drawdown : null, index: last.indexReturnRate }
  })
}

export function movingAverage(values: Array<number | null>, period: number): Array<number | null> {
  return values.map((_, i) => {
    if (i < period - 1) return null
    const window = values.slice(i - period + 1, i + 1)
    return window.every(v => v !== null) ? (window as number[]).reduce((a, b) => a + b, 0) / period : null
  })
}

function ema(values: Array<number | null>, period: number): Array<number | null> {
  let previous: number | null = null
  let count = 0
  return values.map(v => {
    if (v === null) { previous = null; count = 0; return null }
    previous = previous === null ? v : v * 2 / (period + 1) + previous * (1 - 2 / (period + 1))
    return ++count >= period ? previous : null
  })
}

export function macd(values: Array<number | null>) {
  const fast = ema(values, 12), slow = ema(values, 26)
  const dif = fast.map((v, i) => v !== null && slow[i] !== null ? v - slow[i]! : null)
  const dea = ema(dif, 9)
  return { dif, dea, histogram: dif.map((v, i) => v !== null && dea[i] !== null ? 2 * (v - dea[i]!) : null) }
}

export function rsi(values: Array<number | null>, period = 14): Array<number | null> {
  let gain = 0, loss = 0, count = 0
  return values.map((v, i) => {
    const previous = values[i - 1]
    if (v === null || previous == null) { gain = 0; loss = 0; count = 0; return null }
    const change = v - previous
    if (count < period) { gain += Math.max(change, 0) / period; loss += Math.max(-change, 0) / period; count++ }
    else { gain = (gain * (period - 1) + Math.max(change, 0)) / period; loss = (loss * (period - 1) + Math.max(-change, 0)) / period }
    return count < period ? null : gain + loss === 0 ? 50 : gain / (gain + loss) * 100
  })
}

export function matchingTrades(trades: TradeRecord[], holding: { id: number; accountId: number; fundCode: string }) {
  return trades.filter(t => t.fundCode === holding.fundCode && t.accountId === holding.accountId
    && (t.holdingId == null || t.holdingId === holding.id) && t.tradeStatus === 'COMPLETED')
}

export function dateBefore(date: string, days: number) {
  const result = new Date(`${date}T00:00:00Z`)
  result.setUTCDate(result.getUTCDate() - days)
  return result.toISOString().slice(0, 10)
}

export function monthsBefore(date: string, months: number) {
  const result = new Date(`${date}T00:00:00Z`), day = result.getUTCDate()
  result.setUTCDate(1)
  result.setUTCMonth(result.getUTCMonth() - months)
  const lastDay = new Date(Date.UTC(result.getUTCFullYear(), result.getUTCMonth() + 1, 0)).getUTCDate()
  result.setUTCDate(Math.min(day, lastDay))
  return result.toISOString().slice(0, 10)
}
