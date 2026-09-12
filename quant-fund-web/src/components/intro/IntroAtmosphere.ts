import { gsap } from 'gsap'
import { selectStarSamples, type StarSample } from './starSamples'

interface AtmosphereElements {
  root: HTMLElement
  breath: HTMLElement
  parallax: HTMLElement
  stars: HTMLCanvasElement
  sky: HTMLCanvasElement
  onBudBreath: (progress: number) => void
  compact: boolean
}

/** Decorative motion owns separate transforms; the flower hit area never moves. */
export class IntroAtmosphere {
  private ctx: gsap.Context
  private breathTween!: gsap.core.Tween
  private settleTween!: gsap.core.Tween
  private budTween!: gsap.core.Tween
  private budState = { progress: 0 }
  private restingBud = false
  private xTo!: gsap.QuickToFunc
  private yTo!: gsap.QuickToFunc
  private starsTo!: gsap.QuickToFunc
  private skyTo!: gsap.QuickToFunc
  private twinkles: gsap.core.Tween[] = []
  private lights = Array.from({ length: 6 }, (_, n) => ({ dim: 0.12 + n * 0.045 }))
  private sample = document.createElement('canvas')
  private atlas = document.createElement('canvas')
  private flareAtlas = document.createElement('canvas')
  private points: StarSample[] = []
  private paintContext: CanvasRenderingContext2D | null
  private skyContext: CanvasRenderingContext2D | null
  private skyTime = 0
  private flowerBounds = { x: 0, y: 0, rx: 1, ry: 1 }
  private disposed = false
  private hidden = document.hidden
  private committed = false
  private moving = false
  private ticking = false
  private lastPaint = -1
  private pointerStarted = false
  private starsStarted = false

  constructor(private elements: AtmosphereElements) {
    elements.stars.width = this.sample.width = 640
    elements.stars.height = this.sample.height = 360
    this.paintContext = elements.stars.getContext('2d')
    this.skyContext = elements.sky.getContext('2d')
    this.resizeSky()
    window.addEventListener('resize', this.resizeSky)
    this.ctx = gsap.context(() => {
      const amplitude = elements.compact ? 0.65 : 1
      this.breathTween = gsap.to(elements.breath, {
        y: -18 * amplitude, x: 5 * amplitude, rotation: 0.65 * amplitude,
        scale: 1 + 0.048 * amplitude, transformOrigin: '50% 60%',
        duration: 3.8, repeat: -1, yoyo: true, ease: 'sine.inOut', paused: true,
      })
      this.budTween = gsap.to(this.budState, {
        progress: 0.105, duration: 3.3, repeat: -1, yoyo: true, ease: 'sine.inOut', paused: true,
        onUpdate: () => {
          if (!this.hidden && !this.committed && this.restingBud) elements.onBudBreath(this.budState.progress)
        },
      })
      this.settleTween = gsap.to(elements.breath, {
        x: 0, y: 0, scale: 1, rotation: 0, duration: 0.85, ease: 'sine.inOut', paused: true,
      })
      this.xTo = gsap.quickTo(elements.parallax, 'x', { duration: 1.4, ease: 'power2.out' })
      this.yTo = gsap.quickTo(elements.parallax, 'y', { duration: 1.4, ease: 'power2.out' })
      this.starsTo = gsap.quickTo(elements.stars, 'opacity', { duration: 0.7, ease: 'sine.inOut' })
      this.skyTo = gsap.quickTo(elements.sky, 'opacity', { duration: 1, ease: 'sine.inOut' })
      this.twinkles = this.lights.map((light, n) => gsap.to(light, {
        dim: 0.82 + (n % 3) * 0.05, duration: 1.5 + n * 0.37,
        repeat: -1, repeatDelay: n * 0.17, yoyo: true, ease: 'sine.inOut', paused: true,
      }))
    }, elements.root)
    this.setHidden(this.hidden)
    this.skyTo(1)
    if (this.hidden) this.skyTo.tween.pause()
  }

  movePointer(x: number, y: number): void {
    if (this.disposed || this.hidden || this.committed) return
    this.pointerStarted = true
    this.xTo(Math.max(-1, Math.min(1, x)) * 6)
    this.yTo(Math.max(-1, Math.min(1, y)) * 3.5)
  }

