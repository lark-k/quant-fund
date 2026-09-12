<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { FlowerFrames } from './FlowerFrames'
import { advanceEntryBlend } from './entryTransition'
import { advanceFlower, rememberIntro, shouldShowIntro, type FlowerMotion } from './flowerMotion'
import type { IntroAtmosphere } from './IntroAtmosphere'

const assetRoot = `${import.meta.env.BASE_URL}intro/flower-v1`
const replay = new URLSearchParams(window.location.search).get('intro') === 'replay'
let storage: Storage | undefined
try { storage = window.sessionStorage } catch { /* Storage can be unavailable. */ }
const visible = ref(shouldShowIntro(storage, replay))
const phase = ref<'idle' | 'opening' | 'entering' | 'leaving'>('idle')
const ready = ref(false)
const failed = ref(false)
const reduced = ref(window.matchMedia('(prefers-reduced-motion: reduce)').matches)
const compact = window.matchMedia('(max-width: 700px)').matches
const coarse = ref(window.matchMedia('(hover: none)').matches)
const variant = compact ? 'mobile' : 'desktop'
const poster = `${assetRoot}/${variant}/168.webp`
const dialog = ref<HTMLElement>()
const art = ref<HTMLElement>()
const canvas = ref<HTMLCanvasElement>()
const film = ref<HTMLVideoElement>()
const flowerButton = ref<HTMLButtonElement>()
const breathLayer = ref<HTMLElement>()
const parallaxLayer = ref<HTMLElement>()
const starCanvas = ref<HTMLCanvasElement>()
const skyCanvas = ref<HTMLCanvasElement>()
const posterImage = ref<HTMLImageElement>()
const frameVisible = ref(false)
const filmVisible = ref(false)
const cameraStarted = ref(false)
const expanded = ref(false)
const busy = computed(() => phase.value !== 'idle')
const hint = computed(() => reduced.value ? '一瞬盛放，静候启程' : failed.value ? '一瞬盛放，静候启程' : !ready.value ? '静候花开' : coarse.value ? '轻触花朵，令它盛放' : '靠近，听见花开的静谧')
let frames: FlowerFrames | undefined
let context: CanvasRenderingContext2D | null = null
let motion: FlowerMotion = { progress: 0, velocity: 0 }
let target: 0 | 1 = 0
let raf = 0
let lastTime = 0
let lastFrame = -1
let leaveTimer = 0
let exitTimer = 0
let entryWatchdog = 0
let openingWatchdog = 0
let videoFrameCallback = 0
let videoRaf = 0
let videoPrepared = false
let entryBlend = 0
let oldOverflow = ''
let oldFocus: HTMLElement | null = null
let mounted = false
let locked = false
let hover = false
let keyboardFocus = false
let tapOpen = false
let atmosphere: IntroAtmosphere | undefined
let atmosphereLoading = false
const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')

function requestTarget(next: 0 | 1) {
  target = next
  expanded.value = next === 1
  atmosphere?.setMoving(motion.progress !== next || motion.velocity !== 0)
  if (!ready.value || reduced.value || !visible.value || phase.value === 'entering' || phase.value === 'leaving') return
  if (!raf) {
    lastTime = 0
    raf = requestAnimationFrame(tick)
  }
}

function tick(now: number) {
  raf = 0
  if (!visible.value || document.hidden || phase.value === 'entering' || phase.value === 'leaving') return
  const elapsed = lastTime ? (now - lastTime) / 1000 : 1 / 60
  lastTime = now
  let next = advanceFlower(motion, target, elapsed)
  const videoReady = (film.value?.readyState ?? 0) >= 2
  // Never jump straight into a fully weighted video frame if decoding was late.
  if (phase.value === 'opening' && next.progress > 0.78 && !videoReady) {
    next = { progress: Math.max(motion.progress, Math.min(next.progress, 0.78)), velocity: 0 }
  }
  // A flower already opened by hover still needs a gradual pose handoff.
  const nextBlend = phase.value === 'opening' ? advanceEntryBlend(entryBlend, next.progress, elapsed, videoReady) : 0
  const drawn = renderFrame(next.progress, next.velocity > 0 ? -1 : 1, nextBlend)
  if (frames?.failed) {
    failed.value = true
    frames.dispose()
    if (phase.value === 'opening') reveal()
    return
  }
  if (drawn) { motion = next; entryBlend = nextBlend }
  if (phase.value === 'opening' && motion.progress >= 0.86) startCamera()
  if (phase.value === 'opening' && motion.progress === 1 && entryBlend === 1) {
    void playEntry()
    return
  }
  if (phase.value === 'opening' || motion.progress !== target || motion.velocity !== 0) raf = requestAnimationFrame(tick)
  else if (drawn) {
    // The bud's star atlas is prepared once, not read back on this critical frame.
    // While fully open, the independent sky remains active without a bud-aligned overlay.
    atmosphere?.setMoving(target === 1)
    atmosphere?.restBud(target === 0)
  }
}

