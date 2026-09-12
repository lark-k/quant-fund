/** Compressed frames stay in memory; only a small window is decoded into bitmaps. */
export class FlowerFrames {
  readonly count = 169
  failed = false
  private blobs = new Map<number, Blob>()
  private decoded = new Map<number, ImageBitmap>()
  private pending = new Map<number, Promise<ImageBitmap | undefined>>()
  private abort = new AbortController()
  private disposed = false
  private center = this.count - 1

  constructor(private base: string) {}

  async prepare(): Promise<void> {
    let index = this.count - 1
    const timeout = window.setTimeout(() => this.abort.abort(), 18000)
    try {
      await Promise.all(Array.from({ length: 6 }, async () => {
        while (index >= 0 && !this.disposed) {
          const frame = index--
          const response = await fetch(`${this.base}/${String(frame).padStart(3, '0')}.webp`, { signal: this.abort.signal })
          if (!response.ok) throw new Error('Flower frame unavailable')
          this.blobs.set(frame, await response.blob())
        }
      }))
      if (!this.disposed) await Promise.all(Array.from({ length: 12 }, (_, n) => this.decode(this.count - 1 - n)))
    } catch (error) {
      this.abort.abort()
      throw error
    } finally {
      window.clearTimeout(timeout)
    }
  }

  get(index: number): ImageBitmap | undefined { return this.decoded.get(index) }

  warm(index: number, direction: number): void {
    this.center = index
    void this.decode(index)
    // Ahead of motion plus a few frames behind for an immediate reversal.
    for (let step = 1; step <= 10; step++) void this.decode(index + step * direction)
    for (let step = 1; step <= 4; step++) void this.decode(index - step * direction)
    this.evict()
  }

  private decode(index: number): Promise<ImageBitmap | undefined> {
    if (this.disposed || index < 0 || index >= this.count) return Promise.resolve(undefined)
    const existing = this.decoded.get(index)
    if (existing) return Promise.resolve(existing)
    const pending = this.pending.get(index)
    if (pending) return pending
    const blob = this.blobs.get(index)
    if (!blob) return Promise.resolve(undefined)
    const task = createImageBitmap(blob).then(bitmap => {
      if (this.disposed || Math.abs(index - this.center) > 20) {
        bitmap.close()
        return undefined
      }
      this.decoded.set(index, bitmap)
      this.evict()
      return this.decoded.get(index)
    }).catch(() => { this.failed = true; return undefined }).finally(() => this.pending.delete(index))
    this.pending.set(index, task)
    return task
  }

  private evict(): void {
    if (this.decoded.size <= 22) return
    const farthest = [...this.decoded.keys()].sort((a, b) => Math.abs(b - this.center) - Math.abs(a - this.center))
    for (const index of farthest.slice(0, this.decoded.size - 22)) {
      this.decoded.get(index)?.close()
      this.decoded.delete(index)
    }
  }

  dispose(): void {
    this.disposed = true
    this.abort.abort()
    for (const bitmap of this.decoded.values()) bitmap.close()
    this.decoded.clear()
    this.blobs.clear()
  }
}
