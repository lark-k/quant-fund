/** Blend the two source poses before starting the camera clip, including hover-open entry. */
export function advanceEntryBlend(current: number, progress: number, elapsed: number, videoReady: boolean): number {
  if (!videoReady) return current
  const target = progress >= 1 ? 1 : Math.max(0, (progress - 0.78) / 0.22)
  return Math.min(target, current + Math.max(0, Math.min(elapsed, 0.05)) / 0.65)
}