function renderFrame(progress: number, direction: number, bridge = 0): boolean {
  if (!frames || !context || !canvas.value) return false
  const position = (1 - progress) * 168
  const lower = Math.floor(position)
  const upper = Math.min(168, lower + 1)
  frames.warm(lower, direction)
  const a = frames.get(lower)
  const b = frames.get(upper)
  if (!a || !b) return false
  if (position === lastFrame && phase.value !== 'opening') return true
  context.globalAlpha = 1
  context.drawImage(a, 0, 0)
  context.globalAlpha = position - lower
  context.drawImage(b, 0, 0)
  context.globalAlpha = 1
  // The two approved clips have different petal poses. Resolve that difference
  // before playback, using the decoded first video frame on this same canvas.
  const blend = bridge * bridge * (3 - 2 * bridge)
  if (blend > 0 && film.value && film.value.readyState >= 2) {
    context.globalAlpha = blend
    context.drawImage(film.value, 0, 0, canvas.value.width, canvas.value.height)
    context.globalAlpha = 1
  }
  lastFrame = position
  frameVisible.value = true
  canvas.value.dataset.frame = position.toFixed(2)
  canvas.value.dataset.source = blend > 0 ? 'bridge' : 'frames'
  canvas.value.dataset.bridge = blend.toFixed(4)
  return true
}

function renderBudBreath(progress: number) {
  if (!ready.value || target !== 0 || phase.value !== 'idle' || raf || reduced.value) return
  if (renderFrame(progress, progress > motion.progress ? -1 : 1)) motion = { progress, velocity: 0 }
}

async function startAtmosphere() {
  if (atmosphere || atmosphereLoading || !visible.value || reduced.value || phase.value !== 'idle') return
  atmosphereLoading = true
  try {
    const { IntroAtmosphere } = await import('./IntroAtmosphere')
    if (!mounted || !visible.value || reduced.value || phase.value !== 'idle' || !dialog.value || !breathLayer.value || !parallaxLayer.value || !starCanvas.value || !skyCanvas.value) return
    atmosphere = new IntroAtmosphere({ root: dialog.value, breath: breathLayer.value, parallax: parallaxLayer.value, stars: starCanvas.value, sky: skyCanvas.value, onBudBreath: renderBudBreath, compact })
    if (posterImage.value?.complete && posterImage.value.naturalWidth) atmosphere.refreshStars(posterImage.value)
    atmosphere.setMoving(motion.progress !== target || motion.velocity !== 0)
    if (ready.value && !raf && target === 0) {
      if (motion.progress === 0) atmosphere.restBud(true)
      else requestTarget(0)
    }
  } catch { /* Optional atmosphere must never prevent entering the website. */ }
  finally { atmosphereLoading = false }
}

function posterLoaded() {
  if (!frameVisible.value && posterImage.value) atmosphere?.refreshStars(posterImage.value)
}

function moveAtmosphere(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || coarse.value || busy.value || reduced.value) return
  atmosphere?.movePointer(event.clientX / window.innerWidth * 2 - 1, event.clientY / window.innerHeight * 2 - 1)
}

function enterFlower(event: PointerEvent) {
  if (event.pointerType === 'touch' || busy.value) return
  hover = true
  clearTimeout(leaveTimer)
  requestTarget(1)
}

