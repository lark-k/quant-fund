import { describe, expect, it } from 'vitest'
import type { MarketSessionStatus } from '@/types/domain'
import { isAShareEstimateRefreshAllowed } from './marketSession'

function session(statusText: string, trading = false): MarketSessionStatus {
  return {
    primaryStatusText: statusText,
    trading,
    updateTime: '2026-10-01T10:00:00',
    markets: [{ market: 'A股', statusText, trading }]
  }
}

describe('dashboard intraday estimate refresh', () => {
  it.each(['2026-10-01T10:00:00', '2026-10-01T12:00:00', '2026-10-02T14:00:00'])(
    'does not refresh on a weekday holiday at %s', (time) => {
      expect(isAShareEstimateRefreshAllowed(session('非交易日'), new Date(time))).toBe(false)
    }
  )

  it('uses the A-share session when another market is trading', () => {
    const status = session('非交易日')
    status.trading = true
    status.markets.push({ market: '港股', statusText: '港股交易中', trading: true })
    expect(isAShareEstimateRefreshAllowed(status, new Date('2026-10-01T10:00:00'))).toBe(false)
  })

  it('does not infer a trading day from missing or unknown market status', () => {
    const now = new Date('2026-10-01T10:00:00')
    expect(isAShareEstimateRefreshAllowed(null, now)).toBe(false)
    expect(isAShareEstimateRefreshAllowed({ ...session('已收盘'), markets: [] }, now)).toBe(false)
    expect(isAShareEstimateRefreshAllowed(session('状态未同步'), now)).toBe(false)
    expect(isAShareEstimateRefreshAllowed(session('未开盘'), now)).toBe(false)
  })

  it('allows refreshing while the A-share market is trading', () => {
    expect(isAShareEstimateRefreshAllowed(session('A股交易中', true), new Date('2026-09-30T10:00:00'))).toBe(true)
  })

  it('preserves refreshing during a trading-day lunch break', () => {
    expect(isAShareEstimateRefreshAllowed(session('已收盘'), new Date('2026-09-30T12:00:00'))).toBe(true)
  })

  it.each(['2026-09-30T09:29:00', '2026-09-30T15:01:00', '2026-10-03T10:00:00'])(
    'does not extend a closed session outside the weekday daytime window at %s', (time) => {
      expect(isAShareEstimateRefreshAllowed(session('已收盘'), new Date(time))).toBe(false)
    }
  )
})
