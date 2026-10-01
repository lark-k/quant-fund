import type { MarketSessionStatus } from '@/types/domain'

export function isAShareEstimateRefreshAllowed(status: MarketSessionStatus | null, now = new Date()) {
  const market = status?.markets.find((item) => item.market === 'A股')
  if (!market) return false
  if (market.trading) return true

  // The server reports lunch breaks as closed. Only extend that state within
  // the daytime window; a weekday alone cannot establish that it is a trading day.
  if (market.statusText !== '已收盘') return false
  const day = now.getDay()
  if (day === 0 || day === 6) return false
  const minutes = now.getHours() * 60 + now.getMinutes()
  return minutes >= 9 * 60 + 30 && minutes <= 15 * 60
}
