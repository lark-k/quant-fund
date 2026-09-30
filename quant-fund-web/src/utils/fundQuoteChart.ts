import type { EChartsCoreOption } from 'echarts/core'
import type { TradeRecord } from '@/types/domain'
import { aggregateNav, macd, movingAverage, periodKey, rsi, type QuotePeriod, type QuotePoint } from './fundQuote'

export interface QuoteChartSettings {
  points: QuotePoint[]
  period: QuotePeriod
  startDate: string
  averages: number[]
  cost: number | null
  trades: TradeRecord[]
  growth: boolean
  drawdown: boolean
  indicator: 'none' | 'MACD' | 'RSI'
  compare: boolean
  indexName: string
  adjusted: boolean
}
const red = '#ff858b', green = '#57dfb3', blue = '#8dccff'
const font = '"Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif'
const gradient = (top: string, bottom: string) => ({ type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: top }, { offset: 1, color: bottom }] })
export const tradeLabel = (type: TradeRecord['tradeType']) => ({ BUY: '申购', SELL: '赎回', REGULAR_INVEST: '定投', CONVERT_IN: '转入', CONVERT_OUT: '转出' })[type]

export function buildQuoteChart(settings: QuoteChartSettings): { option: EChartsCoreOption; height: number; markerCount: number } {
  const { points, period, startDate } = settings
  const all = aggregateNav(points, period)
  const first = all.findIndex(p => p.end >= startDate)
  const bars = first < 0 ? [] : all.slice(first)
  const slice = <T>(values: T[]) => first < 0 ? [] : values.slice(first)
  const dates = bars.map(p => p.date)
  const values = all.map(p => p.close)
  const grids: Record<string, unknown>[] = [], xAxes: Record<string, unknown>[] = [], yAxes: Record<string, unknown>[] = []
  const series: Record<string, unknown>[] = []
  const titles: Record<string, unknown>[] = []
  const legends: Record<string, unknown>[] = []
  const headers: number[] = []
  let top = 52
  function panel(title: string, height: number, percent = false) {
    const id = grids.length
    headers.push(top - 30)
    titles.push({ type: 'text', left: 18, top: top - 28, silent: true, style: { text: title, fill: '#e4edf7', fontFamily: font, fontSize: 14, fontWeight: 600 } })
    grids.push({ left: 18, right: 96, top, height, show: true, borderWidth: 0, backgroundColor: id ? 'rgba(132,166,197,0.035)' : 'transparent' })
    xAxes.push({ type: 'category', gridIndex: id, data: dates, boundaryGap: period !== 'day',
      axisLine: { show: false }, axisLabel: { show: false, color: '#bacbdb', fontFamily: font, fontSize: 12, fontWeight: 500, hideOverlap: true, margin: 14,
        formatter: (date: string) => date },
      axisTick: { show: false }, splitLine: { show: false }, axisPointer: { show: true, label: { show: false, backgroundColor: '#30485e' }, lineStyle: { color: '#6c8195', width: 1, type: [3, 4] } } })
    yAxes.push({ type: 'value', gridIndex: id, scale: true, position: 'right', splitNumber: id ? 2 : 4,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#bacbdb', fontFamily: font, fontSize: 12, fontWeight: 500, margin: 14, hideOverlap: true, formatter: (v: number) => `${Number(v.toFixed(percent ? 2 : 4))}${percent ? '%' : ''}` },
      splitLine: { lineStyle: { color: 'rgba(160,187,212,0.18)', width: 1, type: [2, 4] } } })
    top += height + 46
    return id
  }
  function legend(axis: number, names: string[], left = 185) {
    legends.push({ type: 'scroll', top: headers[axis], left, right: 96, padding: 0, itemWidth: 16, itemHeight: 4, icon: 'roundRect', itemGap: 20,
      textStyle: { color: '#d2deeb', fontSize: 13, fontFamily: font, fontWeight: 500 }, inactiveColor: '#6f8193',
      pageIconColor: '#c3dbef', pageIconInactiveColor: '#465c70', pageIconSize: 12, pageTextStyle: { color: '#bacbdb', fontSize: 12 },
      formatter: (name: string) => {
        const data = series.find(s => s.name === name)?.data as unknown[] | undefined
        const value = data?.[data.length - 1]
        return typeof value === 'number' ? `${name}  ${value.toFixed(4)}` : name
      }, data: names })
  }
  function line(name: string, data: unknown[], axis: number, color: string) {
    series.push({ name, type: 'line', data, xAxisIndex: axis, yAxisIndex: axis, showSymbol: false, connectNulls: false,
      lineStyle: { width: 2.1, color, cap: 'round', join: 'round' }, itemStyle: { color }, emphasis: { disabled: true } })
  }
  function histogram(name: string, data: Array<number | null>, axis: number) {
    series.push({ name, type: 'bar', xAxisIndex: axis, yAxisIndex: axis, itemStyle: { borderRadius: [1, 1, 0, 0] },
      data: data.map(v => ({ value: v, itemStyle: { color: (v ?? 0) >= 0 ? red : green, opacity: 1 } })), barMaxWidth: 7, barCategoryGap: '35%' })
  }
  panel(`${settings.adjusted ? '复权净值' : '单位净值'} · ${period === 'day' ? '日' : period === 'week' ? '周K' : '月K'}`, 390)
  if (period === 'day') {
    line('净值', bars.map(p => p.close), 0, blue)
    series[0]!.lineStyle = { width: 3.2, color: blue, cap: 'round', join: 'round' }
    series[0]!.areaStyle = { color: gradient('rgba(76,151,218,0.2)', 'rgba(38,88,135,0.008)') }
    series[0]!.showSymbol = true
    series[0]!.symbol = 'circle'
    series[0]!.symbolSize = (_: unknown, params: { dataIndex: number }) => params.dataIndex === bars.length - 1 ? 5 : 0
    series[0]!.itemStyle = { color: '#d4ecff', borderColor: '#78b6e8', borderWidth: 2, shadowBlur: 7, shadowColor: 'rgba(117,188,244,0.4)' }
    series[0]!.z = 4
  } else series.push({ name: '净值聚合K线', type: 'candlestick', barMaxWidth: 10,
    data: bars.map(p => p.open === null ? ['-', '-', '-', '-'] : [p.open, p.close, p.low, p.high]),
    itemStyle: { color: red, color0: green, borderColor: red, borderColor0: green, borderWidth: 1 }, emphasis: { itemStyle: { borderWidth: 2 } } })
  const referenceLines: Record<string, unknown>[] = []
  const latest = bars[bars.length - 1]?.close
  if (latest != null) referenceLines.push({ yAxis: latest, lineStyle: { color: blue, width: 1.2, opacity: .8, type: [3, 5] },
    label: { show: true, formatter: latest.toFixed(4), position: 'end', color: '#ffffff', backgroundColor: '#285982', opacity: 1, borderRadius: 3, padding: [5, 7], fontFamily: font, fontSize: 13, fontWeight: 600 } })
  if (settings.cost !== null && settings.cost > 0) {
    const cost = settings.cost
    referenceLines.push({ yAxis: cost, lineStyle: { color: '#f2cf7c', width: 1.4, type: [5, 5], opacity: .9 },
      label: { formatter: `成本 ${cost.toFixed(4)}`, position: 'insideStartTop', color: '#f2cf7c', fontSize: 13, fontWeight: 600, fontFamily: font,
        backgroundColor: '#101b26', borderRadius: 3, padding: [3, 5] } })
    yAxes[0]!.min = (extent: { min: number; max: number }) => Math.min(extent.min, cost) * .98
    yAxes[0]!.max = (extent: { min: number; max: number }) => Math.max(extent.max, cost) * 1.02
  }
  series[0]!.markLine = { silent: true, symbol: 'none', animation: false, data: referenceLines }
  const colors = ['#f2cf7c', '#c0adf5', '#69d5c5', '#eeaad1']
  settings.averages.forEach((n, i) => {
    const color = colors[i % colors.length]!
    line(`MA${n}`, slice(movingAverage(values, n)), 0, color)
    series[series.length - 1]!.lineStyle = { width: 1.8, color, opacity: 1, cap: 'round', join: 'round' }
  })
  legend(0, [period === 'day' ? '净值' : '净值聚合K线', ...settings.averages.map(n => `MA${n}`)])

  const pointByDate = new Map(points.map(p => [p.date, p]))
  const barByPeriod = new Map(bars.map(p => [periodKey(p.date, period), p]))
  let markerCount = 0
  for (const buy of [true, false]) {
    const data = settings.trades.flatMap(t => {
      if (['BUY', 'REGULAR_INVEST', 'CONVERT_IN'].includes(t.tradeType) !== buy) return []
      const date = t.tradeTime.slice(0, 10), point = pointByDate.get(date)
      const bar = barByPeriod.get(periodKey(date, period))
      if (!point || !bar || point.factor === null || date < startDate || !(t.tradeNav && t.tradeNav > 0)) return []
      markerCount++
      return [{ value: [bar.date, t.tradeNav * point.factor], tradeId: t.id, name: `${tradeLabel(t.tradeType)} · ${date}` }]
    })
    series.push({ name: buy ? '买入记录' : '卖出记录', type: 'scatter', data, symbol: 'triangle', symbolSize: 11,
      symbolRotate: buy ? 0 : 180, itemStyle: { color: buy ? red : green, borderColor: '#0d1823', borderWidth: 1.5, opacity: .95 }, z: 8,
      label: { show: false }, emphasis: { scale: 1.6, label: { show: true, formatter: buy ? '买入' : '卖出', position: 'top', color: '#dce8f3', fontSize: 10, backgroundColor: '#22384b', padding: [4, 6], borderRadius: 3 } } })
  }
  if (settings.growth) histogram(period === 'day' ? '日涨跌幅' : '周期涨跌幅', bars.map(p => p.growth), panel('涨跌幅  %', 68, true))
  if (settings.drawdown) {
    line('回撤', bars.map(p => p.drawdown), panel('回撤  %', 68, true), '#69d5c5')
    series[series.length - 1]!.areaStyle = { color: gradient('rgba(67,187,155,0.025)', 'rgba(67,187,155,0.13)') }
  }
  if (settings.indicator === 'MACD') {
    const axis = panel('MACD  12 · 26 · 9', 80), result = macd(values)
    histogram('MACD', slice(result.histogram), axis)
    line('DIF', slice(result.dif), axis, blue)
    line('DEA', slice(result.dea), axis, '#f2cf7c')
    legend(axis, ['DIF', 'DEA'])
  } else if (settings.indicator === 'RSI') {
    const axis = panel('RSI  14', 80)
    yAxes[axis]!.min = 0; yAxes[axis]!.max = 100
    line('RSI', slice(rsi(values)), axis, '#c0adf5')
  }
  if (settings.compare) {
    const axis = panel('参考对比  %', 90, true)
    const base = bars.find(p => p.index !== null && p.index > -100 && p.close !== null)
    line(settings.adjusted ? '基金复权变动' : '基金单位净值变动', bars.map(p => base && p.date >= base.date && p.close !== null ? (p.close / base.close! - 1) * 100 : null), axis, blue)
    line(settings.indexName, bars.map(p => base && p.date >= base.date && p.index !== null ? ((1 + p.index / 100) / (1 + base.index! / 100) - 1) * 100 : null), axis, '#f2cf7c')
    legend(axis, [settings.adjusted ? '基金复权变动' : '基金单位净值变动', settings.indexName])
  }
  const bottomAxis = xAxes[xAxes.length - 1]!
  ;(bottomAxis.axisLabel as Record<string, unknown>).show = true
  ;((bottomAxis.axisPointer as Record<string, unknown>).label as Record<string, unknown>).show = true
  const axes = grids.map((_, i) => i)
  return { height: top + 34, markerCount, option: {
    animation: false, backgroundColor: 'transparent', textStyle: { color: '#d2deeb', fontFamily: font },
    legend: legends,
    tooltip: { trigger: 'axis', renderMode: 'richText', axisPointer: { type: 'cross', crossStyle: { color: '#71899d', type: 'dashed' } },
      backgroundColor: 'rgba(20,35,49,0.97)', borderColor: '#334d64', borderWidth: 1, padding: [12, 16],
      textStyle: { color: '#edf4fb', fontSize: 13, fontFamily: font }, confine: true,
      valueFormatter: (v: unknown) => typeof v === 'number' ? Number(v.toFixed(4)).toString() : '--' },
    axisPointer: { link: [{ xAxisIndex: 'all' }], label: { backgroundColor: '#30485e', fontFamily: font, fontSize: 13 } },
    graphic: titles, grid: grids, xAxis: xAxes, yAxis: yAxes, series,
    dataZoom: [{ type: 'inside', xAxisIndex: axes, filterMode: 'none' }, { type: 'slider', xAxisIndex: axes, filterMode: 'none',
      left: 18, right: 96, bottom: 8, height: 22, showDetail: false, brushSelect: false, moveHandleSize: 0,
      borderColor: '#243747', borderRadius: 3, backgroundColor: '#101e2b', fillerColor: 'rgba(76,140,192,0.09)',
      dataBackground: { lineStyle: { color: '#47647e', width: 1 }, areaStyle: { color: '#1b344b', opacity: .3 } },
      selectedDataBackground: { lineStyle: { color: '#8dccff', width: 1.5 }, areaStyle: { color: '#2a4c69', opacity: .4 } },
      handleIcon: 'path://M-2,-12L2,-12L2,12L-2,12Z', handleSize: '75%', handleStyle: { color: '#7291ad', borderWidth: 0 },
      emphasis: { handleStyle: { color: '#acd4f6' } }, textStyle: { color: '#bacbdb', fontFamily: font } }]
  } }
}
