export interface StarSample { x: number; y: number; brightness: number }

/** Pick highlights from the supplied photograph, excluding every flower/stem position. */
export function selectStarSamples(pixels: Uint8ClampedArray, width: number, height: number, limit = 32): StarSample[] {
  const candidates: StarSample[] = []
  for (let y = 8; y < height - 8; y++) {
    for (let x = 8; x < width - 8; x++) {
      const nx = x / width
      const ny = y / height
      const flower = ((nx - 0.5) / 0.34) ** 2 + ((ny - 0.4) / 0.46) ** 2 < 1
      const stem = nx > 0.41 && nx < 0.6 && ny > 0.58
      if (flower || stem) continue
      const at = (y * width + x) * 4
      const brightness = pixels[at] * 0.2126 + pixels[at + 1] * 0.7152 + pixels[at + 2] * 0.0722
      if (brightness >= 120) candidates.push({ x, y, brightness })
    }
  }
  const selected: StarSample[] = []
  for (const candidate of candidates.sort((a, b) => b.brightness - a.brightness)) {
    if (selected.every(star => Math.hypot(star.x - candidate.x, star.y - candidate.y) > 18)) selected.push(candidate)
    if (selected.length >= limit) break
  }
  return selected
}
