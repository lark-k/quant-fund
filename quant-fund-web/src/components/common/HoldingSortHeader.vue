<script setup lang="ts">
import type { HoldingSort, HoldingSortKey, SortDirection } from '@/stores/holdingSort'

const props = defineProps<{ label: string; sortLabel?: string; field: HoldingSortKey; sort: HoldingSort }>()
const emit = defineEmits<{ change: [key: HoldingSortKey, direction: SortDirection] }>()
function toggle() {
  emit('change', props.field, props.sort.key === props.field && props.sort.direction === 'desc' ? 'asc' : 'desc')
}
</script>

<template>
  <span class="holding-sort-header" :class="{ active: sort.key === field }">
    <button type="button" class="sort-label" :title="`按${sortLabel || label}排序`" @click="toggle">{{ label }}</button>
    <span class="sort-arrows">
      <button v-for="direction in (['asc', 'desc'] as const)" :key="direction" type="button"
        :class="['sort-arrow', direction, { selected: sort.key === field && sort.direction === direction }]"
        :aria-label="`${sortLabel || label}${direction === 'asc' ? '递增' : '递减'}排序`"
        :aria-pressed="sort.key === field && sort.direction === direction"
        :title="`${sortLabel || label}${direction === 'asc' ? '递增' : '递减'}排序`"
        @click="emit('change', field, direction)"><span aria-hidden="true" /></button>
    </span>
  </span>
</template>

<style scoped>
.holding-sort-header { display: inline-flex; align-items: center; gap: 2px; max-width: 100%; }
.holding-sort-header button { border: 0; padding: 0; background: transparent; color: inherit; font: inherit; cursor: pointer; }
.holding-sort-header .sort-label { text-align: left; white-space: nowrap; overflow-wrap: normal; }
.holding-sort-header.active, .holding-sort-header button:hover { color: #72c9d8; }
.holding-sort-header button:focus-visible { outline: 1px solid #72c9d8; outline-offset: 1px; border-radius: 2px; }
.sort-arrows { display: inline-flex; flex-direction: column; flex-shrink: 0; }
.holding-sort-header .sort-arrow { display: flex; align-items: center; justify-content: center; width: 14px; height: 12px; color: #647e8d; }
.holding-sort-header .sort-arrow.selected, .holding-sort-header .sort-arrow:hover { color: #72c9d8; }
.sort-arrow span { width: 0; height: 0; border-left: 4px solid transparent; border-right: 4px solid transparent; }
.sort-arrow.asc span { border-bottom: 4px solid currentColor; }
.sort-arrow.desc span { border-top: 4px solid currentColor; }
</style>
