# 基金量化驾驶舱接口

驾驶舱接口为 PC 首页和手机首页提供聚合数据，减少前端首屏请求数量。接口只读，需要登录。

## 首页总览

```http
GET /api/dashboard/overview
```

返回内容：

| 字段 | 说明 |
| --- | --- |
| `summary` | 账户资产总览：总资产、总投入、总收益、收益率、当日收益、仓位比例、持仓数量。 |
| `topHoldings` | 按持仓金额排序的前 10 个持仓基金。 |
| `positionDistribution` | 权益基金、债券基金、现金仓位分布，可直接用于饼图。 |
| `profitTrend` | 近 30 天持仓快照收益走势，可用于收益折线图。 |
| `latestStrategySignals` | 最新 10 条策略信号。 |
| `todayAiSuggestions` | 今日 AI 操作建议，最多 10 条。 |
| `estimateStatus` | 今日持仓基金估值刷新状态。 |
| `riskAlertCount` | 高风险、减仓或转换类策略信号数量。 |
| `aiSuggestionCount` | 今日 AI 建议数量。 |
| `disclaimer` | “仅供参考，不构成投资建议，不承诺收益”。 |

示例响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "summary": {},
    "topHoldings": [],
    "positionDistribution": [
      { "name": "权益基金", "rate": 60.0 },
      { "name": "债券基金", "rate": 20.0 },
      { "name": "现金", "rate": 20.0 }
    ],
    "profitTrend": [],
    "latestStrategySignals": [],
    "todayAiSuggestions": [],
    "estimateStatus": {
      "trackedFundCount": 5,
      "refreshedTodayCount": 5,
      "delayedCount": 0,
      "latestEstimateTime": "2026-06-23 14:55:00",
      "statusText": "今日估值已刷新"
    },
    "riskAlertCount": 1,
    "aiSuggestionCount": 3,
    "disclaimer": "仅供参考，不构成投资建议，不承诺收益"
  },
  "timestamp": "2026-06-23 15:00:00"
}
```

## 前端使用建议

PC 首页建议展示：

- 顶部资产总览卡片使用 `summary`
- 持仓列表使用 `topHoldings`
- 仓位分布图使用 `positionDistribution`
- 收益走势图使用 `profitTrend`
- 策略信号列表使用 `latestStrategySignals`
- AI 今日操作建议使用 `todayAiSuggestions`
- 估值刷新状态使用 `estimateStatus`

手机首页建议保留：

- `summary.totalAsset`
- `summary.dailyProfit`
- `topHoldings`
- `todayAiSuggestions`
- `estimateStatus.statusText`

所有买卖建议都必须展示 `disclaimer`。买卖相关页面还必须展示“仅为模拟操作，并非真实交易”。

## 市场读数

```http
GET /api/dashboard/market-readings
```

返回驾驶舱关注的 A 股、港股和美股市场指数，包括指数代码、名称、最新点位、涨跌幅、更新时间和数据状态。数据用于首页市场卡片，也会作为量化分析的市场上下文。

市场数据来自后端 `MarketDataService` 和本地 `market_index_daily` 缓存。外部数据不足时会保留明确的缺失/延迟状态，不将 Mock 值伪装成真实行情。

## 市场交易状态

```http
GET /api/dashboard/market-status
```

返回各市场当前是否交易日、是否处于交易时段、开闭市时间、下一状态说明和整体状态文案。交易日判断结合：

- `Asia/Shanghai` 当前时间；
- 周末；
- A 股、港股和美股各自节假日配置；
- 各市场交易时段。

前端使用该接口展示开市状态；量化任务和调度任务仍在后端独立执行交易日校验，不能只依赖前端显示。
