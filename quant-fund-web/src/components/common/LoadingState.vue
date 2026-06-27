<script setup lang="ts">
withDefaults(defineProps<{
  text?: string
}>(), {
  text: '数据加载中'
})
</script>

<template>
  <div class="loading-state">
    <div class="loading-card" role="status" aria-live="polite">
      <div class="loading-orbit" aria-hidden="true">
        <span></span>
        <i></i>
      </div>
      <div class="loading-copy">
        <strong>{{ text }}</strong>
        <span>正在同步行情、净值与持仓指标</span>
      </div>
      <div class="loading-pulse" aria-hidden="true">
        <span></span>
        <span></span>
        <span></span>
      </div>
      <div class="loading-bar" aria-hidden="true"></div>
    </div>
  </div>
</template>
