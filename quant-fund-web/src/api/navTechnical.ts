import { http, USE_MOCK } from './http'
import type { TechnicalAdvice } from '@/utils/fundTechnicalAdvice'

export interface NavBacktestParams {
  startDate: string; endDate: string; initialCash: number; buyPercent: number; sellPercent: number
  buyFee: number; shortSellFee: number; mediumSellFee: number; sellFee: number
  disclosureDelay: number; confirmDelay: number; settlementDelay: number
  initialPositionPercent?: number; ruleVersion?: 'NAV-TA v1' | 'NAV-TA v2-balanced' | 'NAV-TA v3.1-trend'
}
export interface NavBacktestResult {
  ruleId?: string
  validationStatus: 'SIMULATED' | 'NO_ELIGIBLE_DAYS'
  id: string; fundCode: string; fundName: string; ruleVersion: string; engineVersion: string; createdAt: string
  startDate: string; endDate: string; source: string; dataHash: string; parameters: NavBacktestParams
  metrics: { totalReturn: number; benchmarkReturn: number; excessReturn: number; annualReturn: number | null; maxDrawdown: number; benchmarkMaxDrawdown: number; tradeCount: number; initialTradeCount?: number; signalTradeCount?: number; totalFees: number; benchmarkFees: number; exposure: number; analyzedDays: number; totalDays: number }
  comparison?: { status: 'AVAILABLE' | 'INSUFFICIENT'; method: string; candidate: string; adoption: string; results: Array<{ segment: string; ruleVersion: string; startDate: string; endDate: string; validationStatus: string; metrics: NavBacktestResult['metrics'] }> }
  curve: Array<{ date: string; returnRate: number; benchmarkReturnRate: number; drawdown: number; benchmarkDrawdown: number; equity: number; cash: number; unsettled: number; holding: number }>
  trades: Array<{ signalDate: string; navAsOf: string; executionDate: string; action: string; amount: number; fee: number; unitNav: number; settlementDate: string | null; reason: string; evidence: Array<{ label: string; value: string; explanation: string }> }>
  signalCounts: Record<string, number>; unmetConditions: Record<string, number>; blockedReasons: Record<string, number>; skippedTrades: Record<string, number>
  assumptions: string[]
}
export interface NavBacktestSummary { id: string; ruleVersion: string; createdAt: string }
const base = (id: number) => `/holdings/${id}/nav-technical`
// Real recommendations and replay both use the same Python implementation.
// Browser QA replaces this API explicitly; demo NAVs never become real recommendations.
function liveOnly() { if (USE_MOCK) throw new Error('演示模式不运行真实技术分析或回测，请连接后端服务。') }
export const navTechnicalApi = {
  async analyze(id: number, signal: AbortSignal): Promise<TechnicalAdvice> {
    liveOnly(); return http.post(`${base(id)}/analyze`, {}, { signal, timeout: 180000, suppressErrorMessage: true })
  },
  async run(id: number, params: NavBacktestParams, signal: AbortSignal): Promise<NavBacktestResult> {
    liveOnly(); return http.post(`${base(id)}/backtests`, params, { signal, timeout: 240000, suppressErrorMessage: true })
  },
  async history(id: number, signal: AbortSignal): Promise<NavBacktestSummary[]> {
    liveOnly(); return http.get(`${base(id)}/backtests`, { signal, suppressErrorMessage: true })
  },
  async result(id: number, resultId: string, signal: AbortSignal): Promise<NavBacktestResult> {
    liveOnly(); return http.get(`${base(id)}/backtests/${encodeURIComponent(resultId)}`, { signal, timeout: 30000, suppressErrorMessage: true })
  }
}
