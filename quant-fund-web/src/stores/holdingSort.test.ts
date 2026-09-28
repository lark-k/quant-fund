import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { sortHoldings, useHoldingSortStore, type HoldingSortKey } from './holdingSort'
import type { FundHolding, UserProfile } from '@/types/domain'

vi.mock('@/api/auth', () => ({ authApi: {} }))

describe('dashboard holding sort', () => {
  let pinia: ReturnType<typeof createPinia>
  let saved: Map<string, string>
  const login = (id: number) => useAuthStore().$patch({ token: `user-${id}`, user: { id } as UserProfile })
  beforeEach(() => {
    saved = new Map()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => saved.get(key) ?? null,
      setItem: (key: string, value: string) => saved.set(key, value)
    })
    pinia = createPinia()
    setActivePinia(pinia)
    login(1)
  })
  afterEach(() => { disposePinia(pinia); vi.unstubAllGlobals() })

  it.each<HoldingSortKey>(['holdingAmount', 'dailyProfit', 'holdingProfit', 'cumulativeProfit'])('sorts %s numerically in both directions without changing source order', (key) => {
    const rows = [10, -20, 0, -20].map((value, index) => ({ id: index, [key]: value }) as unknown as FundHolding)
    const cumulative = (row: FundHolding) => [10, -20, 0, -20][row.id]
    expect(sortHoldings(rows, { key, direction: 'asc' }, cumulative).map(row => row.id)).toEqual([1, 3, 2, 0])
    expect(sortHoldings(rows, { key, direction: 'desc' }, cumulative).map(row => row.id)).toEqual([0, 2, 1, 3])
    expect(rows.map(row => row.id)).toEqual([0, 1, 2, 3])
  })

  it('uses holding profit amount even when the return rates imply the opposite order', () => {
    const rows = [
      { id: 1, holdingAmount: 1000, holdingProfit: -100, holdingProfitRate: -10 },
      { id: 2, holdingAmount: 10000, holdingProfit: -200, holdingProfitRate: -2 }
    ] as FundHolding[]
    expect(sortHoldings(rows, { key: 'holdingProfit', direction: 'asc' }, () => undefined).map(row => row.id)).toEqual([2, 1])
    expect(sortHoldings(rows, { key: 'holdingProfit', direction: 'desc' }, () => undefined).map(row => row.id)).toEqual([1, 2])
  })

  it('keeps missing cumulative values last and reorders when cumulative data arrives', () => {
    const rows = [0, 1, 2, 3].map(id => ({ id }) as FundHolding)
    const values = [undefined, -3, 0, NaN]
    for (const direction of ['asc', 'desc'] as const) {
      const ids = sortHoldings(rows, { key: 'cumulativeProfit', direction }, row => values[row.id]).map(row => row.id)
      expect(ids).toEqual(direction === 'asc' ? [1, 2, 0, 3] : [2, 1, 0, 3])
    }
    values[0] = 5
    expect(sortHoldings(rows, { key: 'cumulativeProfit', direction: 'desc' }, row => values[row.id]).map(row => row.id)).toEqual([0, 2, 1, 3])
    expect(sortHoldings([], { key: 'dailyProfit', direction: 'asc' }, () => undefined)).toEqual([])
  })

  it('restores field and direction after recreation and isolates users through logout and login', () => {
    let store = useHoldingSortStore()
    store.setSort('holdingProfit', 'asc')
    disposePinia(pinia)
    pinia = createPinia()
    setActivePinia(pinia)
    login(1)
    store = useHoldingSortStore()
    expect(store.sort).toEqual({ key: 'holdingProfit', direction: 'asc' })
    useAuthStore().logout()
    login(2)
    expect(store.sort).toEqual({ key: 'holdingAmount', direction: 'desc' })
    store.setSort('cumulativeProfit', 'desc')
    login(1)
    expect(store.sort).toEqual({ key: 'holdingProfit', direction: 'asc' })
    login(2)
    expect(store.sort).toEqual({ key: 'cumulativeProfit', direction: 'desc' })
  })

  it.each(['{broken', 'null', '{"key":"fundName","direction":"asc"}', '{"key":"dailyProfit","direction":"invalid"}'])('falls back safely for invalid preference %s', (value) => {
    saved.set('quantfund:holding-sort:v1:1', value)
    expect(useHoldingSortStore().sort).toEqual({ key: 'holdingAmount', direction: 'desc' })
  })

  it('keeps sorting usable and reports when persistence is unavailable', () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('blocked') }, setItem: () => { throw new Error('quota') } })
    const store = useHoldingSortStore()
    store.setSort('dailyProfit', 'asc')
    expect(store.sort).toEqual({ key: 'dailyProfit', direction: 'asc' })
    expect(store.storageWarning).toContain('本次访问')
  })
})
