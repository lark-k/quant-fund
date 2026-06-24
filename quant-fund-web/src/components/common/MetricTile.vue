<script setup lang="ts">
defineProps<{
  label: string
  value: string
  subLabel?: string
  delta?: string
  tone?: 'rise' | 'fall' | 'neutral' | 'warning' | 'info'
}>()
</script>

<template>
  <section class="metric-tile">
    <div class="metric-label">{{ label }}</div>
    <div class="metric-value" :class="tone ? `metric-${tone}` : ''">{{ value }}</div>
    <div v-if="subLabel || delta" class="metric-sub">
      <span>{{ subLabel }}</span>
      <strong :class="tone ? `metric-${tone}` : ''">{{ delta }}</strong>
    </div>
  </section>
</template>
