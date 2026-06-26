export function money(value: number, digits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  }).format(value)
}

export function percent(value: number, digits = 2) {
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(digits)}%`
}

export function signed(value: number, digits = 2) {
  const sign = value > 0 ? '+' : ''
  return `${sign}${money(value, digits)}`
}

export function formatDateTime(value?: string | null) {
  if (!value) return '--'
  const trimmed = value.trim()
  const match = trimmed.match(/^(\d{4}-\d{2}-\d{2})[T\s](\d{2}:\d{2}:\d{2})(?:\.\d+)?/)
  if (!match) return trimmed
  return `${match[1]} ${match[2]}`
}

export function toneClass(value: number) {
  if (value > 0) return 'text-rise'
  if (value < 0) return 'text-fall'
  return 'text-muted'
}

export function metricTone(value: number) {
  if (value > 0) return 'rise'
  if (value < 0) return 'fall'
  return 'neutral'
}

export function actionTone(action: string) {
  return {
    BUY: 'danger',
    SELL: 'success',
    HOLD: 'neutral',
    WATCH: 'warning',
    CONVERT: 'info'
  }[action] || 'neutral'
}
