<script setup lang="ts">
import { computed } from 'vue'
import { actionTone } from '@/utils/format'

const props = defineProps<{
  action: string
  text?: string
}>()

const tone = computed(() => actionTone(props.action))
</script>

<template>
  <span class="action-tag" :class="`tag-${tone}`">{{ text || action }}</span>
</template>
