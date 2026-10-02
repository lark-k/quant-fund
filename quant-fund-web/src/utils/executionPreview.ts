import type { TechnicalExecution } from './fundTechnicalAdvice'

export function executionPreview(plan: TechnicalExecution, value: number, clear = false) {
  if (!Number.isFinite(value) || value < 0 || plan.direction === 'NONE') return null
  const buy = plan.direction === 'BUY'
  if (!buy && !clear && value > plan.holdingAmount + 0.005) return null
  // The displayed amount is rounded; preserve the engine's precise suggested shares on restore.
  const unchanged = Math.abs(value - plan.suggestedAmount) < 0.005
  const shares = buy ? 0 : clear ? plan.holdingShares : unchanged ? plan.suggestedShares : Math.min(plan.holdingShares, Math.floor((value / plan.referenceNav + 1e-10) * 10000) / 10000)
  const transfer = buy ? value : shares * plan.referenceNav
  return {
    shares,
    holdingAfter: Math.max(0, buy ? plan.holdingAmount + transfer : clear ? 0 : plan.holdingShares * plan.referenceNav - transfer),
    cashAfter: buy ? plan.cashBalance - transfer : plan.cashBalance + transfer,
    extraCash: buy ? Math.max(0, transfer - plan.cashBalance) : 0,
    changed: clear || !unchanged
  }
}
