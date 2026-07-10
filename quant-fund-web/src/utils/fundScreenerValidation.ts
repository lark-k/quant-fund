import type { FundScreenerBacktestMetric } from '@/types/domain'

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

export function taskResultMessageLevel(status: string): 'success' | 'warning' | 'error' {
  if (status === 'FAILED') return 'error'
  if (status === 'PARTIAL_SUCCESS') return 'warning'
  return 'success'
}