  centerPointer(): void {
    if (this.disposed || this.hidden) return
    this.pointerStarted = true
    this.xTo(0)
    this.yTo(0)
  }

  setMoving(moving: boolean): void {
    if (this.disposed || this.committed) return
    this.moving = moving
    this.starsStarted = true
    this.starsTo(moving ? 0 : 1)
    if (moving) { this.restingBud = false; this.budTween.pause() }
    this.addTicker()
  }

  restBud(resting: boolean): void {
    if (this.disposed || this.committed) return
    this.restingBud = resting
    this.budTween.pause()
    if (resting) {
      this.budState.progress = 0
      this.budTween.invalidate().restart()
      if (this.hidden) this.budTween.pause()
    }
  }

  refreshStars(source: CanvasImageSource): void {
    if (this.disposed || this.committed) return
    // Read once at an endpoint, never during frame-by-frame petal motion.
    const sampleContext = this.sample.getContext('2d', { willReadFrequently: true })
    if (!sampleContext || !this.paintContext) return
    try {
      sampleContext.clearRect(0, 0, 640, 360)
      sampleContext.drawImage(source, 0, 0, 640, 360)
      const pixels = sampleContext.getImageData(0, 0, 640, 360)
      this.points = selectStarSamples(pixels.data, 640, 360, this.elements.compact ? 28 : 44)
      this.atlas.width = Math.max(1, this.points.length * 16)
      this.atlas.height = 16
      this.flareAtlas.width = this.atlas.width
      this.flareAtlas.height = 16
      const atlasContext = this.atlas.getContext('2d')
      const flareContext = this.flareAtlas.getContext('2d')
      if (!atlasContext || !flareContext) return
      this.points.forEach((star, n) => {
        const patch = sampleContext.getImageData(star.x - 8, star.y - 8, 16, 16)
        const flare = new ImageData(new Uint8ClampedArray(patch.data), 16, 16)
        for (let i = 0; i < patch.data.length; i += 4) {
          const brightness = Math.max(patch.data[i], patch.data[i + 1], patch.data[i + 2])
          const dx = (i / 4) % 16 - 7.5
          const dy = Math.floor(i / 4 / 16) - 7.5
          const feather = Math.max(0, 1 - Math.hypot(dx, dy) / 8)
          patch.data[i] = patch.data[i + 1] = patch.data[i + 2] = 0
          patch.data[i + 3] = Math.round(brightness * feather)
          flare.data[i + 3] = Math.round(brightness * feather)
        }
        atlasContext.putImageData(patch, n * 16, 0)
        flareContext.putImageData(flare, n * 16, 0)
      })
      this.elements.stars.dataset.stars = String(this.points.length)
      this.paint(0)
      this.setMoving(false)
    } catch {
      // Atmosphere is optional: an unreadable canvas must not break navigation.
      this.points = []
      this.paintContext.clearRect(0, 0, 640, 360)
    }
  }

  settle(): void {
    if (this.disposed || this.committed) return
    this.committed = true
    this.breathTween.pause()
    this.budTween.pause()
    this.restingBud = false
    this.twinkles.forEach(tween => tween.pause())
    this.removeTicker()
    this.starsStarted = true
    this.starsTo(0)
    this.skyTo(0)
    this.centerPointer()
    this.settleTween.invalidate().restart()
    if (this.hidden) this.pauseTweens()
  }

  setHidden(hidden: boolean): void {
    if (this.disposed) return
    this.hidden = hidden
    if (hidden) {
      this.pauseTweens()
      this.removeTicker()
    } else {
      if (this.committed) this.settleTween.resume()
      else {
        this.breathTween.resume()
        if (this.restingBud) this.budTween.resume()
        this.twinkles.forEach(tween => tween.resume())
        this.addTicker()
      }
      if (this.pointerStarted) {
        this.xTo.tween.resume()
        this.yTo.tween.resume()
      }
      if (this.starsStarted) this.starsTo.tween.resume()
      if (this.skyTo.tween.progress() > 0) this.skyTo.tween.resume()
    }
  }

  private pauseTweens(): void {
    this.breathTween.pause()
    this.budTween.pause()
    this.settleTween.pause()
    this.twinkles.forEach(tween => tween.pause())
    this.xTo.tween.pause()
    this.yTo.tween.pause()
    this.starsTo.tween.pause()
    this.skyTo.tween.pause()
  }

