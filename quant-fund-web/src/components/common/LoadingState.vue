<script setup lang="ts">
withDefaults(defineProps<{
  text?: string
}>(), {
  text: '数据加载中'
})
</script>

<template>
  <div class="loading-state">
    <span class="loading-dot"></span>
    <span>{{ text }}</span>
  </div>
</template>
