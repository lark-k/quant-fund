import { describe, expect, it } from 'vitest'
import {
  buildValidationRows,
  hasValidationSamples,
  significanceText,
  taskResultMessageLevel,
  validationStatusClass,
  validationStatusText,
  validationStatusType
} from './fundScreenerValidation'

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

  it('can build independent rows for current lookback and forward validation metrics', () => {
    const lookbackRows = buildValidationRows([
      {
        bucketName: 'TOP_5',
        horizonDays: 20,
        sampleCount: 80,
        scoreDateCount: 1,
        avgForwardReturn: 3.4,
        winRate: 62,
        avgExcessReturn: 1.1,
        maxDrawdown: -4.2,
        statisticallySignificant: true
      }
    ])
    const forwardRows = buildValidationRows([])

    expect(lookbackRows[0].horizons[20]?.sampleCount).toBe(80)
    expect(forwardRows[0].horizons[20]).toBeUndefined()
  })

  it('detects forward validation samples independently from current lookback rows', () => {
    expect(hasValidationSamples(buildValidationRows([]))).toBe(false)
    expect(hasValidationSamples(buildValidationRows([
      {
        bucketName: 'TOP_5',
        horizonDays: 20,
        sampleCount: 12,
        scoreDateCount: 1,
        avgForwardReturn: 2.1,
        winRate: 58,
        avgExcessReturn: 0.9,
        maxDrawdown: -3.4,
        statisticallySignificant: false
      }
    ]))).toBe(true)
  })

  it('labels missing or small samples as statistically insufficient', () => {
    expect(significanceText(undefined)).toBe('暂无样本')
    expect(significanceText({ statisticallySignificant: false })).toBe('不具备统计意义')
    expect(significanceText({ statisticallySignificant: true })).toBe('样本有效')
  })

  it('uses a readable warning tag for insufficient validation status', () => {
    expect(validationStatusText('INSUFFICIENT')).toBe('样本不足')
    expect(validationStatusClass('INSUFFICIENT')).toBe('validation-status-insufficient')
    expect(validationStatusType('INSUFFICIENT')).toBe('warning')
    expect(validationStatusType('EFFECTIVE')).toBe('success')
    expect(validationStatusType('FAILED')).toBe('danger')
  })

  it('does not report partial or failed backtests as successful', () => {
    expect(taskResultMessageLevel('SUCCESS')).toBe('success')
    expect(taskResultMessageLevel('PARTIAL_SUCCESS')).toBe('warning')
    expect(taskResultMessageLevel('FAILED')).toBe('error')
  })
})
