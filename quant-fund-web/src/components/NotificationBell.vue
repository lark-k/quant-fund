<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Bell, Close, TopRight, BottomRight, Check } from '@element-plus/icons-vue'
import { useNotificationsStore, signalTimestamp } from '@/stores/notifications'
import { useQuantSignalsStore } from '@/stores/quantSignals'

const notifications = useNotificationsStore()
const signals = useQuantSignalsStore()
const route = useRoute()
const visible = ref(false)
const bell = ref<HTMLButtonElement>()
let timer: number | undefined
const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
})

function close(restoreFocus = false) {
  visible.value = false
  if (restoreFocus) bell.value?.focus()
}

function handleKey(event: KeyboardEvent) {
  if (event.key === 'Escape' && visible.value) close(true)
}

function refreshWhenVisible() {
  if (document.visibilityState === 'visible') void notifications.refresh()
}

function handleStorage(event: StorageEvent) {
  if (event.key === notifications.storageKey || event.key === null) notifications.syncFromStorage()
}

watch(() => signals.signals, (value) => notifications.ingestSignals(value))
watch(() => route.fullPath, () => close())
watch(() => notifications.storageKey, () => { close(); void notifications.refresh() })
watch(visible, (value) => { if (value) void notifications.refresh() })

onMounted(() => {
  void notifications.refresh()
  timer = window.setInterval(refreshWhenVisible, 60000)
  document.addEventListener('visibilitychange', refreshWhenVisible)
  document.addEventListener('keydown', handleKey)
  window.addEventListener('storage', handleStorage)
})

onBeforeUnmount(() => {
  window.clearInterval(timer)
  document.removeEventListener('visibilitychange', refreshWhenVisible)
  document.removeEventListener('keydown', handleKey)
  window.removeEventListener('storage', handleStorage)
})
</script>

