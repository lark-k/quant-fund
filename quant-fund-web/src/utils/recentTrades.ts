import type { TradeRecord } from '@/types/domain'

const RECENT_TRADES_KEY = 'quantfund:recent-trades'
const MAX_RECENT_TRADES = 20

function parseRecentTrades() {
  try {
    const raw = window.sessionStorage.getItem(RECENT_TRADES_KEY)
    return raw ? JSON.parse(raw) as TradeRecord[] : []
  } catch {
    return []
  }
}

function saveRecentTrades(trades: TradeRecord[]) {
  window.sessionStorage.setItem(RECENT_TRADES_KEY, JSON.stringify(trades.slice(0, MAX_RECENT_TRADES)))
}

export function rememberRecentTrades(records: TradeRecord | TradeRecord[]) {
  const nextRecords = Array.isArray(records) ? records : [records]
  const merged = [...nextRecords, ...parseRecentTrades()]
  const seen = new Set<number>()
  saveRecentTrades(merged.filter((record) => {
    if (seen.has(record.id)) return false
    seen.add(record.id)
    return true
  }))
}

export function mergeRecentTrades(records: TradeRecord[]) {
  const merged = [...records, ...parseRecentTrades()]
  const seen = new Set<number>()
  return merged.filter((record) => {
    if (seen.has(record.id)) return false
    seen.add(record.id)
    return true
  }).sort((left, right) => String(right.tradeTime || '').localeCompare(String(left.tradeTime || '')))
}

export function forgetRecentTrade(id: number) {
  saveRecentTrades(parseRecentTrades().filter((record) => record.id !== id))
}
