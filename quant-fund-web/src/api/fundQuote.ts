import { http, USE_MOCK } from './http'
import { quantApi } from './quant'
import { normalizeNav } from '@/utils/fundQuote'
import type { FundBasicInfo, FundEstimate, MarketSessionStatus, TradeRecord } from '@/types/domain'

// Kept separate from fundNav: the quote chart must preserve missing returns as null.
export const fundQuoteApi = {
  marketStatus(signal: AbortSignal): Promise<MarketSessionStatus> {
    return USE_MOCK ? quantApi.marketStatus() : http.get('/dashboard/market-status', { signal, suppressErrorMessage: true })
  },
  async nav(code: string, startDate: string, endDate: string, indexCode: string, signal: AbortSignal) {
    const rows = USE_MOCK ? await quantApi.fundNav(code) : await http.get(`/funds/${code}/nav`, {
      params: { startDate, endDate, indexCode }, signal, timeout: 90000, suppressErrorMessage: true
    })
    return normalizeNav(rows as unknown as Array<Record<string, unknown>>)
      .filter(p => p.date >= startDate && p.date <= endDate)
  },
  info(code: string, signal: AbortSignal): Promise<FundBasicInfo> {
    return USE_MOCK ? quantApi.fundBasicInfo(code) : http.get(`/funds/${code}`, { signal, suppressErrorMessage: true })
  },
  estimate(code: string, signal: AbortSignal): Promise<FundEstimate> {
    return USE_MOCK ? quantApi.fundEstimate(code) : http.get(`/funds/${code}/estimate`, { signal, suppressErrorMessage: true })
  },
  trades(signal: AbortSignal): Promise<TradeRecord[]> {
    return USE_MOCK ? quantApi.trades() : http.get('/trades', { signal, suppressErrorMessage: true })
  }
}
