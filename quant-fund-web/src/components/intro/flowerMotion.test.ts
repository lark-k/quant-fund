import { describe, expect, it } from 'vitest'
import { advanceFlower, INTRO_SESSION_KEY, rememberIntro, shouldShowIntro } from './flowerMotion'

describe('flower entrance interaction', () => {
  it('reverses from the current shape with continuous momentum and reaches the bud', () => {
    let motion = { progress: 0, velocity: 0 }
    for (let n = 0; n < 90; n++) motion = advanceFlower(motion, 1, 1 / 60)
    expect(motion.progress).toBeGreaterThan(0.25)
    expect(motion.progress).toBeLessThan(0.6)
    const before = motion
    motion = advanceFlower(motion, 0, 1 / 60)
    expect(Math.abs(motion.progress - before.progress)).toBeLessThan(0.006)
    expect(Math.abs(motion.velocity - before.velocity)).toBeLessThan(0.02)
    for (let n = 0; n < 300; n++) motion = advanceFlower(motion, 0, 1 / 60)
    expect(motion).toEqual({ progress: 0, velocity: 0 })
  })

  it('survives rapid direction changes without overshooting either endpoint', () => {
    let motion = { progress: 0, velocity: 0 }
    for (let n = 0; n < 1200; n++) {
      motion = advanceFlower(motion, n % 73 < 45 ? 1 : 0, 1 / 60)
      expect(motion.progress).toBeGreaterThanOrEqual(0)
      expect(motion.progress).toBeLessThanOrEqual(1)
    }
    for (let n = 0; n < 400; n++) motion = advanceFlower(motion, 1, 1 / 60)
    expect(motion).toEqual({ progress: 1, velocity: 0 })
  })

  it('does not jump across the animation after a suspended tab resumes', () => {
    const next = advanceFlower({ progress: 0.4, velocity: 0.28 }, 1, 60)
    expect(next.progress - 0.4).toBeLessThanOrEqual(0.0141)
  })

  it('arrives at both endpoints at rest instead of cutting off visible speed', () => {
    for (const fps of [30, 60, 120]) for (const target of [0, 1] as const) {
      let motion = { progress: 1 - target, velocity: 0 }
      let previous = motion
      let ticks = 0
      while (motion.progress !== target && ticks++ < fps * 6) {
        previous = motion
        motion = advanceFlower(motion, target, 1 / fps)
      }
      expect(motion).toEqual({ progress: target, velocity: 0 })
      expect(Math.abs(previous.velocity)).toBeLessThan(0.002)
      // The final correction is much less than one hundredth of a source frame.
      expect(Math.abs(motion.progress - previous.progress) * 168).toBeLessThan(0.025)
    }
  })

  it('shows once per session and allows an explicit review replay', () => {
    const values = new Map<string, string>()
    const storage = { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => { values.set(key, value) } }
    expect(shouldShowIntro(storage)).toBe(true)
    rememberIntro(storage)
    expect(values.get(INTRO_SESSION_KEY)).toBe('seen')
    expect(shouldShowIntro(storage)).toBe(false)
    expect(shouldShowIntro(storage, true)).toBe(true)
  })

  it('allows entry with blocked browser storage', () => {
    const storage = { getItem: () => { throw new Error('denied') }, setItem: () => { throw new Error('denied') } }
    expect(shouldShowIntro(storage)).toBe(true)
    expect(() => rememberIntro(storage)).not.toThrow()
    expect(() => rememberIntro(undefined)).not.toThrow()
  })
})
