import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { quantApi } from '@/api/quant'
import { useAuthStore } from '@/stores/auth'
import { signalTimestamp, useNotificationsStore } from './notifications'
import type { QuantSignal, UserProfile } from '@/types/domain'

vi.mock('@/api/quant', () => ({ quantApi: { quantSignals: vi.fn() } }))
vi.mock('@/api/auth', () => ({ authApi: {} }))

function signal(id: number, overrides: Partial<QuantSignal> = {}): QuantSignal {
  return { id, fundCode: '005827', fundName: '测试基金', action: 'BUY', actionText: '建议小额加仓',
    reasons: ['趋势改善'], signalTime: `2026-09-11T10:${String(id % 60).padStart(2, '0')}:00`, ...overrides } as QuantSignal
}

describe('suggestion notifications', () => {
  let pinia: ReturnType<typeof createPinia>
  let saved: Map<string, string>
  const now = new Date('2026-09-11T16:00:00+08:00')

  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(now)
    saved = new Map()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => saved.get(key) ?? null,
      setItem: (key: string, value: string) => saved.set(key, value)
    })
    pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().$patch({ token: 'user-a', user: { id: 1 } as UserProfile })
  })

  afterEach(() => { disposePinia(pinia); vi.unstubAllGlobals(); vi.useRealTimers() })

  it('collects only today’s BUY and SELL publications in Shanghai time', () => {
    const store = useNotificationsStore()
    store.ingestSignals([
      signal(1), signal(2, { action: 'SELL' }), signal(3, { action: 'HOLD' }),
      signal(4, { action: 'WATCH' }), signal(5, { action: 'CONVERT' }),
      signal(6, { signalTime: '2026-09-10T23:59:00' }),
      signal(7, { signalTime: '2026-09-11T17:00:00' }),
      signal(8, { signalTime: 'invalid' }),
      signal(9, { signalTime: '2026-09-10T16:01:00Z' })
    ])
    expect(store.items.map((item) => item.action)).toEqual(['SELL', 'BUY', 'BUY'])
    expect(store.unreadCount).toBe(3)
    expect(signalTimestamp('2026-09-11T00:01:00')).toBe(Date.parse('2026-09-10T16:01:00Z'))
  })

  it('deduplicates refreshes and preserves read state after page reload and login', () => {
    const store = useNotificationsStore()
    store.ingestSignals([signal(1), signal(2)])
    store.markRead(store.items[0]!.id)
    store.ingestSignals([signal(1), signal(2)])
    expect(store.items).toHaveLength(2)
    expect(store.unreadCount).toBe(1)
    disposePinia(pinia)
    pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().$patch({ token: 'new-session', user: { id: 1 } as UserProfile })
    expect(useNotificationsStore().unreadCount).toBe(1)
    expect(useNotificationsStore().items).toHaveLength(2)
  })

  it('marks all read without deleting notifications and leaves later arrivals unread', () => {
    const store = useNotificationsStore()
    store.ingestSignals([signal(1), signal(2)])
    store.markAllRead()
    expect(store.unreadCount).toBe(0)
    expect(store.items).toHaveLength(2)
    store.ingestSignals([signal(1), signal(2), signal(3)])
    expect(store.unreadCount).toBe(1)
  })

  it('retains only the latest 30, persists the limit, and does not resurrect older unread records', () => {
    const store = useNotificationsStore()
    const input = Array.from({ length: 35 }, (_, i) => signal(i + 1))
    store.ingestSignals(input.reverse())
    expect(store.items).toHaveLength(30)
    expect(store.items[0]!.publishedAt).toBe(signal(35).signalTime)
    expect(store.items[29]!.publishedAt).toBe(signal(6).signalTime)
    expect(JSON.parse(saved.get(store.storageKey)!)).toHaveLength(30)
    store.markAllRead()
    store.ingestSignals(input)
    expect(store.unreadCount).toBe(0)
  })

  it('keeps historical notifications after midnight but does not ingest old suggestions', () => {
    const store = useNotificationsStore()
    store.ingestSignals([signal(1)])
    store.markAllRead()
    vi.setSystemTime(new Date('2026-09-12T00:02:00+08:00'))
    store.ingestSignals([signal(2), signal(3, { signalTime: '2026-09-12T00:01:00' })])
    expect(store.items).toHaveLength(2)
    expect(store.unreadCount).toBe(1)
  })

  it('treats a republished signal with the same row ID as a new notification', () => {
    const store = useNotificationsStore()
    store.ingestSignals([signal(1)])
    store.markAllRead()
    store.ingestSignals([signal(1, { action: 'SELL', signalTime: '2026-09-11T15:00:00' })])
    expect(store.items).toHaveLength(2)
    expect(store.items[0]!.action).toBe('SELL')
    expect(store.unreadCount).toBe(1)
  })

  it('isolates users and ignores a response from an old login session', async () => {
    const store = useNotificationsStore()
    store.ingestSignals([signal(1)])
    let resolve!: (value: QuantSignal[]) => void
    vi.mocked(quantApi.quantSignals).mockReturnValue(new Promise((done) => { resolve = done }))
    const request = store.refresh()
    await Promise.resolve()
    useAuthStore().logout()
    expect(store.items).toEqual([])
    useAuthStore().$patch({ token: 'user-b', user: { id: 2 } as UserProfile })
    resolve([signal(2)])
    await request
    expect(store.items).toEqual([])
    store.ingestSignals([signal(3)])
    useAuthStore().$patch({ token: 'user-a-again', user: { id: 1 } as UserProfile })
    expect(store.items.map((item) => item.publishedAt)).toEqual([signal(1).signalTime])
  })

  it('shares in-flight requests and preserves history after network failure', async () => {
    const store = useNotificationsStore()
    store.ingestSignals([signal(1)])
    vi.mocked(quantApi.quantSignals).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([signal(2)])
    await Promise.all([store.refresh(), store.refresh()])
    expect(quantApi.quantSignals).toHaveBeenCalledTimes(1)
    expect(store.items).toHaveLength(1)
    expect(store.error).toBeTruthy()
    await store.refresh()
    expect(store.items).toHaveLength(2)
    expect(store.error).toBe('')
  })

  it('merges other tabs’ read state before updating a message', () => {
    const store = useNotificationsStore()
    store.ingestSignals([signal(1), signal(2)])
    const otherTab = JSON.parse(saved.get(store.storageKey)!)
    otherTab[0].read = true
    saved.set(store.storageKey, JSON.stringify(otherTab))
    store.markRead(store.items[1]!.id)
    expect(store.unreadCount).toBe(0)
  })

  it('recovers from malformed storage and keeps the UI working when writes fail', () => {
    saved.set('quantfund:notifications:v1:1', '{bad json')
    const store = useNotificationsStore()
    expect(store.items).toEqual([])
    vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => { throw new Error('quota') } })
    store.ingestSignals([signal(1)])
    store.markAllRead()
    expect(store.items).toHaveLength(1)
    expect(store.unreadCount).toBe(0)
    expect(store.storageWarning).toBeTruthy()
  })
})
