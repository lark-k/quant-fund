# 基金优选与验证

基金优选模块从公开基金全集构建独立的可投资基金池，计算多维因子和质量分，提供分级榜单、单基金解释和前瞻验证。所有接口需要登录，但筛选数据本身是系统级数据，不属于某个用户持仓。

Base path：`/api/fund-screener`

## 数据流水线

```text
同步基金全集
  → 同步筛选专用历史净值
  → 重建可投资池
  → 计算因子快照
  → 计算质量分和推荐等级
  → 增量回测与分层验证
```

模块使用独立表：

- `screener_fund_universe`
- `screener_fund_nav_daily`
- `screener_universe_filter`
- `screener_factor_snapshot`
- `screener_quality_score`
- `screener_backtest_result`

首次使用前必须执行 `008_fund_screener.sql`、`009_fund_screener_v2.sql` 和 `010_fund_screener_validation_loop.sql`。

## 榜单与解释

### 查询榜单

```http
GET /api/fund-screener/rank
```

查询参数：

| 参数 | 说明 |
| --- | --- |
| `fundType` | 基金类型，如 `ACTIVE_EQUITY`、`MIXED`、`INDEX` |
| `period` | 评分周期过滤 |
| `recommendLevel` | `STRONG`、`WATCH`、`NEUTRAL`、`AVOID` |
| `minScore` | 最低质量分 |
| `minFundSize` | 最低基金规模 |
| `excludeShareClassC` | 是否排除 C 类份额，默认 `true` |
| `onlyActiveFund` | 是否只看主动基金，默认 `false` |
| `pageNo`、`pageSize` | 分页 |
| `sortBy` | 排序字段，前端默认 `qualityScore` |

榜单返回基金资料、因子日期、质量分、推荐等级、分项评分和可投资状态。

### 单基金解释

```http
GET /api/fund-screener/{fundCode}/explain
```

返回基金池信息、过滤结果、因子、质量分拆解、策略阈值、推荐等级、优势与风险说明。

## 手动任务

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/fund-screener/sync-universe` | 同步基金全集 |
| `POST /api/fund-screener/sync-nav` | 同步筛选基金净值 |
| `POST /api/fund-screener/rebuild-universe` | 重建有效可投资池 |
| `POST /api/fund-screener/refresh-factors` | 重算因子快照 |
| `POST /api/fund-screener/refresh-score` | 重算质量分和等级 |
| `POST /api/fund-screener/refresh-full` | 按顺序执行上述完整刷新 |

任务响应包含 `taskName`、`status`、成功/失败/跳过数量、耗时和错误摘要。完整刷新可能耗时较长，并受接口限流保护。

## 验证回测

| 方法与路径 | 作用 |
| --- | --- |
| `GET /api/fund-screener/backtest` | 查询/触发兼容的回测入口 |
| `POST /api/fund-screener/backtest` | 执行幂等的增量前瞻回测 |
| `GET /api/fund-screener/backtest/validation` | 聚合当前验证结论 |

验证按评分日期、模型版本、推荐桶和 20/60/120 个未来净值样本聚合：

- 样本数、评分日期数和可用覆盖；
- 平均/中位未来收益；
- 正收益率、相对基准表现和显著性；
- 强关注、观察、中性、回避各桶的区分度；
- 配置阈值是否满足最低样本要求。

回测单元使用唯一键保证同一评分日、窗口、桶和模型版本可重复增量执行而不产生重复记录。

## 评分与策略配置

质量评分综合收益质量、回撤控制、多周期一致性、可投资性、数据质量、相对基准和基金年龄等信息。推荐阈值通过后端环境变量配置：

- `QUANTFUND_SCREENER_STRONG_MIN_SCORE`
- `QUANTFUND_SCREENER_STRONG_TOP_PERCENT`
- `QUANTFUND_SCREENER_WATCH_MIN_SCORE`
- `QUANTFUND_SCREENER_WATCH_TOP_PERCENT`
- `QUANTFUND_SCREENER_NEUTRAL_MIN_SCORE`
- `QUANTFUND_SCREENER_MIN_VALIDATION_SAMPLES`
- `QUANTFUND_SCREENER_MIN_VALIDATION_SCORE_DATES`

前端展示的策略政策来自后端配置，避免 UI 与实际评分阈值漂移。

## 自动任务

`QUANTFUND_SCHEDULER_SCREENER_ENABLED=true` 时，交易日晚上依次执行：

- 21:30 同步基金全集；
- 22:00 同步筛选净值；
- 23:00 重建可投资池；
- 23:20 重算因子；
- 23:40 重算质量分；
- 23:50 增量回测。

具体调度和总开关见[定时任务文档](scheduler.md)。

## 风险边界

- 质量分和推荐等级是研究筛选结果，不是买入指令。
- 外部公开数据可能延迟或缺失，数据不足的基金会被过滤或降低数据质量分。
- 前瞻验证衡量历史区分度，不保证未来收益。
- 基金优选与用户持仓量化是两个模块，榜单结果不能替代个人仓位和风险判断。
