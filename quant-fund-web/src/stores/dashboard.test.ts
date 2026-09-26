import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, it, vi } from 'vitest'
import { quantApi } from '@/api/quant'
import { useDashboardStore } from './dashboard'
import type { DashboardOverview } from '@/types/domain'

vi.mock('@/api/quant', () => ({ quantApi: { dashboard: vi.fn(), cumulativeProfit: vi.fn(), syncOfficialNav: vi.fn() } }))
beforeEach(() => { vi.resetAllMocks(); setActivePinia(createPinia()) })

it('keeps existing dashboard calculations when the separate cumulative endpoint fails', async () => {
  const overview = { summary: { currentProfit: -334.91, dailyProfit: 0, totalAsset: 5771.64 } } as DashboardOverview
  vi.mocked(quantApi.dashboard).mockResolvedValue(overview)
  vi.mocked(quantApi.cumulativeProfit).mockRejectedValue(new Error('unavailable'))
  const store = useDashboardStore()
  await store.fetchOverview()
  expect(store.overview).toEqual(overview)
  expect(store.error).toBe('')
  expect(store.cumulative).toBeNull()
  expect(store.cumulativeError).toContain('累计收益暂未更新')
})

it('retains last settled profit on a refresh failure and accepts the next official result', async () => {
  vi.mocked(quantApi.dashboard).mockResolvedValue({} as DashboardOverview)
  vi.mocked(quantApi.cumulativeProfit)
    .mockResolvedValueOnce({ totalCumulativeProfit: -765.51, historicalProfit: -232.13, funds: [] })
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce({ totalCumulativeProfit: -755.51, historicalProfit: -232.13, funds: [] })
  const store = useDashboardStore()
  await store.fetchOverview(); await store.fetchOverview()
  expect(store.cumulative?.totalCumulativeProfit).toBe(-765.51)
  await store.fetchOverview()
  expect(store.cumulative?.totalCumulativeProfit).toBe(-755.51)
  expect(store.cumulativeError).toBe('')
})
