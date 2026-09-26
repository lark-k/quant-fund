import { defineStore } from 'pinia'
import { quantApi } from '@/api/quant'
import type { CumulativeProfitOverview, DashboardOverview } from '@/types/domain'

interface DashboardState {
  loading: boolean
  error: string
  overview: DashboardOverview | null
  cumulative: CumulativeProfitOverview | null
  cumulativeError: string
}

export const useDashboardStore = defineStore('dashboard', {
  state: (): DashboardState => ({
    loading: false,
    error: '',
    overview: null,
    cumulative: null,
    cumulativeError: ''
  }),
  actions: {
    async fetchOverview(syncOfficialNav = false) {
      this.loading = true
      this.error = ''
      try {
        if (syncOfficialNav) {
          await quantApi.syncOfficialNav().catch(() => undefined)
        }
        this.overview = await quantApi.dashboard()
        try {
          this.cumulative = await quantApi.cumulativeProfit()
          this.cumulativeError = ''
        } catch {
          this.cumulativeError = '累计收益暂未更新，请稍后重试'
        }
      } catch (error) {
        this.error = error instanceof Error ? error.message : '驾驶舱数据加载失败'
      } finally {
        this.loading = false
      }
    }
  }
})