function leaveFlower() {
  hover = false
  clearTimeout(leaveTimer)
  leaveTimer = window.setTimeout(() => {
    if (!busy.value && !keyboardFocus && !tapOpen) requestTarget(0)
  }, 150)
}

function focusFlower() {
  keyboardFocus = !!flowerButton.value?.matches(':focus-visible')
  if (keyboardFocus && !busy.value) requestTarget(1)
}

function blurFlower() {
  keyboardFocus = false
  if (!busy.value && !hover && !tapOpen) requestTarget(0)
}

function toggleFlower(event: MouseEvent) {
  if (busy.value) return
  if (event.detail > 0 && !coarse.value) return
  if (coarse.value) {
    tapOpen = !tapOpen
    requestTarget(tapOpen ? 1 : 0)
  } else requestTarget(target ? 0 : 1)
}

async function enter() {
  if (busy.value) return
  clearTimeout(leaveTimer)
  if (reduced.value || failed.value || !ready.value) { reveal(); return }
  phase.value = 'opening'
  atmosphere?.settle()
  prepareEntry()
  if (motion.progress >= 0.86) startCamera()
  requestTarget(1)
  // A decoder failure must never strand someone in the entrance.
  openingWatchdog = window.setTimeout(reveal, 6500)
}

function prepareEntry() {
  if (videoPrepared || !film.value || reduced.value) return
  videoPrepared = true
  film.value.preload = 'auto'
  film.value.load()
}

function startCamera() {
  if (cameraStarted.value || !art.value) return
  const bounds = art.value.getBoundingClientRect()
  art.value.style.setProperty('--intro-camera-scale', String(Math.max(window.innerWidth, window.innerHeight * 16 / 9) / bounds.width))
  art.value.style.setProperty('--intro-camera-y', `${window.innerHeight / 2 - (bounds.top + bounds.height / 2)}px`)
  cameraStarted.value = true
}

async function playEntry() {
  if (!visible.value || phase.value !== 'opening') return
  clearTimeout(openingWatchdog)
  phase.value = 'entering'
  const video = film.value
  if (!video) { reveal(); return }
  entryWatchdog = window.setTimeout(reveal, 9000)
  try {
    // Keep both the decoder and the visible canvas alive across the handoff.
    // No video-element fade, poster swap, or bitmap disposal at the seam.
    drawVideoFrame()
    scheduleVideoFrame()
    await video.play()
    if (phase.value !== 'entering') return
  } catch { reveal() }
}

function drawVideoFrame() {
  const video = film.value
  if (phase.value !== 'entering' || !video || video.readyState < 2 || !context || !canvas.value) return
  context.globalAlpha = 1
  context.drawImage(video, 0, 0, canvas.value.width, canvas.value.height)
  canvas.value.dataset.source = 'video'
  canvas.value.dataset.videoTime = video.currentTime.toFixed(3)
  filmVisible.value = true
}

function scheduleVideoFrame() {
  if (phase.value !== 'entering' || document.hidden || !film.value) return
  if (typeof film.value.requestVideoFrameCallback === 'function') {
    videoFrameCallback = film.value.requestVideoFrameCallback(() => {
      videoFrameCallback = 0
      drawVideoFrame()
      scheduleVideoFrame()
    })
  } else {
    videoRaf = requestAnimationFrame(() => {
      videoRaf = 0
      drawVideoFrame()
      scheduleVideoFrame()
    })
  }
}

function stopVideoFrames() {
  if (videoFrameCallback) film.value?.cancelVideoFrameCallback(videoFrameCallback)
  videoFrameCallback = 0
  cancelAnimationFrame(videoRaf)
  videoRaf = 0
}

function reveal() {
  if (!visible.value || phase.value === 'leaving') return
  clearTimeout(leaveTimer)
  clearTimeout(openingWatchdog)
  clearTimeout(entryWatchdog)
  cancelAnimationFrame(raf)
  raf = 0
  phase.value = 'leaving'
  stopVideoFrames()
  atmosphere?.settle()
  rememberIntro(storage)
  film.value?.pause()
  exitTimer = window.setTimeout(finish, reduced.value ? 180 : 900)
}

