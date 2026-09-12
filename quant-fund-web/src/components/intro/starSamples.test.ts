import { describe, expect, it } from 'vitest'
import { selectStarSamples } from './starSamples'

describe('photographic star sampling', () => {
  it('never twinkles highlights belonging to petals or the stem', () => {
    const width = 100
    const height = 100
    const pixels = new Uint8ClampedArray(width * height * 4)
    for (const [x, y] of [[50, 40], [48, 85], [12, 30], [87, 65]]) {
      pixels.set([255, 255, 255, 255], (y * width + x) * 4)
    }
    expect(selectStarSamples(pixels, width, height).map(({ x, y }) => [x, y])).toEqual([[12, 30], [87, 65]])
  })

  it('deduplicates bright pixels in one star and bounds the total overlay count', () => {
    const width = 640
    const height = 360
    const pixels = new Uint8ClampedArray(width * height * 4)
    for (let y = 10; y < 340; y += 25) {
      for (const x of [50, 51, 52, 590]) pixels.set([240, 240, 240, 255], (y * width + x) * 4)
    }
    const stars = selectStarSamples(pixels, width, height, 10)
    expect(stars).toHaveLength(10)
    expect(stars.every((star, n) => stars.slice(n + 1).every(other => Math.hypot(star.x - other.x, star.y - other.y) > 18))).toBe(true)
    expect(selectStarSamples(new Uint8ClampedArray(width * height * 4), width, height)).toEqual([])
  })
})
