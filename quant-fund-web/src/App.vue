<script setup lang="ts">
import { RouterView } from 'vue-router'
import FirstVisitIntro from './components/intro/FirstVisitIntro.vue'
</script>

<template>
  <el-config-provider size="default">
    <FirstVisitIntro>
      <RouterView />
    </FirstVisitIntro>
  </el-config-provider>
</template>