  private paint = (time: number): void => {
    if (!this.paintContext || (time && time - this.lastPaint < 1 / 24)) return
    if (time && this.lastPaint >= 0) this.skyTime += Math.min(time - this.lastPaint, 0.1)
    this.lastPaint = time
    this.paintContext.clearRect(0, 0, 640, 360)
    this.paintContext.globalCompositeOperation = 'source-over'
    this.points.forEach((star, n) => {
      this.paintContext!.globalAlpha = this.lights[n % this.lights.length].dim
      this.paintContext!.drawImage(this.atlas, n * 16, 0, 16, 16, star.x - 8, star.y - 8, 16, 16)
    })
    this.paintContext.globalCompositeOperation = 'lighter'
    this.points.forEach((star, n) => {
      this.paintContext!.globalAlpha = (1 - this.lights[n % this.lights.length].dim) ** 2 * 1.25
      this.paintContext!.drawImage(this.flareAtlas, n * 16, 0, 16, 16, star.x - 8, star.y - 8, 16, 16)
    })
    this.paintContext.globalAlpha = 1
    this.paintSky()
  }

  private resizeSky = (): void => {
    const ratio = Math.min(1, 1280 / window.innerWidth)
    this.elements.sky.width = Math.round(window.innerWidth * ratio)
    this.elements.sky.height = Math.round(window.innerHeight * ratio)
    const bounds = this.elements.parallax.parentElement?.getBoundingClientRect()
    if (bounds) this.flowerBounds = {
      x: (bounds.left + bounds.width * 0.5) * ratio,
      y: (bounds.top + bounds.height * 0.4) * ratio,
      rx: bounds.width * 0.35 * ratio,
      ry: bounds.height * 0.48 * ratio,
    }
  }

  private paintSky(): void {
    if (!this.skyContext || !this.points.length) return
    const ctx = this.skyContext
    const { width, height } = this.elements.sky
    const count = this.elements.compact ? 42 : 84
    ctx.clearRect(0, 0, width, height)
    ctx.globalCompositeOperation = 'lighter'
    for (let n = 0; n < count; n++) {
      const depth = 0.35 + (n % 5) * 0.16
      const phase = (n * 0.61803398875 + this.skyTime * (0.002 + depth * 0.005)) % 1
      const x = ((n * 0.754877666 + Math.sin(this.skyTime * 0.09 + n) * 0.018) % 1 + 1) % 1
      const fade = Math.min(1, phase * 12, (1 - phase) * 12)
      const pulse = 0.58 + 0.42 * Math.sin(this.skyTime * (0.55 + depth * 0.2) + n * 2.4) ** 2
      const size = (13 + depth * 17) * Math.max(0.65, Math.min(1, width / 900))
      const px = x * width
      const py = (0.12 + phase * 0.7) * height
      const region = this.flowerBounds
      const distance = ((px - region.x) / region.rx) ** 2 + ((py - region.y) / region.ry) ** 2
      const flowerExclusion = Math.max(0, Math.min(1, (distance - 1) * 2.5))
      ctx.globalAlpha = fade * pulse * (0.55 + depth * 0.45) * flowerExclusion
      ctx.drawImage(this.flareAtlas, (n % this.points.length) * 16, 0, 16, 16, px - size / 2, py - size / 2, size, size)
    }
    ctx.globalAlpha = 1
  }

  private addTicker(): void {
    if (this.hidden || this.committed || this.ticking || !this.points.length) return
    this.lastPaint = -1
    gsap.ticker.add(this.paint)
    this.ticking = true
  }

  private removeTicker(): void {
    gsap.ticker.remove(this.paint)
    this.ticking = false
  }

  dispose(): void {
    if (this.disposed) return
    this.disposed = true
    this.removeTicker()
    this.ctx.revert()
    window.removeEventListener('resize', this.resizeSky)
    this.paintContext?.clearRect(0, 0, 640, 360)
    this.skyContext?.clearRect(0, 0, this.elements.sky.width, this.elements.sky.height)
    this.sample.width = this.atlas.width = this.flareAtlas.width = 0
    this.points = []
  }
}
