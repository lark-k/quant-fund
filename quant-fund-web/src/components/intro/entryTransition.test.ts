import { describe, expect, it } from 'vitest'
import { advanceEntryBlend } from './entryTransition'

describe('entry pose handoff', () => {
  it('reaches an exact completed state so playback cannot stall on floating point residue', () => {
    let blend = 0
    for (let n = 0; n < 60; n++) blend = advanceEntryBlend(blend, 1, 1 / 60, true)
    expect(blend).toBe(1)
  })

  it('does not swap immediately when entering from an already hover-open flower', () => {
    expect(advanceEntryBlend(0, 1, 1 / 60, true)).toBeLessThan(0.03)
    expect(advanceEntryBlend(0, 1, 60, true)).toBeLessThan(0.08)
  })

  it('waits for decoded video and then starts a gradual handoff even after a long wait', () => {
    let blend = 0
    for (let n = 0; n < 120; n++) blend = advanceEntryBlend(blend, 1, 1 / 60, false)
    expect(blend).toBe(0)
    blend = advanceEntryBlend(blend, 1, 1 / 60, true)
    expect(blend).toBeGreaterThan(0)
    expect(blend).toBeLessThan(0.03)
  })
})
