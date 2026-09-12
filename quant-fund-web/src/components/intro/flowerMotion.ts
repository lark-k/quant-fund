export interface FlowerMotion { progress: number; velocity: number }

/** Acceleration-limited motion preserves position and momentum when hover reverses. */
export function advanceFlower(state: FlowerMotion, target: 0 | 1, elapsed: number): FlowerMotion {
  const dt = Math.max(0, Math.min(elapsed, 0.05))
  const distance = target - state.progress
  const acceleration = 1.15
  // Begin braking before the discrete integrator reaches the endpoint. A gentle
  // proportional tail avoids clamping nonzero speed straight into the idle loop.
  const desired = Math.sign(distance) * Math.min(target ? 0.28 : 0.32, Math.sqrt(1.3 * acceleration * Math.abs(distance)), Math.abs(distance) * 8)
  const velocity = state.velocity + Math.max(-acceleration * dt, Math.min(acceleration * dt, desired - state.velocity))
  const progress = Math.max(0, Math.min(1, state.progress + velocity * dt))
  if (Math.abs(target - progress) < 0.0001 && Math.abs(velocity) < 0.002) return { progress: target, velocity: 0 }
  if ((target === 1 && progress >= 1) || (target === 0 && progress <= 0)) {
    return { progress: target, velocity: 0 }
  }
  return { progress, velocity }
}

export const INTRO_SESSION_KEY = 'quantfund:intro:flower-v1'

export function shouldShowIntro(storage: Pick<Storage, 'getItem'> | undefined, replay = false): boolean {
  if (replay) return true
  try { return storage?.getItem(INTRO_SESSION_KEY) !== 'seen' } catch { return true }
}

export function rememberIntro(storage: Pick<Storage, 'setItem'> | undefined): void {
  try { storage?.setItem(INTRO_SESSION_KEY, 'seen') } catch { /* Private browsing must not trap the visitor. */ }
}
