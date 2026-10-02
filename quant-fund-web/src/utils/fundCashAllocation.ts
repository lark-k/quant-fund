/** Transfers between a fund and the unallocated pool, in integer cents. */
export function reallocateCash(unallocated: number, previous: number, next: number) {
  if (![unallocated, previous, next].every(Number.isFinite) || unallocated < 0 || next < 0) {
    return { ok: false as const, reason: 'invalid' as const }
  }
  const remainder = Math.round(unallocated * 100) + Math.round(previous * 100) - Math.round(next * 100)
  if (remainder < 0) return { ok: false as const, reason: 'insufficient' as const }
  return { ok: true as const, unallocated: remainder / 100, balance: Math.round(next * 100) / 100 }
}
