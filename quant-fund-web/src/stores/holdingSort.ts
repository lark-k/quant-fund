import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import type { FundHolding } from '@/types/domain'

export type HoldingSortKey = 'holdingAmount' | 'dailyProfit' | 'holdingProfit' | 'cumulativeProfit'
export type SortDirection = 'asc' | 'desc'
export interface HoldingSort { key: HoldingSortKey; direction: SortDirection }
const keys: HoldingSortKey[] = ['holdingAmount', 'dailyProfit', 'holdingProfit', 'cumulativeProfit']
const defaultSort = (): HoldingSort => ({ key: 'holdingAmount', direction: 'desc' })

export function sortHoldings(holdings: FundHolding[], sort: HoldingSort, cumulativeProfit: (holding: FundHolding) => number | undefined): FundHolding[] {
  const valueOf = (holding: FundHolding) => sort.key === 'cumulativeProfit' ? cumulativeProfit(holding) : holding[sort.key]
  return [...holdings].sort((a, b) => {
    const left = valueOf(a)
    const right = valueOf(b)
    const leftValid = typeof left === 'number' && Number.isFinite(left)
    const rightValid = typeof right === 'number' && Number.isFinite(right)
    // Missing values stay last in both directions; ties retain the original order.
    if (!leftValid) return rightValid ? 1 : 0
    if (!rightValid) return -1
    return (left - right) * (sort.direction === 'asc' ? 1 : -1)
  })
}

export const useHoldingSortStore = defineStore('holdingSort', () => {
  const auth = useAuthStore()
  const sort = ref<HoldingSort>(defaultSort())
  const storageWarning = ref('')
  const storageKey = computed(() => auth.user?.id != null ? `quantfund:holding-sort:v1:${auth.user.id}` : '')

  watch(storageKey, (key) => {
    sort.value = defaultSort()
    storageWarning.value = ''
    if (!key) return
    try {
      const saved = JSON.parse(localStorage.getItem(key) || 'null')
      if (saved && keys.includes(saved.key) && ['asc', 'desc'].includes(saved.direction)) {
        sort.value = { key: saved.key, direction: saved.direction }
      }
    } catch {
      // Invalid or unavailable storage must not prevent the dashboard from loading.
    }
  }, { immediate: true, flush: 'sync' })

  function setSort(key: HoldingSortKey, direction: SortDirection) {
    sort.value = { key, direction }
    if (!storageKey.value) return
    try {
      localStorage.setItem(storageKey.value, JSON.stringify(sort.value))
      storageWarning.value = ''
    } catch {
      storageWarning.value = '浏览器存储不可用，排序仅保留于本次访问'
    }
  }

  return { sort, storageWarning, setSort }
})
