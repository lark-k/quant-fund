import { http, USE_MOCK } from './http'

export interface FundCashRow {
  fundCode: string
  fundName: string
  balance: number
  archived: boolean
  pendingBuy: number
  pendingSell: number
}
export interface FundCashSnapshot {
  version: number
  unallocated: number
  total: number
  funds: FundCashRow[]
}
export interface FundCashUpdate {
  version: number
  unallocated: number
  funds: Array<{ fundCode: string; balance: number }>
}
export const fundCashApi = {
  async get(id: number): Promise<FundCashSnapshot> {
    if (USE_MOCK) throw new Error('基金现金分配需要连接实际服务')
    return http.get(`/portfolios/${id}/fund-cash`)
  },
  async save(id: number, value: FundCashUpdate): Promise<FundCashSnapshot> {
    if (USE_MOCK) throw new Error('基金现金分配需要连接实际服务')
    return http.put(`/portfolios/${id}/fund-cash`, value)
  }
}
