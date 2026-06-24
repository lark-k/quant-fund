import { defineStore } from 'pinia'
import { quantApi } from '@/api/quant'
import type { DashboardOverview } from '@/types/domain'

interface DashboardState {
  loading: boolean
  error: string
  overview: DashboardOverview | null
}

export const useDashboardStore = defineStore('dashboard', {
  state: (): DashboardState => ({
    loading: false,
    error: '',
    overview: null
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
      } catch (error) {
        this.error = error instanceof Error ? error.message : '驾驶舱数据加载失败'
      } finally {
        this.loading = false
      }
    }
  }
})
