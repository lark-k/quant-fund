import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { quantApi } from '@/api/quant'
import { useAuthStore } from '@/stores/auth'
import type { QuantSignal } from '@/types/domain'

export interface SignalNotification {
  id: string
  fundCode: string
  fundName: string
  action: 'BUY' | 'SELL'
  actionText: string
  reason: string
  publishedAt: string
  read: boolean
}

export const NOTIFICATION_LIMIT = 30
const dateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit'
})

// The server serializes LocalDateTime without an offset; its timezone is Shanghai.
export function signalTimestamp(value: string): number {
  return Date.parse(/[zZ]$|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value.replace(' ', 'T')}+08:00`)
}

function newest(items: SignalNotification[]): SignalNotification[] {
  return [...new Map(items.map((item) => [item.id, item])).values()]
    .sort((a, b) => signalTimestamp(b.publishedAt) - signalTimestamp(a.publishedAt) || b.id.localeCompare(a.id))
    .slice(0, NOTIFICATION_LIMIT)
}

function isNotification(value: unknown): value is SignalNotification {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  return ['id', 'fundCode', 'fundName', 'actionText', 'reason', 'publishedAt'].every((key) => typeof item[key] === 'string')
    && (item.action === 'BUY' || item.action === 'SELL') && typeof item.read === 'boolean'
    && Number.isFinite(signalTimestamp(item.publishedAt as string))
}

export const useNotificationsStore = defineStore('notifications', () => {
  const auth = useAuthStore()
  const items = ref<SignalNotification[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')
  const storageWarning = ref('')
  const storageKey = computed(() => auth.token && auth.user?.id != null
    ? `quantfund:notifications:v1:${auth.user.id}` : '')
  const unreadCount = computed(() => items.value.filter((item) => !item.read).length)
  let sessionVersion = 0
  let inFlight: Promise<void> | null = null

  function readStored(): SignalNotification[] {
    if (!storageKey.value) return []
    try {
      const saved: unknown = JSON.parse(localStorage.getItem(storageKey.value) || '[]')
      return Array.isArray(saved) ? newest(saved.filter(isNotification)) : []
    } catch {
      return []
    }
  }

  function persist() {
    if (!storageKey.value) return
    try {
      localStorage.setItem(storageKey.value, JSON.stringify(items.value))
      storageWarning.value = ''
    } catch {
      storageWarning.value = '浏览器存储不可用，阅读状态暂仅保留于本次访问'
    }
  }

  function syncFromStorage() {
    if (!storageKey.value) return
    const stored = readStored()
    const readIds = new Set([...stored, ...items.value].filter((item) => item.read).map((item) => item.id))
    items.value = newest([...items.value, ...stored]).map((item) => ({ ...item, read: readIds.has(item.id) }))
  }

  watch([() => auth.token, storageKey], () => {
    sessionVersion += 1
    inFlight = null
    loading.value = false
    loaded.value = false
    error.value = ''
    storageWarning.value = ''
    items.value = readStored()
  }, { immediate: true, flush: 'sync' })

  function ingestSignals(signals: QuantSignal[], now = new Date()) {
    if (!storageKey.value) return
    syncFromStorage()
    const known = new Set(items.value.map((item) => item.id))
    const today = dateFormatter.format(now)
    const incoming: SignalNotification[] = []
    for (const signal of signals) {
      if (signal.action !== 'BUY' && signal.action !== 'SELL') continue
      const timestamp = signalTimestamp(signal.signalTime)
      if (!Number.isFinite(timestamp) || timestamp > now.getTime() || dateFormatter.format(timestamp) !== today) continue
      // A recalculation can update an existing signal row with a new publication time.
      const id = `${signal.id}:${timestamp}`
      if (known.has(id)) continue
      known.add(id)
      incoming.push({
        id, fundCode: signal.fundCode, fundName: signal.fundName || signal.fundCode,
        action: signal.action, actionText: signal.actionText || (signal.action === 'BUY' ? '建议加仓' : '建议减仓'),
        reason: (signal.reasons || []).join('；'), publishedAt: signal.signalTime, read: false
      })
    }
    items.value = newest([...items.value, ...incoming])
    persist()
  }

  function markRead(id: string) {
    syncFromStorage()
    items.value = items.value.map((item) => item.id === id ? { ...item, read: true } : item)
    persist()
  }

  function markAllRead() {
    syncFromStorage()
    items.value = items.value.map((item) => ({ ...item, read: true }))
    persist()
  }

  function refresh(): Promise<void> {
    if (!storageKey.value) return Promise.resolve()
    if (inFlight) return inFlight
    const version = sessionVersion
    loading.value = true
    error.value = ''
    inFlight = Promise.resolve().then(() => {
      if (version !== sessionVersion) return
      return quantApi.quantSignals()
    }).then((signals) => {
      if (version !== sessionVersion || !signals) return
      ingestSignals(signals)
      loaded.value = true
    }).catch(() => {
      if (version === sessionVersion) error.value = '通知同步失败，点击重试'
    }).finally(() => {
      if (version !== sessionVersion) return
      loading.value = false
      inFlight = null
    })
    return inFlight
  }

  return { items, unreadCount, loading, loaded, error, storageWarning, storageKey, ingestSignals,
    refresh, markRead, markAllRead, syncFromStorage }
})
