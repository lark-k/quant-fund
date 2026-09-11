<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{ loading: boolean; failed: boolean; compact: boolean }>()
const completed = ref(false)
const elapsed = ref(0)
let clock: ReturnType<typeof setInterval> | undefined
let dismiss: ReturnType<typeof setTimeout> | undefined
const waitText = computed(() => `${String(Math.floor(elapsed.value / 60)).padStart(2, '0')}:${String(elapsed.value % 60).padStart(2, '0')}`)
const title = computed(() => completed.value ? '量化建议已更新' : props.compact ? '正在更新量化建议' : '正在加载量化建议')

function clearTimers() {
  clearInterval(clock)
  clearTimeout(dismiss)
}

watch(() => props.loading, (loading, wasLoading) => {
  clearTimers()
  completed.value = false
  if (loading) {
    elapsed.value = 0
    const started = Date.now()
    clock = setInterval(() => { elapsed.value = Math.floor((Date.now() - started) / 1000) }, 1000)
  } else if (wasLoading && !props.failed) {
    completed.value = true
    dismiss = setTimeout(() => { completed.value = false }, 900)
  }
}, { immediate: true })

onBeforeUnmount(clearTimers)
</script>

<template>
  <div v-if="loading || completed" class="quant-progress" :class="{ 'is-compact': compact, 'is-complete': completed }">
    <div class="quant-progress-heading">
      <div class="quant-progress-label" role="status" aria-live="polite">
        <svg class="quant-progress-chip" viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <path d="M11 2v5m10-5v5M11 25v5m10-5v5M2 11h5m-5 10h5m18-10h5m-5 10h5" />
          <rect x="7" y="7" width="18" height="18" rx="3" />
          <path v-if="completed" d="m11 16 3 3 7-7" />
          <path v-else d="M12 12h8v8h-8z" />
        </svg>
        <div>
          <span class="quant-progress-eyebrow">QUANT SIGNAL / SYNC</span>
          <strong>{{ title }}</strong>
        </div>
      </div>
      <span class="quant-progress-time" aria-hidden="true">{{ completed ? '同步完成' : `已等待 ${waitText}` }}</span>
    </div>

    <div class="quant-progress-track" role="progressbar" :aria-label="title" :aria-valuenow="completed ? 100 : undefined" :aria-valuetext="completed ? '加载完成' : '正在等待数据返回'">
      <div class="quant-progress-beam"></div>
    </div>

    <div v-if="!compact" class="quant-progress-stages" aria-hidden="true">
      <span class="is-done"><i>✓</i>请求已发送</span>
      <span :class="completed ? 'is-done' : 'is-active'"><i>{{ completed ? '✓' : '02' }}</i>读取建议</span>
      <span :class="{ 'is-done': completed }"><i>{{ completed ? '✓' : '03' }}</i>更新完成</span>
    </div>
    <p v-if="!completed" class="quant-progress-hint" role="status">
      {{ elapsed >= 6 ? '本次响应较慢，仍在加载中，请稍候…' : compact ? '正在获取最新建议，当前结果仍可查看' : '正在读取最新建议，完成后自动展示' }}
    </p>
  </div>
</template>

<style scoped>
.quant-progress {
  --progress-accent: #67e8f9;
  position: relative;
  overflow: hidden;
  padding: 24px;
  border: 1px solid rgba(103, 232, 249, .2);
  border-radius: 8px;
  background:
    linear-gradient(rgba(103, 232, 249, .025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(103, 232, 249, .025) 1px, transparent 1px),
    linear-gradient(120deg, #10252d, #0b1921);
  background-size: 24px 24px, 24px 24px, auto;
  box-shadow: inset 0 1px rgba(103, 232, 249, .06);
}
.quant-progress-heading, .quant-progress-label {
  display: flex;
  align-items: center;
  gap: 12px;
}
.quant-progress-heading { justify-content: space-between; flex-wrap: wrap; row-gap: 8px; }
.quant-progress-chip { width: 32px; height: 32px; flex: none; stroke: var(--progress-accent); stroke-width: 1.4; }
.quant-progress-label strong { display: block; color: #e2f6fb; font-size: 15px; line-height: 1.5; }
.quant-progress-eyebrow { display: block; margin-bottom: 3px; font: 10px/1.4 Consolas, monospace; letter-spacing: 1.5px; color: #8bb8c4; }
.quant-progress-time { color: #a6c9d2; font: 12px/1.5 Consolas, monospace; font-variant-numeric: tabular-nums; white-space: nowrap; }
.quant-progress-track {
  height: 10px;
  position: relative;
  overflow: hidden;
  margin: 24px 0 18px;
  border: 1px solid rgba(103, 232, 249, .2);
  border-radius: 3px;
  background: #08151c;
}
.quant-progress-track::after {
  content: '';
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(90deg, transparent 0, transparent 15px, #0b1921 15px, #0b1921 18px);
}
.quant-progress-beam {
  width: 45%;
  height: 100%;
  background: linear-gradient(90deg, transparent, #159cbb 40%, var(--progress-accent) 85%, #d5fbff);
  box-shadow: 0 0 16px rgba(103, 232, 249, .5);
  animation: quant-progress-scan 2.2s ease-in-out infinite;
}
.quant-progress-stages { display: flex; justify-content: space-between; gap: 8px; }
.quant-progress-stages span { display: flex; align-items: center; gap: 7px; color: #90a5b0; font-size: 12px; }
.quant-progress-stages i { display: grid; place-items: center; width: 22px; height: 22px; border: 1px solid #314b56; border-radius: 50%; font: 10px Consolas, monospace; }
.quant-progress-stages .is-done { color: #a4d3dc; }
.quant-progress-stages .is-done i { color: var(--progress-accent); border-color: #397483; background: #11333c; }
.quant-progress-stages .is-active { color: var(--progress-accent); }
.quant-progress-stages .is-active i { border-color: var(--progress-accent); box-shadow: 0 0 10px #67e8f926; }
.quant-progress-hint { margin: 18px 0 0; color: #9ab6c2; font-size: 12px; line-height: 1.6; }
.quant-progress.is-compact { padding: 12px 16px; }
.is-compact .quant-progress-eyebrow { display: none; }
.is-compact .quant-progress-chip { width: 22px; height: 22px; }
.is-compact .quant-progress-label strong { font-size: 12px; }
.is-compact .quant-progress-track { height: 6px; margin: 10px 0 0; }
.is-compact .quant-progress-hint { margin-top: 8px; }
.quant-progress.is-complete { --progress-accent: #75e8bd; border-color: #75e8bd40; }
.is-complete .quant-progress-beam { width: 100%; animation: none; background: var(--progress-accent); }
.is-complete .quant-progress-time { color: var(--progress-accent); }
@keyframes quant-progress-scan { from { transform: translateX(-110%); } to { transform: translateX(235%); } }
@media (max-width: 480px) {
  .quant-progress { padding: 16px 12px; }
  .quant-progress-eyebrow { font-size: 9px; letter-spacing: 1px; }
  .quant-progress-label { gap: 8px; }
  .quant-progress-label strong { font-size: 13px; }
  .quant-progress-stages span { font-size: 11px; gap: 4px; }
  .quant-progress-time { font-size: 11px; }
}
@media (prefers-reduced-motion: reduce) {
  .quant-progress-beam { animation: none; transform: translateX(60%); }
  .is-complete .quant-progress-beam { transform: none; }
}
</style>
