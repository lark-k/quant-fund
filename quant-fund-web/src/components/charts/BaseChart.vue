<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart, ScatterChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

echarts.use([LineChart, BarChart, PieChart, ScatterChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

type ChartOption = Record<string, unknown>

const props = defineProps<{
  option: ChartOption
  height?: number
}>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

async function render() {
  await nextTick()
  if (!chartRef.value) return
  chart ||= echarts.init(chartRef.value, undefined, { renderer: 'canvas' })
  chart.setOption(props.option as EChartsCoreOption, true)
  chart.resize()
}

const resize = () => chart?.resize()

onMounted(() => {
  render()
  if (chartRef.value && 'ResizeObserver' in window) {
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(chartRef.value)
  }
  window.addEventListener('resize', resize)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="chartRef" class="base-chart" :style="{ height: `${height || 260}px` }"></div>
</template>
