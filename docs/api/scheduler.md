# 定时任务

QuantFund 使用 Spring Scheduler 执行模拟交易结算、数据同步、量化/AI 分析、快照和基金优选任务。时区统一为 `Asia/Shanghai`，不接入真实交易下单。

## 配置

配置前缀：`quantfund.scheduler`

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 任务总开关；关闭后任务跳过并记录日志 |
| `screener-enabled` | `true` | 基金优选任务独立开关 |
| `holidays` | 内置 2026 A 股节假日 | A 股休市日，逗号分隔 |
| `hong-kong-holidays` | 内置 2026 港股节假日 | 港股休市日 |
| `us-holidays` | 内置 2026 美股节假日 | 美股休市日 |
| `batch-delay-max-millis` | `800` | 批处理单项之间的随机等待上限 |
| `ai-focus-holding-limit` | `200` | 重点 AI 分析持仓上限 |
| `snapshot-backfill-trading-days` | `30` | 快照回补交易日范围 |

对应环境变量为 `QUANTFUND_SCHEDULER_ENABLED`、`QUANTFUND_SCHEDULER_SCREENER_ENABLED`、`QUANTFUND_TRADING_HOLIDAYS`、`QUANTFUND_HK_TRADING_HOLIDAYS`、`QUANTFUND_US_TRADING_HOLIDAYS` 等。

## 交易与持仓任务

| 任务名 | 时间 | 交易日限制 | 作用 |
| --- | --- | --- | --- |
| `CREATE_DUE_REGULAR_INVEST_TRADES` | 工作日 09:05 | 是 | 为到期且启用的定投计划生成模拟交易 |
| `SETTLE_DUE_PROCESSING_TRADES` | 工作日 09:10 | 是 | 结算达到确认日的在途模拟交易 |
| `REFRESH_MORNING_INTRADAY_ESTIMATES` | 09:30–11:30，每 2 分钟 | 是 | 刷新上午盘中估值、持仓和账户 |
| `REFRESH_AFTERNOON_INTRADAY_ESTIMATES` | 13:00–15:00，每 2 分钟 | 是 | 刷新下午盘中估值和账户 |
| `GENERATE_QUANT_SIGNALS` | 09:45、10:30、11:20、13:30、14:30、14:50、14:55 | 是 | 调用 Python 批量生成量化信号 |
| `AI_FOCUS_HOLDING_ANALYSIS` | 14:50、14:55 | 是 | 对重点持仓生成结构化 AI 报告 |
| `SYNC_OFFICIAL_NAV` | 15:30–21:45 每 15 分钟，22:00 | 是 | 在正式净值逐步发布后同步并重算 |
| `CREATE_HOLDING_SNAPSHOTS` | 每日 23:00 | 否 | 保存持仓快照并支持缺失快照回补 |
| `WEEKLY_REVIEW_CHECKPOINT` | 周五 23:30 | 否 | 保存周复盘检查点 |

交易日判断会结合周末、A 股/港股/美股节假日和基金市场类型。手动补偿入口：

- `POST /api/trades/regular-invest/compensate-due`
- `POST /api/trades/settle-due`

## 基金优选任务

| 任务名 | 时间 | 作用 |
| --- | --- | --- |
| `SCREENER_SYNC_UNIVERSE` | 工作日 21:30 | 同步基金全集 |
| `SCREENER_SYNC_NAV` | 工作日 22:00 | 同步筛选净值 |
| `SCREENER_REBUILD_UNIVERSE` | 工作日 23:00 | 重建可投资池 |
| `SCREENER_REFRESH_FACTORS` | 工作日 23:20 | 计算因子快照 |
| `SCREENER_REFRESH_QUALITY_SCORE` | 工作日 23:40 | 计算质量分与推荐等级 |
| `SCREENER_INCREMENTAL_BACKTEST` | 工作日 23:50 | 执行增量前瞻验证 |

这些任务除受总开关控制外，还要求 `screener-enabled=true`。

## 写入与日志

- `trade_record`：定投生成和在途交易结算；
- `fund_holding`、`portfolio_account`：估值/净值和交易完成后的重算；
- `fund_estimate_intraday`、`fund_nav_daily`、`market_index_daily`：行情数据；
- `portfolio_intraday_snapshot`、`holding_snapshot`：盘中和日终快照；
- `quant_signal`、`strategy_signal`、`ai_analysis_report`：量化和 AI 结果；
- `screener_*`：基金优选流水线；
- `scheduler_task_log`：任务状态、触发方式、耗时、成功/失败/跳过数和错误摘要。

任务状态包括 `SUCCESS`、`PARTIAL_SUCCESS`、`FAILED`、`SKIPPED`。批量任务允许单项失败并继续处理后续对象。

## 安全边界

- 任务只同步数据、生成参考信号、保存模拟交易和分析记录；
- 不调用真实交易平台，不自动买入或卖出；
- DeepSeek Key 只从本地配置或环境变量读取；
- 所有建议继续显示“仅供参考，不构成投资建议，不承诺收益”。
