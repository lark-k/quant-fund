import type { DashboardOverview, FundHolding, FundNavPoint, ProfitAnalysis, ProfitCalendar, TradeRecord } from '@/types/domain'

type TrendPoint = {
  date: string
  holdingProfit?: number
  cumulativeProfit?: number
  dailyProfitRate?: number
  indexReturnRate?: number | null
}

type ReturnTrendPoint = {
  label: string
  portfolioReturn: number | null
  indexReturn: number | null
}

const axis = {
  axisLine: { lineStyle: { color: '#263844' } },
  axisLabel: { color: '#7f93a1', fontSize: 11 },
  splitLine: { lineStyle: { color: '#20313a', type: 'dashed' } }
}

export function profitTrendOption(data: DashboardOverview['profitTrend'] | ProfitAnalysis['trend'], showSyntheticIndex = false) {
  const points: TrendPoint[] = data
  const firstValue = points[0]?.holdingProfit ?? points[0]?.cumulativeProfit ?? 1
  const profitRates = points.map((item, index) => {
    const value = item.holdingProfit ?? item.cumulativeProfit ?? firstValue
    return Number((((value - firstValue) / Math.max(Math.abs(firstValue), 1)) * 18 + index * 0.42).toFixed(2))
  })
  const indexRates = points.map((_, index) => Number((Math.sin(index / 2.8) * 3 + index * 0.55 - 1.2).toFixed(2)))
  const historicalIndexRates = points.map((item) => item.indexReturnRate ?? null)
  const hasHistoricalIndex = historicalIndexRates.some((value) => value !== null)
  let peak = profitRates[0] ?? 0
  const drawdowns = profitRates.map((value) => {
    peak = Math.max(peak, value)
    return Number((value - peak).toFixed(2))
  })
  const series: Array<Record<string, unknown>> = [
    { name: '组合收益', type: 'line', smooth: true, showSymbol: false, data: profitRates },
    { name: '最大回撤', type: 'line', smooth: true, showSymbol: false, data: drawdowns, lineStyle: { width: 0 }, areaStyle: { opacity: 0.46 } }
  ]
  if (hasHistoricalIndex) {
    series.splice(1, 0, { name: '沪深300', type: 'line', smooth: true, showSymbol: false, data: historicalIndexRates })
  } else if (showSyntheticIndex) {
    series.splice(1, 0, { name: '沪深300', type: 'line', smooth: true, showSymbol: false, data: indexRates })
  }

  return {
    color: ['#3c9cff', '#ffb84d', '#30c978'],
    tooltip: { trigger: 'axis', backgroundColor: '#101a20', borderColor: '#283b46', textStyle: { color: '#d7e3ea' } },
    legend: { top: 0, textStyle: { color: '#8ea0ad' } },
    grid: { top: 34, right: 16, bottom: 24, left: 44 },
    xAxis: { type: 'category', data: points.map((item) => item.date.slice(5)), ...axis },
    yAxis: { type: 'value', axisLabel: { color: '#7f93a1', fontSize: 11, formatter: '{value}%' }, axisLine: axis.axisLine, splitLine: axis.splitLine },
    series
  }
}

