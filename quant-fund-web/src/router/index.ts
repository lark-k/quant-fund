import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import TerminalLayout from '@/layouts/TerminalLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/',
    component: TerminalLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '首页总览' } },
      { path: 'holdings', name: 'holdings', component: () => import('@/views/portfolio/HoldingsView.vue'), meta: { title: '持仓列表' } },
      { path: 'holding-edit', name: 'holding-edit', component: () => import('@/views/portfolio/HoldingEditView.vue'), meta: { title: '持仓编辑' } },
      { path: 'fund-detail', name: 'fund-detail', component: () => import('@/views/fund/FundDetailView.vue'), meta: { title: '基金详情' } },
      { path: 'fund-screener', name: 'fund-screener', component: () => import('@/views/fund/FundScreenerView.vue'), meta: { title: '基金优选' } },
      { path: 'ai-analysis', name: 'ai-analysis', component: () => import('@/views/analysis/AiAnalysisView.vue'), meta: { title: '智能分析' } },
      { path: 'profit-analysis', name: 'profit-analysis', component: () => import('@/views/analysis/ProfitAnalysisView.vue'), meta: { title: '收益分析' } },
      { path: 'profit-calendar', name: 'profit-calendar', component: () => import('@/views/analysis/ProfitCalendarView.vue'), meta: { title: '收益日历' } },
      { path: 'backtest-validation', name: 'backtest-validation', component: () => import('@/views/backtest/BacktestValidationView.vue'), meta: { title: '回测验证' } },
      { path: 'trades', name: 'trades', component: () => import('@/views/trade/TradesView.vue'), meta: { title: '交易流水' } },
      { path: 'strategy-config', name: 'strategy-config', component: () => import('@/views/profile/StrategyConfigView.vue'), meta: { title: '策略配置' } },
      { path: 'system-config', name: 'system-config', component: () => import('@/views/profile/SystemConfigView.vue'), meta: { title: '系统配置' } },
      { path: 'profile', name: 'profile', component: () => import('@/views/profile/ProfileView.vue'), meta: { title: '个人中心' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const CHUNK_RELOAD_KEY_PREFIX = 'quantfund:chunk-reload:'
const CHUNK_LOAD_ERROR_PATTERN = /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Loading chunk [\w-]+ failed/i

function chunkReloadKey(path: string) {
  return `${CHUNK_RELOAD_KEY_PREFIX}${path}`
}

router.onError((error, to) => {
  const message = error instanceof Error ? error.message : String(error)
  if (!CHUNK_LOAD_ERROR_PATTERN.test(message)) return

  const target = to.fullPath || window.location.pathname + window.location.search + window.location.hash
  const reloadKey = chunkReloadKey(target)
  try {
    if (window.sessionStorage.getItem(reloadKey)) {
      window.sessionStorage.removeItem(reloadKey)
      return
    }
    window.sessionStorage.setItem(reloadKey, '1')
  } catch {
    // Reload is still safe when session storage is unavailable.
  }
  window.location.replace(target)
})

router.afterEach((to) => {
  try {
    window.sessionStorage.removeItem(chunkReloadKey(to.fullPath))
  } catch {
    // Ignore browsers that block session storage.
  }
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return '/login'
  }
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return '/dashboard'
  }
  return true
})

export default router