function finish() {
  visible.value = false
  cleanupMedia()
  unlockPage()
  void nextTick(() => {
    if (oldFocus?.isConnected && oldFocus !== document.body) oldFocus.focus({ preventScroll: true })
    else {
      const destination = document.querySelector<HTMLElement>('main, h1, input, [role="main"]')
      if (destination) {
        const previous = destination.getAttribute('tabindex')
        destination.setAttribute('tabindex', '-1')
        destination.focus({ preventScroll: true })
        if (previous === null) destination.removeAttribute('tabindex')
        else destination.setAttribute('tabindex', previous)
      }
    }
  })
}

function keydown(event: KeyboardEvent) {
  if (!visible.value) return
  if (event.key === 'Escape') { event.preventDefault(); reveal(); return }
  if (event.key !== 'Tab') return
  const buttons = [...(dialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])]
  const first = buttons[0]
  const last = buttons[buttons.length - 1]
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.value)) {
    event.preventDefault(); last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault(); first?.focus()
  }
}

function visibilityChanged() {
  atmosphere?.setHidden(document.hidden)
  if (document.hidden) {
    cancelAnimationFrame(raf); raf = 0
    stopVideoFrames()
    film.value?.pause()
  } else if (phase.value === 'entering') {
    scheduleVideoFrame()
    void film.value?.play().catch(reveal)
  } else if (ready.value && visible.value && !reduced.value) requestTarget(target)
}

function preferenceChanged(event: MediaQueryListEvent) {
  reduced.value = event.matches
  if (event.matches) {
    atmosphere?.dispose()
    atmosphere = undefined
    cancelAnimationFrame(raf); raf = 0
    if (busy.value) reveal()
  } else {
    void startAtmosphere()
    if (!frames && visible.value) void prepare()
  }
}

async function prepare() {
  if (reduced.value || !visible.value || !canvas.value) return
  frames = new FlowerFrames(`${assetRoot}/${variant}`)
  const ownFrames = frames
  canvas.value.width = compact ? 768 : 1280
  canvas.value.height = compact ? 432 : 720
  context = canvas.value.getContext('2d', { alpha: false })
  try {
    await ownFrames.prepare()
    if (!mounted || !visible.value || frames !== ownFrames || phase.value === 'leaving') return
    if (!context || !ownFrames.get(168)) throw new Error('Canvas unavailable')
    ready.value = true
    prepareEntry()
    requestTarget(target)
  } catch {
    if (!mounted || phase.value === 'leaving') return
    failed.value = true
    ownFrames.dispose()
  }
}

function unlockPage() {
  if (locked) { document.body.style.overflow = oldOverflow; locked = false }
}

function cleanupMedia() {
  atmosphere?.dispose()
  atmosphere = undefined
  cancelAnimationFrame(raf)
  for (const timer of [leaveTimer, exitTimer, entryWatchdog, openingWatchdog]) clearTimeout(timer)
  frames?.dispose()
  frames = undefined
  film.value?.pause()
  stopVideoFrames()
  film.value?.removeAttribute('src')
  film.value?.load()
}

onMounted(() => {
  mounted = true
  if (!visible.value) return
  oldFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  oldOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  locked = true
  dialog.value?.focus({ preventScroll: true })
  document.addEventListener('visibilitychange', visibilityChanged)
  motionQuery.addEventListener('change', preferenceChanged)
  void prepare()
  void startAtmosphere()
})

onUnmounted(() => {
  mounted = false
  cleanupMedia()
  unlockPage()
  document.removeEventListener('visibilitychange', visibilityChanged)
  motionQuery.removeEventListener('change', preferenceChanged)
})
</script>