export function returnTrendOption(data: ReturnTrendPoint[], indexName = '沪深300') {
  return {
    backgroundColor: 'transparent',
    color: ['#ff514b', '#3c9cff'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#101a20',
      borderColor: '#283b46',
      textStyle: { color: '#d7e3ea' },
      valueFormatter: (value: number | string) => `${Number(value).toFixed(2)}%`
    },
    legend: { show: false },
    grid: { top: 20, right: 12, bottom: 28, left: 44 },
    xAxis: {
      type: 'category',
      data: data.map((item) => item.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#263844' } },
      axisLabel: { color: '#7f93a1', fontSize: 11 },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#7f93a1', fontSize: 11, formatter: '{value}%' },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#20313a', type: 'dashed' } }
    },
    series: [
      {
        name: '我的收益',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: data.map((item) => item.portfolioReturn),
        lineStyle: { width: 2 },
        connectNulls: true
      },
      {
        name: indexName,
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: data.map((item) => item.indexReturn),
        lineStyle: { width: 2 },
        connectNulls: true
      }
    ]
  }
}

export function positionOption(data: DashboardOverview['positionDistribution']) {
  return {
    color: ['#3c9cff', '#ffb84d', '#30c978', '#8b7cf6'],
    tooltip: { trigger: 'item', backgroundColor: '#101a20', borderColor: '#283b46', textStyle: { color: '#d7e3ea' } },
    legend: { right: 8, top: 'center', orient: 'vertical', textStyle: { color: '#9badb8', fontSize: 12 } },
    series: [{
      name: '持仓分布',
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['34%', '52%'],
      label: { show: false },
      data: data.map((item) => ({ name: item.name, value: item.rate }))
    }]
  }
}

export function estimateHoldingOption(data: FundHolding[]) {
  const points = data.slice(0, 8)
  return {
    color: ['#ff514b', '#3c9cff'],
    tooltip: { trigger: 'axis', backgroundColor: '#101a20', borderColor: '#283b46', textStyle: { color: '#d7e3ea' } },
    legend: { top: 0, textStyle: { color: '#8ea0ad' } },
    grid: { top: 38, right: 42, bottom: 26, left: 52 },
    xAxis: { type: 'category', data: points.map((item) => item.fundCode), ...axis },
    yAxis: [
      { type: 'value', axisLabel: { color: '#7f93a1', fontSize: 11, formatter: '{value}' }, axisLine: axis.axisLine, splitLine: axis.splitLine },
      { type: 'value', axisLabel: { color: '#7f93a1', fontSize: 11, formatter: '{value}%' }, axisLine: axis.axisLine, splitLine: { show: false } }
    ],
    series: [
      {
        name: '当日收益',
        type: 'bar',
        data: points.map((item) => item.dailyProfit),
        itemStyle: {
          color: (params: { value: number }) => params.value >= 0 ? '#ff514b' : '#2fd17c'
        }
      },
      {
        name: '板块收益率',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: true,
        data: points.map((item) => item.relatedThemeRate || item.currentEstimateGrowthRate || 0)
      }
    ]
  }
}

export function calendarBarOption(data: ProfitCalendar['days']) {
  return {
    color: ['#3c9cff'],
    tooltip: { trigger: 'axis', backgroundColor: '#101a20', borderColor: '#283b46', textStyle: { color: '#d7e3ea' } },
    grid: { top: 16, right: 12, bottom: 22, left: 44 },
    xAxis: { type: 'category', data: data.map((item) => item.date.slice(8)), ...axis },
    yAxis: { type: 'value', ...axis },
    series: [{
      name: '每日盈亏',
      type: 'bar',
      data: data.map((item) => item.dailyProfit),
      itemStyle: {
        color: (params: { value: number }) => params.value >= 0 ? '#ff514b' : '#2fd17c'
      }
    }]
  }
}

export function fundNavOption(data: FundNavPoint[], trades: TradeRecord[] = []) {
  const peakSeries: number[] = []
  let peak = data[0]?.nav || 1
  const firstNav = data[0]?.nav || 1
  const drawdown = data.map((item) => {
    peak = Math.max(peak, item.nav)
    peakSeries.push(peak)
    return Number(((item.nav - peak) / peak * 100).toFixed(2))
  })
  const fundReturn = data.map((item) => Number(((item.nav - firstNav) / Math.max(firstNav, 0.0001) * 100).toFixed(2)))
  const indexReturn = data.map((item) => item.indexReturnRate ?? null)
  const hasIndexReturn = indexReturn.some((value) => value !== null)
  const indexName = data.find((item) => item.indexReturnRate !== null && item.indexReturnRate !== undefined)?.indexName || '沪深300'
  const tradeByDate = new Map<string, TradeRecord[]>()
  for (const trade of trades) {
    const day = trade.tradeTime.slice(0, 10)
    tradeByDate.set(day, [...(tradeByDate.get(day) || []), trade])
  }
  const buyPoints = data.flatMap((point, index) => {
    const tradesOnDay = (tradeByDate.get(point.date) || []).filter((trade) => ['BUY', 'REGULAR_INVEST', 'CONVERT_IN'].includes(trade.tradeType))
    return tradesOnDay.map(() => [point.date.slice(5), fundReturn[index]])
  })
  const sellPoints = data.flatMap((point, index) => {
    const tradesOnDay = (tradeByDate.get(point.date) || []).filter((trade) => ['SELL', 'CONVERT_OUT'].includes(trade.tradeType))
    return tradesOnDay.map(() => [point.date.slice(5), fundReturn[index]])
  })

  const series: Array<Record<string, unknown>> = [
    { name: '本基金', type: 'line', smooth: true, showSymbol: false, data: fundReturn, areaStyle: { opacity: 0.08 } },
    { name: '买入', type: 'scatter', symbolSize: 9, data: buyPoints, itemStyle: { color: '#ff514b' }, z: 8 },
    { name: '卖出', type: 'scatter', symbolSize: 9, data: sellPoints, itemStyle: { color: '#2fd17c' }, z: 8 },
    { name: '当前回撤', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false, data: drawdown, lineStyle: { width: 0 }, areaStyle: { opacity: 0.12 } }
  ]
  if (hasIndexReturn) {
    series.splice(1, 0, { name: indexName, type: 'line', smooth: true, showSymbol: false, data: indexReturn })
  }

  return {
    color: ['#6e91ff', '#ff9657', '#ff514b', '#2fd17c'],
    tooltip: { trigger: 'axis', backgroundColor: '#101a20', borderColor: '#283b46', textStyle: { color: '#d7e3ea' } },
    legend: { top: 0, textStyle: { color: '#8ea0ad' } },
    grid: { top: 34, right: 16, bottom: 24, left: 46 },
    xAxis: { type: 'category', data: data.map((item) => item.date.slice(5)), ...axis },
    yAxis: [
      { type: 'value', axisLabel: { color: '#7f93a1', fontSize: 11, formatter: '{value}%' }, axisLine: axis.axisLine, splitLine: axis.splitLine },
      { type: 'value', axisLabel: { color: '#7f93a1', fontSize: 11, formatter: '{value}%' }, axisLine: axis.axisLine, splitLine: { show: false } }
    ],
    series
  }
}