<template>
  <el-popover
    v-model:visible="visible"
    trigger="click"
    placement="bottom-end"
    :width="400"
    :offset="14"
    :hide-after="0"
    popper-class="notification-popover"
    :popper-style="{ padding: '0', maxWidth: 'calc(100vw - 24px)' }"
  >
    <template #reference>
      <button
        ref="bell" type="button" class="icon-button notification-bell" :class="{ 'is-active': visible }"
        :aria-label="notifications.unreadCount ? `通知，${notifications.unreadCount} 条未读` : '通知，无未读消息'"
        :aria-expanded="visible" aria-controls="notification-panel" aria-haspopup="dialog"
      >
        <el-icon><Bell /></el-icon>
        <span v-if="notifications.unreadCount" class="notification-count" aria-hidden="true">{{ notifications.unreadCount }}</span>
      </button>
    </template>

    <section id="notification-panel" class="notification-panel" role="dialog" aria-label="建议通知">
      <header class="notification-header">
        <div class="notification-heading">
          <span class="notification-eyebrow">交易提醒</span>
          <div><h2>建议通知</h2><span class="notification-unread">{{ notifications.unreadCount ? `${notifications.unreadCount} 条未读` : '全部已读' }}</span></div>
        </div>
        <div class="notification-actions">
          <el-tooltip :content="notifications.unreadCount ? '一键全部标为已读' : '全部通知已读'" placement="bottom" :show-after="200" popper-class="notification-tooltip">
            <button type="button" class="notification-tool" aria-label="全部标为已读" :aria-disabled="!notifications.unreadCount" @click="notifications.markAllRead">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="m14 11 6-8a1.4 1.4 0 0 1 2 2l-7 7M9 10l7 5M9 10c-1 4-3 6-6 7 3 4 8 5 13-2ZM7 17l-1 2m4-1-1 3" />
              </svg>
            </button>
          </el-tooltip>
          <button type="button" class="notification-tool" aria-label="关闭通知" @click="close(true)"><el-icon><Close /></el-icon></button>
        </div>
      </header>

      <button v-if="notifications.error" type="button" class="notification-error" @click="notifications.refresh">{{ notifications.error }}</button>
      <div class="notification-list" :aria-busy="notifications.loading" tabindex="0" aria-label="通知列表，可滚动查看历史消息">
        <button
          v-for="item in notifications.items" :key="item.id" type="button"
          class="notification-item" :class="{ 'is-unread': !item.read }"
          :aria-label="`${item.read ? '已读' : '未读'}，${item.fundName}，${item.actionText}，${timeFormatter.format(signalTimestamp(item.publishedAt))}`"
          @click="notifications.markRead(item.id)"
        >
          <span class="notification-direction" :class="item.action === 'BUY' ? 'is-buy' : 'is-sell'">
            <el-icon><TopRight v-if="item.action === 'BUY'" /><BottomRight v-else /></el-icon>
          </span>
          <span class="notification-content">
            <span class="notification-title"><strong>{{ item.fundName }}</strong><span v-if="!item.read" class="notification-unread-dot" /></span>
            <span class="notification-meta"><span>{{ item.fundCode }}</span><span class="notification-action" :class="item.action === 'BUY' ? 'is-buy' : 'is-sell'">{{ item.action === 'BUY' ? '加仓建议' : '减仓建议' }}</span></span>
            <span class="notification-summary">{{ item.actionText }}</span>
            <span v-if="item.reason" class="notification-reason">{{ item.reason }}</span>
            <span class="notification-bottom"><time :datetime="new Date(signalTimestamp(item.publishedAt)).toISOString()">{{ timeFormatter.format(signalTimestamp(item.publishedAt)) }}</time><span v-if="item.read" class="notification-read"><el-icon><Check /></el-icon>已读</span></span>
          </span>
        </button>
        <div v-if="!notifications.items.length" class="notification-empty">
          <span class="notification-empty-icon"><el-icon><Bell /></el-icon></span>
          <strong>{{ notifications.loading ? '正在同步建议通知' : notifications.error ? '通知暂时无法加载' : '暂无买卖建议通知' }}</strong>
          <p>{{ notifications.error ? '请稍后重试' : '当天有新的加仓、减仓建议时，会在这里提醒你' }}</p>
        </div>
      </div>
      <footer class="notification-footer"><span>{{ notifications.storageWarning || '保留最近 30 条通知' }}</span><span v-if="!notifications.storageWarning">{{ notifications.items.length }} / 30</span></footer>
    </section>
  </el-popover>
</template>

