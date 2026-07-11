# QuantFund 文档中心

这里汇总 QuantFund 的现行使用文档、模块/API 说明，以及开发过程中保留的历史设计和实施记录。

> 运行方式和接口行为以当前代码、配置文件及 OpenAPI 为准。`superpowers/`、实施计划和 prompt 文档记录当时的设计过程，不代表当前待办事项。

## 新用户从这里开始

1. 阅读根目录 [README](../README.md) 了解项目能力和整体架构。
2. 按[本地开发与联调指南](deploy/local-integration.md)启动 MySQL、Redis、量化引擎、后端和前端。
3. 通过后端 Knife4j `http://127.0.0.1:8080/doc.html` 查看实时接口定义。
4. 需要理解模块边界时阅读[项目总体设计](01-project-overall-design.md)。

## 运行与架构文档

| 文档 | 用途 | 状态 |
| --- | --- | --- |
| [项目总体设计](01-project-overall-design.md) | 三服务架构、核心数据流、模块边界和安全约束 | 现行 |
| [本地开发与联调](deploy/local-integration.md) | 环境、数据库脚本、启动、配置、验证和排障 | 现行 |
| [后端模块 README](../quant-fund-server/README.md) | Spring Boot 分层、配置、启动、API 和测试 | 现行 |
| [前端模块 README](../quant-fund-web/README.md) | 页面、路由、Mock/真实接口、启动和测试 | 现行 |
| [量化引擎 README](../quant-engine/README.md) | 规则模型、回测、LightGBM、API 和测试 | 现行 |

## API 与模块文档

| 文档 | 覆盖范围 |
| --- | --- |
| [认证](api/auth.md) | 注册、登录、用户资料和密码 |
| [AOP 横切能力](api/aop.md) | 登录校验、数据范围、操作日志、限流和防重 |
| [基金数据源](api/fund-datasource.md) | 搜索、净值、估值、重仓股、主题、排名和缓存 |
| [账户、持仓与模拟交易](api/portfolio-trade.md) | 账户、持仓、交易、定投计划和结算 |
| [Java 策略](api/strategy.md) | 策略分析、配置、风险偏好和规则 |
| [量化分析与回测](api/quant-backtest.md) | Python 信号、回测、净值缓存和训练样本 |
| [基金优选](api/fund-screener.md) | 基金池、因子、评分、榜单和验证闭环 |
| [AI 分析](api/ai-analysis.md) | DeepSeek 调用、输入输出、降级和持久化 |
| [定时任务](api/scheduler.md) | 交易任务、净值、快照、基金优选和开关 |
| [系统配置与日志](api/system-management.md) | 数据源、健康状态、运行配置和日志 |
| [驾驶舱](api/dashboard.md) | 资产总览、市场读数和交易时段 |
| [收益分析](api/analytics.md) | 区间收益、指数对比、盘中走势和收益日历 |

接口文档优先级：

1. 当前 Controller、DTO/VO 和配置源码；
2. 运行中的 Knife4j/OpenAPI；
3. `docs/api` 中的说明文档。

## 数据库脚本

`sql/` 不是自动迁移框架。新数据库按以下规则初始化：

- `001_schema.sql`：基础结构，Compose 仅在空数据卷首次创建时自动执行。
- `002_seed_demo.sql`：可选、可重复执行的演示数据。
- `003`–`010`：按编号执行一次的增量结构脚本，部分脚本不可重复执行。

具体命令见[本地开发与联调](deploy/local-integration.md#2-初始化数据库结构)。

## QA 与视觉记录

| 文档/目录 | 说明 |
| --- | --- |
| [本地 smoke 记录](qa/local-smoke-2026-06-24.md) | 2026-06-24 当时环境的验证快照，不代表当前持续测试结果 |
| [设计 QA 清单](../design-qa.md) | 视觉方向确认和实现检查记录 |
| [`quant-fund-web/qa-artifacts/`](../quant-fund-web/qa-artifacts/) | 已纳入版本管理的前端视觉 QA 截图与诊断结果 |

仓库根目录的 `qa-artifacts/` 是本地临时测试产物，已被 Git 忽略，不作为 GitHub 文档资源。

## 历史设计与实施记录

以下文档用于追溯设计决策和实施过程，内容可能已被当前代码取代，不应直接当作运行手册：

- [`superpowers/specs/`](superpowers/specs/)：基金优选相关设计规格。
- [`superpowers/plans/`](superpowers/plans/)：已执行的实施计划。
- [量化引擎实施方案](quant-engine-implementation-plan.md)：量化引擎从规则模型到 ML 的阶段性设计。
- [基金优选实施 prompt](fund-screener-implementation-prompt.md)：基金优选初始实施上下文。
- [过夜实施 prompt](quantfund-overnight-implementation-prompt.md)：早期缺口修复和验收上下文。

历史文档中的“待实现”“下一批”“Phase”等表述描述的是文档创建时的状态。判断当前功能时，请回到根 README、现行模块文档和源码。

## 文档维护规则

- 新增或修改外部可见功能时，同步更新根 README、对应模块 README 和 `docs/api`。
- 新增数据库脚本时更新本索引及本地联调文档，并说明是否可重复执行。
- 调整端口、环境变量或启动命令时，以实际配置文件为依据同步更新文档。
- 设计规格和实施计划作为历史记录保留，完成后不改写为当前手册。
- 所有本地链接使用仓库相对路径，提交前运行链接和 Markdown 围栏检查。
