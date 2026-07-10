import type { FundScreenerBacktestMetric, FundScreenerValidation } from '@/types/domain'

export type ValidationBucketRow = {
  bucketName: FundScreenerBacktestMetric['bucketName']
  label: string
  horizons: Partial<Record<FundScreenerBacktestMetric['horizonDays'], FundScreenerBacktestMetric>>
}

const buckets: Array<{ bucketName: ValidationBucketRow['bucketName']; label: string }> = [
  { bucketName: 'TOP_5', label: '前5%' },
  { bucketName: 'TOP_10', label: '前10%' },
  { bucketName: 'WATCH', label: '观察' },
  { bucketName: 'NEUTRAL', label: '中性' },
  { bucketName: 'AVOID', label: '回避' }
]

export function buildValidationRows(metrics: FundScreenerBacktestMetric[]): ValidationBucketRow[] {
  return buckets.map(({ bucketName, label }) => {
    const horizons: ValidationBucketRow['horizons'] = {}
    metrics.filter((metric) => metric.bucketName === bucketName).forEach((metric) => {
      horizons[metric.horizonDays] = metric
    })
    return { bucketName, label, horizons }
  })
}

export function significanceText(metric?: Pick<FundScreenerBacktestMetric, 'statisticallySignificant'>) {
  if (!metric) return '暂无样本'
  return metric.statisticallySignificant ? '样本有效' : '不具备统计意义'
}

export function hasValidationSamples(rows: ValidationBucketRow[]) {
  return rows.some((row) => Object.values(row.horizons).some((metric) => (metric?.sampleCount || 0) > 0))
}

export function validationStatusText(status?: FundScreenerValidation['status']) {
  const labels: Record<FundScreenerValidation['status'], string> = {
    EFFECTIVE: '策略有效',
    NEUTRAL: '策略中性',
    FAILED: '策略失效',
    INSUFFICIENT: '样本不足'
  }
  return status ? labels[status] : '尚未验证'
}

export function validationStatusType(status?: FundScreenerValidation['status']) {
  if (status === 'EFFECTIVE') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

export function validationStatusClass(status?: FundScreenerValidation['status']) {
  return `validation-status-${(status || 'UNKNOWN').toLowerCase()}`
}

export function taskResultMessageLevel(status: string): 'success' | 'warning' | 'error' {
  if (status === 'FAILED') return 'error'
  if (status === 'PARTIAL_SUCCESS') return 'warning'
  return 'success'
}
