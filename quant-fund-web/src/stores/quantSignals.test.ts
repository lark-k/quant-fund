import { effectScope } from 'vue'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { quantApi } from '@/api/quant'
import { useAuthStore } from '@/stores/auth'
import { useQuantSignalsStore } from './quantSignals'
import type { QuantSignal } from '@/types/domain'

vi.mock('@/api/quant', () => ({ quantApi: { quantSignals: vi.fn() } }))
vi.mock('@/api/auth', () => ({ authApi: {} }))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const oldSignal = { id: 1, signalTime: '2026-09-11T10:00:00' } as QuantSignal
const newSignal = { id: 2, signalTime: '2026-09-11T14:55:00' } as QuantSignal

describe('dashboard quant signal cache', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    vi.resetAllMocks()
    pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().token = 'session-a'
  })

  afterEach(() => disposePinia(pinia))

  it('keeps results across page unmounts and refreshes them in the background', async () => {
    vi.mocked(quantApi.quantSignals).mockResolvedValueOnce([oldSignal])
    const page = effectScope()
    const store = page.run(() => useQuantSignalsStore())!
    await store.fetchSignals()
    page.stop()

    const pending = deferred<QuantSignal[]>()
    vi.mocked(quantApi.quantSignals).mockReturnValueOnce(pending.promise)
    const returnedPageStore = useQuantSignalsStore()
    const refreshing = returnedPageStore.fetchSignals()
    expect(returnedPageStore.signals).toEqual([oldSignal])
    expect(returnedPageStore.loaded).toBe(true)
    expect(returnedPageStore.loading).toBe(true)
    pending.resolve([oldSignal, newSignal])
    await refreshing
    expect(returnedPageStore.signals).toEqual([newSignal, oldSignal])
  })

  it('shares an unfinished request during rapid navigation', async () => {
    const pending = deferred<QuantSignal[]>()
    vi.mocked(quantApi.quantSignals).mockReturnValue(pending.promise)
    const store = useQuantSignalsStore()
    const first = store.fetchSignals()
    const second = useQuantSignalsStore().fetchSignals()
    await Promise.resolve()
    expect(quantApi.quantSignals).toHaveBeenCalledTimes(1)
    pending.resolve([newSignal])
    await Promise.all([first, second])
    expect(store.loading).toBe(false)
    expect(store.signals).toEqual([newSignal])
  })

  it('retains the last result after a failed refresh and allows retry', async () => {
    vi.mocked(quantApi.quantSignals)
      .mockResolvedValueOnce([oldSignal])
      .mockRejectedValueOnce(new Error('network timeout'))
      .mockResolvedValueOnce([newSignal])
    const store = useQuantSignalsStore()
    await store.fetchSignals()
    await store.fetchSignals()
    expect(store.signals).toEqual([oldSignal])
    expect(store.error).toBeTruthy()
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
    await store.fetchSignals()
    expect(store.error).toBe('')
    expect(store.signals).toEqual([newSignal])
  })

  it('distinguishes first-load failure from a successful empty result', async () => {
    vi.mocked(quantApi.quantSignals)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([])
    const store = useQuantSignalsStore()
    const loading = store.fetchSignals()
    expect(store.loaded).toBe(false)
    expect(store.loading).toBe(true)
    await loading
    expect(store.loaded).toBe(false)
    expect(store.error).toBeTruthy()
    await store.fetchSignals()
    expect(store.loaded).toBe(true)
    expect(store.error).toBe('')
    expect(store.signals).toEqual([])
  })

  it('replaces cached rows when a successful refresh returns no signals', async () => {
    vi.mocked(quantApi.quantSignals).mockResolvedValueOnce([oldSignal]).mockResolvedValueOnce([])
    const store = useQuantSignalsStore()
    await store.fetchSignals()
    await store.fetchSignals()
    expect(store.signals).toEqual([])
    expect(store.loaded).toBe(true)
  })

  it('clears data on logout and ignores the previous session response', async () => {
    const oldRequest = deferred<QuantSignal[]>()
    const newRequest = deferred<QuantSignal[]>()
    vi.mocked(quantApi.quantSignals)
      .mockResolvedValueOnce([oldSignal])
      .mockReturnValueOnce(oldRequest.promise)
      .mockReturnValueOnce(newRequest.promise)
    const page = effectScope()
    const store = page.run(() => useQuantSignalsStore())!
    await store.fetchSignals()
    page.stop()
    const stale = store.fetchSignals()
    await Promise.resolve()
    useAuthStore().logout()
    expect(store.signals).toEqual([])
    expect(store.loaded).toBe(false)
    useAuthStore().token = 'session-b'
    const current = store.fetchSignals()
    oldRequest.resolve([oldSignal])
    await stale
    expect(store.signals).toEqual([])
    expect(store.loading).toBe(true)
    newRequest.resolve([newSignal])
    await current
    expect(store.signals).toEqual([newSignal])
    expect(store.loading).toBe(false)
  })
})
