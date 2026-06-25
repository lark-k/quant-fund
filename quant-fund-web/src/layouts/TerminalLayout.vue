<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Bell,
  Calendar,
  DataAnalysis,
  Document,
  Fold,
  Grid,
  Histogram,
  Money,
  Operation,
  Refresh,
  Setting,
  SwitchButton,
  TrendCharts,
  UserFilled
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { quantApi } from '@/api/quant'
import { money, percent, toneClass } from '@/utils/format'
import type { MarketIndex, MarketSessionStatus } from '@/types/domain'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const now = ref(new Date())
const marketReadings = ref<MarketIndex[]>([])
const marketStatus = ref<MarketSessionStatus | null>(null)

const navItems = [
  { to: '/dashboard', label: '首页总览', icon: Grid },
  { to: '/holdings', label: '持仓列表', icon: Money },
  { to: '/holding-edit', label: '持仓编辑', icon: Operation },
  { to: '/fund-detail', label: '基金详情', icon: Document },
  { to: '/ai-analysis', label: '智能分析', icon: DataAnalysis },
  { to: '/profit-analysis', label: '收益分析', icon: TrendCharts },
  { to: '/profit-calendar', label: '收益日历', icon: Calendar },
  { to: '/trades', label: '模拟交易', icon: Operation },
  { to: '/strategy-config', label: '策略配置', icon: Histogram },
  { to: '/system-config', label: '系统配置', icon: Setting }
]

const mobileNavItems = [
  { to: '/dashboard', label: '首页', icon: Grid },
  { to: '/holdings', label: '持仓', icon: Money },
  { to: '/ai-analysis', label: '智能', icon: DataAnalysis },
  { to: '/trades', label: '交易', icon: Operation },
  { to: '/profile', label: '我的', icon: UserFilled }
]

const pageTitle = computed(() => String(route.meta.title || '首页总览'))
const nowText = computed(() => now.value.toLocaleTimeString('zh-CN', { hour12: false }))
const dateText = computed(() => {
  return now.value.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short'
  })
})
const isTradingSession = computed(() => Boolean(marketStatus.value?.trading))
const marketStatusText = computed(() => marketStatus.value?.primaryStatusText || '市场状态同步中')
let clockTimer: number | undefined
let marketTimer: number | undefined

function updateClock() {
  now.value = new Date()
}

onMounted(() => {
  updateClock()
  clockTimer = window.setInterval(updateClock, 1000)
  void loadMarketReadings()
  marketTimer = window.setInterval(loadMarketReadings, 60000)
})

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
  if (marketTimer) window.clearInterval(marketTimer)
})

async function loadMarketReadings() {
  try {
    const [readings, status] = await Promise.all([
      quantApi.marketReadings(),
      quantApi.marketStatus()
    ])
    marketReadings.value = readings
    marketStatus.value = status
  } catch {
    marketReadings.value = []
  }
}

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="terminal-shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="terminal-sidebar">
      <div class="brand">
        <div class="brand-mark">Q</div>
        <div class="brand-text">
          <strong>QuantFund</strong>
          <span>基金量化驾驶舱</span>
        </div>
      </div>

      <nav class="side-nav" aria-label="主导航">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" class="nav-item">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="market-tape">
        <div class="tape-head">
          <span>市场读数</span>
          <el-icon><Setting /></el-icon>
        </div>
        <template v-if="marketReadings.length">
          <div v-for="index in marketReadings" :key="index.code" class="index-row">
            <span>{{ index.name }}</span>
            <b :class="toneClass(index.changeRate)">{{ money(index.latestPrice, 2) }}</b>
            <em :class="toneClass(index.changeRate)">{{ percent(index.changeRate) }}</em>
          </div>
        </template>
        <div v-else class="index-row">
          <span>真实行情</span>
          <b>同步中</b>
          <em>--</em>
        </div>
      </div>
    </aside>

    <main class="terminal-main">
      <header class="terminal-header">
        <button class="icon-button mobile-only" aria-label="折叠菜单" @click="collapsed = !collapsed">
          <el-icon><Fold /></el-icon>
        </button>
        <div>
          <h1>QuantFund - AI Fund Quant Dashboard</h1>
          <span class="page-kicker">{{ pageTitle }}</span>
        </div>
        <div class="header-status">
          <span>估算刷新</span>
          <el-icon><Refresh /></el-icon>
          <span :class="isTradingSession ? 'trade-open' : 'trade-closed'">{{ marketStatusText }} {{ nowText }}</span>
          <span>{{ dateText }}</span>
        </div>
        <div class="header-user">
          <button class="icon-button" aria-label="通知">
            <el-icon><Bell /></el-icon>
            <span class="notice-dot">8</span>
          </button>
          <RouterLink to="/profile" class="user-chip">
            <el-icon><UserFilled /></el-icon>
            <span>{{ auth.nickname }}</span>
          </RouterLink>
          <button class="icon-button" aria-label="退出登录" @click="logout">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </div>
      </header>

      <RouterView />
    </main>

    <nav class="mobile-tabbar" aria-label="移动端核心导航">
      <RouterLink v-for="item in mobileNavItems" :key="item.to" :to="item.to" class="mobile-tab">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>
