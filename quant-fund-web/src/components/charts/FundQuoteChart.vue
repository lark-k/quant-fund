<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, CandlestickChart, LineChart, ScatterChart } from 'echarts/charts'
import { AxisPointerComponent, DataZoomComponent, GraphicComponent, GridComponent, LegendComponent, MarkLineComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

echarts.use([BarChart, CandlestickChart, LineChart, ScatterChart, AxisPointerComponent, GraphicComponent,
  DataZoomComponent, GridComponent, LegendComponent, MarkLineComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ option: EChartsCoreOption; height: number }>()
const emit = defineEmits<{ trade: [id: number] }>()
const host = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
let observer: ResizeObserver | undefined
let disposed = false
async function render() {
  await nextTick()
  if (disposed || !host.value) return
  if (!chart) {
    chart = echarts.init(host.value, undefined, { renderer: 'canvas', devicePixelRatio: Math.min(3, Math.max(2, window.devicePixelRatio || 1)) })
    chart.on('click', params => {
      const item = params.data as { tradeId?: number } | undefined
      if (item?.tradeId != null) emit('trade', item.tradeId)
    })
  }
  chart.setOption(props.option, { notMerge: true })
  chart.resize()
}
onMounted(() => {
  void render()
  observer = new ResizeObserver(() => chart?.resize())
  if (host.value) observer.observe(host.value)
})
watch(() => props.option, render)
watch(() => props.height, async () => { await nextTick(); chart?.resize() })
onBeforeUnmount(() => { disposed = true; observer?.disconnect(); chart?.dispose(); chart = undefined })
</script>

<template>
  <div ref="host" class="fund-quote-chart" :style="{ height: `${height}px` }" role="img" aria-label="基金净值行情图，可滚轮缩放、拖动，悬停查看数据；买卖记录也可在下方列表查看"></div>
</template>