<template>
  <div class="intro-content" :inert="visible || undefined" :aria-hidden="visible ? true : undefined">
    <slot />
  </div>
  <Teleport to="body">
    <section v-if="visible" ref="dialog" class="flower-intro" :class="{ 'is-leaving': phase === 'leaving', 'is-entering': filmVisible, 'is-camera': cameraStarted, 'is-committed': busy, 'is-reduced': reduced }"
      role="dialog" aria-modal="true" aria-label="QuantFund 花开序章" tabindex="-1" :data-phase="phase" @keydown="keydown"
      @pointermove="moveAtmosphere" @pointerleave="atmosphere?.centerPointer()">
      <div ref="art" class="intro-art">
        <div ref="parallaxLayer" class="intro-parallax">
        <div ref="breathLayer" class="intro-breath">
        <img ref="posterImage" class="intro-media intro-poster" :src="poster" alt="" fetchpriority="high" @load="posterLoaded" />
        <canvas ref="canvas" class="intro-media intro-frames" aria-hidden="true" :class="{ 'is-visible': frameVisible }" />
        <canvas ref="starCanvas" class="intro-media intro-stars" aria-hidden="true" />
        <video ref="film" class="intro-media intro-film"
          :src="reduced ? undefined : `${assetRoot}/enter.mp4`" :poster="`${assetRoot}/enter-poster.webp`"
          aria-hidden="true" preload="none" muted playsinline disablepictureinpicture @ended="reveal" @error="busy && reveal()" />
        </div>
        </div>
        <button ref="flowerButton" class="flower-hit" type="button" tabindex="0" :disabled="busy || reduced || failed"
          :aria-pressed="expanded" aria-label="花朵开合，悬停或按回车欣赏" @pointerenter="enterFlower" @pointerleave="leaveFlower"
          @focus="focusFlower" @blur="blurFlower" @click="toggleFlower" />
      </div>
      <canvas ref="skyCanvas" class="intro-sky" aria-hidden="true" />
      <header class="intro-masthead">
        <span class="intro-brand">QuantFund<span class="intro-edition">序章 / PROLOGUE</span></span>
      </header>
      <button class="intro-skip" type="button" @click="reveal">跳过<span aria-hidden="true"> · ESC</span></button>
      <div class="intro-invitation">
        <p class="intro-hint" aria-live="polite">{{ hint }}</p>
        <button class="intro-enter" type="button" :disabled="busy" @click="enter">
          <span>进入 QuantFund</span><span class="intro-enter-caption" aria-hidden="true">ENTER THE EXPERIENCE</span>
        </button>
      </div>
      <footer class="intro-colophon" aria-hidden="true"><span>于静谧中，见生长</span><span>A MOMENT BEFORE THE NUMBERS</span></footer>
    </section>
  </Teleport>
</template>

