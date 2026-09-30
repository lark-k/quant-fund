import { describe, expect, it } from 'vitest'
import { aggregateNav, matchingTrades, mergeHistory, monthsBefore, movingAverage, normalizeNav, periodKey, quotePoints, rsi, macd, type QuoteNav } from './fundQuote'
import { buildQuoteChart } from './fundQuoteChart'
import type { TradeRecord } from '@/types/domain'

const nav = (date: string, value: number, rate: number | null = 0, index: number | null = null): QuoteNav => ({ date, nav: value, accumulatedNav: null, dailyGrowthRate: rate, indexReturnRate: index })
const trade = (overrides: Partial<TradeRecord> = {}): TradeRecord => ({ id: 1, accountId: 1, holdingId: 10, fundCode: '001', fundName: '测试', tradeType: 'BUY', tradeStatus: 'COMPLETED', tradeAmount: 100, tradeShare: 100, tradeNav: 1, tradeFee: 0, tradeTime: '2026-01-05T15:00:00', simulatedTradeNotice: '', ...overrides })

describe('fund quote financial data', () => {
  it('keeps missing returns unknown and sorts/deduplicates official NAV', () => {
    expect(normalizeNav([{ navDate: '2026-01-02', unitNav: '1.2' }, { date: '2026-01-01', nav: 1 }, { date: '2026-01-02', nav: 1.3 }, { date: 'bad', nav: 10 }, { date: '2026-01-03', nav: 0 }])).toEqual([
      expect.objectContaining({ date: '2026-01-01', nav: 1, dailyGrowthRate: null }), expect.objectContaining({ date: '2026-01-02', nav: 1.3 })
    ])
  })
  it('adjusts dividend and split discontinuities using published returns, anchored at latest NAV', () => {
    const result = quotePoints([nav('2026-01-01', 2), nav('2026-01-02', 1, 0), nav('2026-01-05', 1.1, 10)], 'adjusted')
    expect(result.map(p => p.value)).toEqual([1, 1, 1.1])
    expect(result[0]!.factor).toBe(.5)
  })
  it('does not invent adjusted history across a missing return', () => {
    const points = quotePoints([nav('2026-01-01', 2), nav('2026-01-02', 1, null), nav('2026-01-05', 1.1, 10)], 'adjusted')
    expect(points.map(p => p.value)).toEqual([null, 1, 1.1])
  })
  it('aggregates actual first/last/min/max observations across ISO-week year boundary', () => {
    expect(periodKey('2026-01-01', 'week')).toBe('2025-12-29')
    const bars = aggregateNav(quotePoints([nav('2025-12-29', 2), nav('2025-12-30', 3, 50), nav('2026-01-02', 1.5, -50), nav('2026-01-05', 2, 33.3333)], 'unit'), 'week')
    expect(bars[0]).toMatchObject({ open: 2, close: 1.5, low: 1.5, high: 3, growth: -25, drawdown: -50 })
    expect(bars).toHaveLength(2)
  })
  it('stitches separate benchmark baselines only through matching dates', () => {
    const current = [nav('2026-01-02', 1, 0, 10), nav('2026-01-05', 1, 0, 21)]
    const result = mergeHistory(current, [nav('2026-01-01', 1, 0, 0), nav('2026-01-02', 1, 0, 10)])
    expect(result.map(p => p.indexReturnRate)).toEqual([0, 10, 21])
    const rebased = mergeHistory(current, [nav('2026-01-01', 1, 0, -10), nav('2026-01-02', 1, 0, 0)])
    expect(rebased[0]!.indexReturnRate).toBeCloseTo(-1)
    expect(mergeHistory(current, [nav('2025-12-01', 1, 0, 5)])[0]!.indexReturnRate).toBeNull()
  })
  it('isolates accounts, holdings and completed transactions', () => {
    expect(matchingTrades([trade(), trade({ id: 2, accountId: 2 }), trade({ id: 3, holdingId: 11 }), trade({ id: 4, tradeStatus: 'PROCESSING' }), trade({ id: 5, tradeStatus: 'CANCELLED' })], { id: 10, accountId: 1, fundCode: '001' }).map(t => t.id)).toEqual([1])
  })
  it('does not relocate weekend transactions; scales marker NAV on adjusted charts', () => {
    const points = quotePoints([nav('2026-01-02', 2), nav('2026-01-05', 1, 0)], 'adjusted')
    const chart = buildQuoteChart({ points, period: 'day', startDate: '2026-01-01', averages: [], cost: 1, trades: [trade({ tradeTime: '2026-01-03', tradeNav: 2 }), trade({ id: 2, tradeTime: '2026-01-02', tradeNav: 2 })], growth: true, drawdown: true, indicator: 'MACD', compare: true, indexName: '参考', adjusted: true })
    expect(chart.markerCount).toBe(1)
    const series = chart.option.series as Array<{ name: string; data: unknown[] }>
    expect(series.find(s => s.name === '买入记录')!.data).toEqual([expect.objectContaining({ value: ['2026-01-02', 1], tradeId: 2 })])
    expect((chart.option.grid as unknown[]).length).toBe(5)
  })
  it('leaves warmup/gaps empty and handles flat RSI and MACD', () => {
    expect(movingAverage([1, 2, null, 4, 5], 2)).toEqual([null, 1.5, null, null, 4.5])
    const values = Array(50).fill(1)
    expect(rsi(values)[13]).toBeNull()
    expect(rsi(values)[14]).toBe(50)
    expect(macd(values).histogram[32]).toBeNull()
    expect(macd(values).histogram[33]).toBe(0)
  })
  it('clamps calendar month ranges at month end', () => {
    expect(monthsBefore('2026-03-31', 1)).toBe('2026-02-28')
    expect(monthsBefore('2024-03-31', 1)).toBe('2024-02-29')
  })
})
