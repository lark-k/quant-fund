import { describe, expect, it } from 'vitest'
import { reallocateCash } from './fundCashAllocation'

describe('fund cash allocation', () => {
  it('deducts increases from unallocated and returns reductions', () => {
    expect(reallocateCash(1000, 0, 300)).toEqual({ ok: true, unallocated: 700, balance: 300 })
    expect(reallocateCash(700, 300, 100)).toEqual({ ok: true, unallocated: 900, balance: 100 })
    expect(reallocateCash(900, 100, 0)).toEqual({ ok: true, unallocated: 1000, balance: 0 })
  })
  it('conserves funds across multiple allocations and decimal amounts', () => {
    const first = reallocateCash(1000, 0, 333.33)
    if (!first.ok) throw new Error('first allocation failed')
    const second = reallocateCash(first.unallocated, 0, 666.67)
    expect(second).toEqual({ ok: true, unallocated: 0, balance: 666.67 })
    expect(reallocateCash(0.3, 0, 0.1)).toEqual({ ok: true, unallocated: 0.2, balance: 0.1 })
  })
  it('rejects overspending and invalid inputs without creating cash', () => {
    expect(reallocateCash(50, 100, 151)).toEqual({ ok: false, reason: 'insufficient' })
    for (const value of [NaN, Infinity, -1]) expect(reallocateCash(50, 100, value).ok).toBe(false)
  })
  it('funding a recorded deficit also consumes unallocated cash', () => {
    expect(reallocateCash(200, -100, 0)).toEqual({ ok: true, unallocated: 100, balance: 0 })
    expect(reallocateCash(50, -100, 0).ok).toBe(false)
  })
})