<style scoped>
.intro-content { display: contents; }
.flower-intro { position: fixed; inset: 0; z-index: 10000; overflow: hidden; isolation: isolate; background: #000; color: #e1e5e5; opacity: 1; transition: opacity 900ms cubic-bezier(.4, 0, .2, 1); font-family: "Times New Roman", "Noto Serif SC", "Songti SC", SimSun, serif; outline: none; }
.flower-intro.is-leaving { opacity: 0; }
.intro-art { position: absolute; width: min(100vw, 120vh); aspect-ratio: 16 / 9; left: 50%; top: 40%; transform: translate(-50%, -50%) translateY(0) scale(1); animation: intro-arrive 1400ms ease-out both; transition: transform 5600ms cubic-bezier(.3, 0, .2, 1); }
.is-camera .intro-art { transform: translate(-50%, -50%) translateY(var(--intro-camera-y)) scale(var(--intro-camera-scale)); will-change: transform; }
.intro-parallax, .intro-breath { position: absolute; inset: 0; pointer-events: none; }
.flower-intro:not(.is-reduced) .intro-parallax, .flower-intro:not(.is-reduced) .intro-breath { will-change: transform; }
.intro-stars { opacity: 0; pointer-events: none; }
.intro-sky { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; pointer-events: none; }
.intro-media { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; mask-image: linear-gradient(to right, transparent, #000 12%, #000 88%, transparent), linear-gradient(to bottom, transparent, #000 8%, #000 80%, transparent); mask-composite: intersect; }
.intro-frames { opacity: 0; }
.intro-frames.is-visible { opacity: 1; }
.intro-film { opacity: 0; pointer-events: none; }
.flower-hit { position: absolute; left: 21%; top: 3%; width: 58%; height: 68%; border: 0; border-radius: 50%; padding: 0; background: transparent; color: inherit; cursor: pointer; -webkit-tap-highlight-color: transparent; }
.flower-hit:focus-visible { outline: 1px solid #b7c4d080; outline-offset: 16px; }
.flower-hit:disabled { cursor: default; }
.intro-masthead { position: absolute; top: max(40px, env(safe-area-inset-top)); left: 4.2%; pointer-events: none; transition: opacity 450ms ease; }
.intro-brand { display: flex; flex-direction: column; gap: 10px; font-size: 24px; font-weight: 400; letter-spacing: .04em; }
.intro-edition { font-family: Arial, "Microsoft YaHei", sans-serif; font-size: 9px; letter-spacing: .23em; color: #929e9f; }
.intro-skip { position: absolute; right: 4.2%; top: max(39px, env(safe-area-inset-top)); min-height: 44px; padding: 0 0 0 15px; border: 0; background: transparent; color: #abb7ba; font: 11px Arial, "Microsoft YaHei", sans-serif; letter-spacing: .18em; cursor: pointer; transition: color 200ms ease; }
.intro-skip:hover { color: #fff; }
.intro-skip span { font-size: 9px; opacity: .55; }
.intro-invitation { position: absolute; bottom: 11%; left: 50%; transform: translateX(-50%); text-align: center; width: max-content; max-width: 90%; transition: opacity 550ms ease, transform 700ms ease; }
.intro-hint { margin: 0 0 27px; color: #a3afb7; font-size: 12px; letter-spacing: .3em; font-weight: 400; }
.intro-enter { display: inline-flex; flex-direction: column; align-items: center; gap: 11px; min-height: 62px; padding: 12px 28px 17px; color: #e4e8ec; background: transparent; border: 0; border-bottom: 1px solid #6a767c80; font-size: 17px; letter-spacing: .15em; cursor: pointer; transition: border-color 300ms ease, color 300ms ease; }
.intro-enter-caption { font: 8px Arial, sans-serif; letter-spacing: .24em; color: #86949e; }
.intro-enter:hover { border-color: #d7e1e8; color: #fff; }
.intro-enter:focus-visible, .intro-skip:focus-visible { outline: 1px solid #b7c4d0; outline-offset: 7px; }
.intro-colophon { position: absolute; left: 4.2%; right: 4.2%; bottom: max(30px, env(safe-area-inset-bottom)); display: flex; justify-content: space-between; color: #77868e; font: 9px Arial, "Microsoft YaHei", sans-serif; letter-spacing: .2em; transition: opacity 450ms ease; }
.is-committed .intro-masthead, .is-committed .intro-colophon { opacity: 0; }
.is-committed .intro-invitation { opacity: 0; transform: translate(-50%, 8px); pointer-events: none; }
.is-committed .intro-skip { color: #83929c; }
.is-reduced { transition-duration: 180ms; }
@keyframes intro-arrive { from { opacity: 0; } to { opacity: 1; } }
@media (max-width: 700px) {
  .intro-art { width: 154vw; top: 44%; }
  .intro-masthead { top: max(30px, env(safe-area-inset-top)); left: 7%; }
  .intro-brand { font-size: 22px; }
  .intro-skip { right: 7%; top: max(25px, env(safe-area-inset-top)); }
  .intro-skip span { display: none; }
  .intro-invitation { bottom: max(14%, 90px); }
  .intro-hint { font-size: 11px; letter-spacing: .2em; margin-bottom: 20px; }
  .intro-enter { font-size: 16px; }
  .intro-colophon { left: 7%; right: 7%; bottom: max(28px, env(safe-area-inset-bottom)); font-size: 8px; }
  .intro-colophon span:last-child { display: none; }
}
@media (max-width: 700px) and (max-height: 700px) {
  .intro-art { width: min(154vw, 106vh); top: 42%; }
}
@media (max-height: 540px) and (min-width: 701px) {
  .intro-masthead { top: 20px; }
  .intro-skip { top: 14px; }
  .intro-invitation { bottom: 5%; left: auto; right: 4%; transform: none; }
  .intro-hint { margin-bottom: 10px; }
  .intro-colophon { bottom: 18px; }
  .intro-colophon span:last-child { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .intro-art { animation: none; transition: none; }
  .intro-invitation, .intro-film, .intro-masthead, .intro-colophon { transition: none; }
}
</style>
