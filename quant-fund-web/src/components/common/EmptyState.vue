<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
}>(), {
  title: '暂无数据',
  description: ''
})
</script>

<template>
  <div class="empty-state">
    <strong>{{ title }}</strong>
    <span v-if="description">{{ description }}</span>
  </div>
</template>
