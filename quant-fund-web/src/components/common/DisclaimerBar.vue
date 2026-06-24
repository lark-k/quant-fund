<script setup lang="ts">
import { DISCLAIMER, SIMULATED_TRADE_NOTICE } from '@/types/domain'

defineProps<{
  simulated?: boolean
}>()
</script>

<template>
  <div class="disclaimer-bar" :class="{ strong: simulated }">
    <span>{{ simulated ? SIMULATED_TRADE_NOTICE : DISCLAIMER }}</span>
  </div>
</template>
