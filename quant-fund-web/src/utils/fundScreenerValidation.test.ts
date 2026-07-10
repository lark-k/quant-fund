import { describe, expect, it } from 'vitest'
import { buildValidationRows, significanceText, taskResultMessageLevel } from './fundScreenerValidation'

describe('fund screener validation table', () => {
  it('orders all buckets and indexes flat metrics by horizon', () => {
    const rows = buildValidationRows([
      {
        bucketName: 'TOP_10',
        horizonDays: 60,
        sampleCount: 42,
        scoreDateCount: 4,
        avgForwardReturn: 6.2,
        winRate: 61,
        avgExcessReturn: 2.3,
        maxDrawdown: -8.4,
        statisticallySignificant: true
      }
    ])

    expect(rows.map((row) => row.bucketName)).toEqual(['TOP_5', 'TOP_10', 'WATCH', 'NEUTRAL', 'AVOID'])
    expect(rows[1].horizons[60]?.avgExcessReturn).toBe(2.3)
    expect(rows[1].label).toBe('前10%')
  })

  it('labels missing or small samples as statistically insufficient', () => {
    expect(significanceText(undefined)).toBe('暂无样本')
    expect(significanceText({ statisticallySignificant: false })).toBe('不具备统计意义')
    expect(significanceText({ statisticallySignificant: true })).toBe('样本有效')
  })

  it('does not report partial or failed backtests as successful', () => {
    expect(taskResultMessageLevel('SUCCESS')).toBe('success')
    expect(taskResultMessageLevel('PARTIAL_SUCCESS')).toBe('warning')
    expect(taskResultMessageLevel('FAILED')).toBe('error')
  })
})
