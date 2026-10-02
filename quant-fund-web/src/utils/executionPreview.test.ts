import { describe, expect, it } from 'vitest'
import { executionPreview } from './executionPreview'
import type { TechnicalExecution } from './fundTechnicalAdvice'

const plan: TechnicalExecution = {
  cashBalance: 1000, holdingShares: 123.4567, holdingAmount: 246.91, referenceNav: 2,
  navDate: '2026-09-30', strategyAssets: 1246.91, currentWeight: 19.8, targetWeight: 100,
  pendingTrades: 0, lastTradeDate: null, observationsSinceTrade: null, requiredInterval: 3,
  snapshotVersion: 1, snapshotAt: '2026-10-02T12:00:00', suggestedAmount: 500,
  suggestedShares: 0, estimatedHoldingAfter: 746.91, estimatedCashAfter: 500,
  direction: 'BUY', status: 'READY', reason: '', sizingVersion: 'FUND-CASH v1'
}
describe('read-only execution preview', () => {
  it('allows a larger user budget but exposes the exact funding shortfall', () => {
    const before = structuredClone(plan)
    const result = executionPreview(plan, 1500)!
    expect(result.extraCash).toBe(500)
    expect(result.cashAfter).toBe(-500)
    expect(result.changed).toBe(true)
    expect(plan).toEqual(before)
  })
  it('supports full liquidation with fractional shares despite rounded market value', () => {
    const sell = { ...plan, direction: 'SELL' as const }
    const result = executionPreview(sell, sell.holdingAmount, true)!
    expect(result.shares).toBe(sell.holdingShares)
    expect(result.holdingAfter).toBe(0)
    expect(result.cashAfter).toBeCloseTo(1246.9134)
    expect(executionPreview(sell, 247)).toBeNull()
    expect(executionPreview(sell, 100.00009)!.shares).toBe(50)
  })
  it('supports zero and rejects invalid or blocked previews', () => {
    expect(executionPreview(plan, 0)!.cashAfter).toBe(1000)
    for (const n of [NaN, Infinity, -1]) expect(executionPreview(plan, n)).toBeNull()
    expect(executionPreview({ ...plan, direction: 'NONE' }, 100)).toBeNull()
  })
  it('preserves the exact suggested shares when its cash estimate is rounded down', () => {
    const sell = { ...plan, direction: 'SELL' as const, suggestedAmount: 70.19, suggestedShares: 50, referenceNav: 1.4039 }
    expect(executionPreview(sell, 70.19)!.shares).toBe(50)
    expect(executionPreview(sell, 70.19)!.changed).toBe(false)
  })
})
