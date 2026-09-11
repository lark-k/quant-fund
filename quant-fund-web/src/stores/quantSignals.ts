import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { quantApi } from '@/api/quant'
import { useAuthStore } from '@/stores/auth'
import type { QuantSignal } from '@/types/domain'

// Keep the last successful result when the dashboard is unmounted. Revalidate
// on entry without persisting investment data beyond the current login session.
export const useQuantSignalsStore = defineStore('quantSignals', () => {
  const auth = useAuthStore()
  const signals = ref<QuantSignal[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')
  let inFlight: Promise<void> | null = null
  let sessionVersion = 0

  watch(() => auth.token, () => {
    sessionVersion += 1
    signals.value = []
    loading.value = false
    loaded.value = false
    error.value = ''
    inFlight = null
  }, { flush: 'sync' })

  function fetchSignals(): Promise<void> {
    if (inFlight) return inFlight
    const version = sessionVersion
    loading.value = true
    error.value = ''
    inFlight = Promise.resolve()
      .then(() => {
        if (version !== sessionVersion) return
        return quantApi.quantSignals()
      })
      .then((result) => {
        if (version !== sessionVersion || !result) return
        signals.value = result.slice()
          .sort((left, right) => Date.parse(right.signalTime) - Date.parse(left.signalTime))
        loaded.value = true
      })
      .catch(() => {
        if (version !== sessionVersion) return
        error.value = '量化建议加载失败，请稍后重试'
      })
      .finally(() => {
        if (version !== sessionVersion) return
        loading.value = false
        inFlight = null
      })
    return inFlight
  }

  return { signals, loading, loaded, error, fetchSignals }
})
