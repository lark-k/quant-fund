# 定时任务模块

QuantFund 使用 Spring Scheduler 执行后台批处理任务。定时任务只做数据同步、模拟分析和历史快照，不接入真实交易下单接口，不会自动买入或卖出基金。

所有 AI 或策略相关结论仍必须展示：

> 仅供参考，不构成投资建议，不承诺收益。

## 配置项

配置前缀：`quantfund.scheduler`

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用定时任务逻辑。关闭后任务会跳过并记录日志。 |
| `holidays` | 空 | 非交易日列表，格式为 `yyyy-MM-dd,yyyy-MM-dd`。周六、周日默认非交易日。 |
| `batch-delay-max-millis` | `800` | 批量处理单只基金或单个持仓后的随机等待上限，降低外部数据源压力。 |
| `ai-focus-holding-limit` | `200` | 定时 AI 分析的重点持仓数量上限。 |

环境变量示例见 `quant-fund-server/.env.example`。

## 任务清单

| 任务名 | 时间 | 交易日限制 | 说明 |
| --- | --- | --- | --- |
| `REFRESH_MORNING_INTRADAY_ESTIMATES` | 09:30-11:30，每 2 分钟 | 是 | 刷新持仓基金的盘中估值，更新持仓收益并重算账户。 |
| `REFRESH_AFTERNOON_INTRADAY_ESTIMATES` | 13:00-15:00，每 2 分钟 | 是 | 刷新下午盘中估值，服务 15:00 前的参考分析。 |
| `AI_FOCUS_HOLDING_ANALYSIS` | 14:30、14:45、14:55 | 是 | 对核心持仓或重点关注持仓生成 AI 分析报告。默认走真实 DeepSeek 配置，mock 需显式开启。 |
| `SYNC_OFFICIAL_NAV` | 20:00 | 是 | 同步官方净值，更新持仓金额、收益和账户汇总。 |
| `CREATE_HOLDING_SNAPSHOTS` | 23:00 | 否 | 保存每日持仓快照，用于收益曲线和历史复盘。 |
| `WEEKLY_REVIEW_CHECKPOINT` | 周五 23:30 | 否 | 记录周复盘检查点，后续可扩展为周报生成。 |

所有定时任务统一写入 `scheduler_task_log`，状态包括 `SUCCESS`、`PARTIAL_SUCCESS`、`FAILED`、`SKIPPED`。

## 数据写入

- `fund_holding`：盘中估值和官方净值同步会更新估值、净值、持仓金额、收益、收益率、日收益。
- `portfolio_account`：持仓变化后会调用账户重算，更新总资产、当前收益、日收益和仓位比例。
- `holding_snapshot`：每日记录持仓快照。
- `ai_analysis_report`：重点持仓 AI 分析保存结构化报告。
- `scheduler_task_log`：记录任务耗时、成功数量、失败数量和错误摘要。

## 安全边界

- 不提供真实交易接口。
- 不自动调用支付宝、天天基金、券商等平台。
- 定时任务生成的买入、卖出、持有、转换建议只作为模拟参考。
- DeepSeek API Key 只从环境变量读取，不写入代码。