<style scoped>
.notification-bell { transition: border-color .18s, color .18s, background .18s; }
.notification-bell:hover, .notification-bell.is-active { color: var(--cyan); border-color: #326171; background: #142b35; }
.notification-count { position: absolute; top: -7px; right: -6px; min-width: 18px; height: 18px; padding: 0 4px; display: grid; place-items: center; border-radius: 20px; background: var(--red); color: white; font-size: 10px; font-weight: 700; line-height: 1; box-shadow: 0 0 0 2px var(--bg); }
.notification-panel { color: var(--text); font-size: 12px; text-align: left; }
.notification-header { display: flex; align-items: center; justify-content: space-between; padding: 19px 20px 16px; border-bottom: 1px solid var(--line); }
.notification-eyebrow { color: var(--cyan); font-size: 10px; letter-spacing: 2px; }
.notification-heading > div { display: flex; align-items: center; gap: 10px; margin-top: 6px; }
.notification-heading h2 { margin: 0; color: var(--text); font-size: 17px; font-weight: 600; }
.notification-unread { color: var(--muted); font-size: 11px; }
.notification-actions { display: flex; gap: 4px; }
.notification-tool { display: grid; place-items: center; width: 30px; height: 30px; border: 1px solid transparent; border-radius: 7px; background: transparent; color: #9cafba; cursor: pointer; }
.notification-tool svg { width: 19px; height: 19px; }
.notification-tool:hover { background: #1b3440; border-color: #2d4b59; color: var(--cyan); }
.notification-tool[aria-disabled="true"] { color: var(--muted-2); }
.notification-list { max-height: min(440px, calc(100dvh - 270px)); overflow-y: auto; overscroll-behavior: contain; scrollbar-width: thin; scrollbar-color: #345260 transparent; }
.notification-item { display: flex; align-items: flex-start; gap: 12px; width: 100%; padding: 17px 20px; border: 0; border-bottom: 1px solid var(--line-soft); text-align: left; color: inherit; background: transparent; cursor: pointer; font: inherit; transition: background .18s; }
.notification-item:last-child { border-bottom: 0; }
.notification-item.is-unread { background: linear-gradient(105deg, rgba(33,199,217,.055), transparent 85%); }
.notification-item:hover { background: rgba(60,156,255,.075); }
.notification-direction { display: grid; place-items: center; flex: 0 0 33px; height: 33px; border-radius: 10px; font-size: 17px; }
.notification-direction.is-buy { color: var(--red); background: rgba(255,81,75,.10); border: 1px solid rgba(255,81,75,.17); }
.notification-direction.is-sell { color: var(--green); background: rgba(47,209,124,.10); border: 1px solid rgba(47,209,124,.17); }
.notification-content { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 7px; }
.notification-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.notification-title strong { font-size: 13px; font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; }
.notification-unread-dot { flex: 0 0 6px; height: 6px; border-radius: 50%; background: var(--cyan); box-shadow: 0 0 7px #21c7d933; }
.notification-meta { display: flex; align-items: center; gap: 10px; color: var(--muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.notification-action { padding: 2px 6px; border-radius: 4px; font-size: 10px; }
.notification-action.is-buy { color: #fa8e88; background: #ff514b12; }
.notification-action.is-sell { color: #76dca7; background: #2fd17c12; }
.notification-summary { color: #c6d5de; line-height: 1.6; overflow-wrap: anywhere; }
.notification-reason { color: #8fa5b2; font-size: 11px; line-height: 1.75; overflow-wrap: anywhere; }
.notification-bottom { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 6px; margin-top: 2px; color: var(--muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.notification-read { display: inline-flex; gap: 3px; align-items: center; color: var(--muted-2); }
.notification-footer { display: flex; justify-content: space-between; gap: 12px; padding: 12px 20px; border-top: 1px solid var(--line); color: var(--muted); font-size: 10px; }
.notification-empty { display: flex; flex-direction: column; align-items: center; gap: 14px; padding: 38px 24px; text-align: center; }
.notification-empty-icon { display: grid; place-items: center; width: 52px; height: 52px; border: 1px solid #2a414d; border-radius: 16px; color: var(--cyan); background: #152630; font-size: 23px; }
.notification-empty strong { color: #bbccd5; font-weight: 500; }
.notification-empty p { margin: 0; max-width: 240px; color: var(--muted); font-size: 11px; line-height: 1.8; }
.notification-error { width: 100%; border: 0; border-bottom: 1px solid var(--line); background: #ffb84d0d; padding: 10px; color: var(--amber); font: inherit; cursor: pointer; }
.notification-panel button:focus-visible, .notification-list:focus-visible, .notification-bell:focus-visible { outline: 2px solid var(--cyan); outline-offset: -2px; }
@media (prefers-reduced-motion: reduce) { .notification-bell, .notification-item { transition: none; } }
</style>

<style>
.el-popper.notification-popover { border: 1px solid #2b4655; border-radius: 14px; background: linear-gradient(155deg, #142731 0%, #101d25 40%, #0c171e 100%); box-shadow: 0 22px 64px #0009, 0 6px 18px #0005, inset 0 1px 0 #a6e2ff0d; }
.el-popper.notification-popover .el-popper__arrow::before { background: #142731; border-color: #2b4655; }
.el-popper.notification-tooltip.is-dark { background: #172a34; border: 1px solid #345361; color: #dce8ee; box-shadow: 0 6px 20px #0006; }
.el-popper.notification-tooltip.is-dark .el-popper__arrow::before { background: #172a34; border-color: #345361; }
</style>
