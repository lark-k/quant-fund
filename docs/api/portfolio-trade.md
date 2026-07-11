# 账户、持仓、模拟交易与定投

本模块管理当前用户的基金账户、持仓、模拟交易流水和定投计划。所有接口需要登录；写操作会进行资源归属、限流、防重复提交和操作日志校验。

> 所有交易记录仅为模拟记账，并非真实交易。系统不连接交易平台或自动下单。

## 账户

Base path：`/api/portfolios`

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/portfolios` | 创建账户 |
| `GET /api/portfolios` | 查询当前用户账户 |
| `GET /api/portfolios/summary` | 聚合资产、投入、收益和仓位 |
| `GET /api/portfolios/{id}` | 账户详情 |
| `PUT /api/portfolios/{id}` | 更新名称、平台、状态和仓位上限 |
| `GET /api/portfolios/{id}/holdings` | 查询账户持仓 |
| `POST /api/portfolios/{id}/recalculate` | 根据持仓重算账户 |

`platformType`：`ALIPAY`、`EASTMONEY`、`BROKER`、`MANUAL`。

## 持仓

Base path：`/api/holdings`

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/holdings` | 新增持仓 |
| `GET /api/holdings` | 查询持仓，可按 `accountId`、`fundCode` 过滤 |
| `POST /api/holdings/sync-official-nav` | 同步当前用户持仓的正式净值 |
| `GET /api/holdings/{id}` | 持仓详情 |
| `PUT /api/holdings/{id}` | 更新持仓 |
| `DELETE /api/holdings/{id}` | 逻辑删除持仓 |
| `POST /api/holdings/{id}/clear` | 清仓并生成对应模拟卖出记录 |
| `POST /api/holdings/{id}/recalculate` | 重算单个持仓收益 |

计算原则：

- 金额、份额、净值和比例统一使用 `BigDecimal`；
- 盘中优先使用 `currentEstimateNav`，日终使用 `latestOfficialNav`；
- 持有收益为当前金额减持仓成本；
- 当日收益基于当前估值/正式净值与上一正式净值的差额；
- 持仓变化后同步重算所属账户，并写入盘中快照。

## 模拟交易

Base path：`/api/trades`

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/trades` | 按请求中的 `tradeType` 创建记录 |
| `POST /api/trades/buy` | 模拟买入 |
| `POST /api/trades/sell` | 模拟卖出 |
| `POST /api/trades/regular-invest` | 模拟定投 |
| `POST /api/trades/convert-in` | 模拟转入 |
| `POST /api/trades/convert-out` | 模拟转出 |
| `POST /api/trades/convert-pair` | 原基金转出和目标基金转入成对创建 |
| `POST /api/trades/regular-invest/compensate-due` | 手动补跑到期定投计划 |
| `GET /api/trades` | 查询流水，可按账户、持仓、类型和状态过滤 |
| `GET /api/trades/processing` | 查询在途记录 |
| `POST /api/trades/settle-due` | 手动结算已到确认日的在途记录 |
| `GET /api/trades/{id}` | 流水详情 |
| `DELETE /api/trades/{id}` | 删除仍处于可删除状态的在途记录 |

`tradeType`：`BUY`、`SELL`、`REGULAR_INVEST`、`CONVERT_IN`、`CONVERT_OUT`。

`tradeStatus`：`PROCESSING`、`COMPLETED`、`CANCELLED`、`FAILED`。

状态规则：

- `PROCESSING`、`CANCELLED`、`FAILED` 不直接改变持仓；
- `COMPLETED` 的买入、定投和转入增加份额和成本；
- `COMPLETED` 的卖出和转出按份额比例减少份额和成本；
- 卖出/转出必须有归属当前用户的持仓和可计算的份额；
- 成对转换在一个业务操作中创建相互关联的转出与转入记录；
- 基金确认日到达后，调度任务或手动接口完成在途结算。

## 定投计划

Base path：`/api/investment-plans`

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/investment-plans` | 创建计划 |
| `PUT /api/investment-plans/{id}` | 更新计划 |
| `GET /api/investment-plans?accountId={id}` | 查询全部或指定账户计划 |
| `PUT /api/investment-plans/{id}/status?status={status}` | 启用/暂停计划 |
| `DELETE /api/investment-plans/{id}` | 删除计划 |

工作日 09:05 的调度任务检查到期计划并生成模拟定投记录，09:10 结算到期在途交易。停机或任务异常后可使用补偿接口手动补跑。

## 数据隔离

业务服务从 `UserContext` 读取当前用户，请求体不能决定 `user_id`。以下资源接入所有者查询：

- `PORTFOLIO_ACCOUNT`
- `FUND_HOLDING`
- `TRADE_RECORD`
- `INVESTMENT_PLAN`

账户、持仓、流水和计划之间的引用也会再次校验，避免通过属于自己的请求引用其他用户资源。

## 响应提示

持仓与交易响应包含：

- `simulatedTradeNotice`：`仅为模拟操作，并非真实交易`；
- `disclaimer`：`仅供参考，不构成投资建议，不承诺收益`。
