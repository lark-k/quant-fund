# QuantFund Server

`quant-fund-server` 是 QuantFund 的业务核心服务，负责用户鉴权和数据隔离、基金数据接入、账户/持仓/模拟交易、策略与风险、任务调度、AI 编排，以及 Python 量化引擎的调用和结果持久化。

## 技术栈

- Java 21、Spring Boot 3.5、Maven
- MyBatis-Plus、MySQL 8.4
- Sa-Token、BCrypt、Spring AOP
- Spring Data Redis、Spring Scheduler
- WebClient、Knife4j / OpenAPI

## 模块结构

```text
src/main/java/com/lk/quantfund/
├─ controller/    # REST API、参数校验和统一响应
├─ service/       # 业务接口、事务编排和实现
├─ entity/        # MySQL 实体
├─ mapper/        # MyBatis-Plus 数据访问
├─ datasource/    # 东方财富与 Mock 基金数据适配器
├─ strategy/      # Java 规则策略与基金分类
├─ quant/         # FastAPI 量化引擎客户端
├─ ai/            # DeepSeek 请求、Prompt、JSON 校验和降级
├─ scheduler/     # 交易日历、定时任务和任务日志
├─ auth/          # 当前用户上下文
├─ aspect/        # 鉴权、数据范围、日志、限流和防重
├─ dto/           # 请求和跨服务数据结构
├─ vo/            # API 响应视图
├─ config/        # Spring、Sa-Token、WebClient 和业务配置
└─ exception/     # 业务异常与统一异常处理
```

## 业务能力

| 模块 | 能力 |
| --- | --- |
| Auth | 注册、登录、退出、资料、修改密码、Sa-Token 登录态 |
| Fund | 搜索、资料、净值、估值、重仓股、主题、同类排名和缓存 |
| Portfolio | 多账户、持仓、正式净值同步、收益与仓位重算 |
| Trade | 模拟买卖、定投、转换、在途结算、到期交易补偿 |
| Investment Plan | 定投计划创建、更新、启停、删除和自动生成 |
| Strategy | Java 规则分析、策略配置和个人风险偏好 |
| Quant | Python 单持仓/账户分析、信号持久化和批量回测 |
| Fund Screener | 基金池、净值、因子、评分、榜单、解释和验证回测 |
| AI | DeepSeek 结构化分析、历史报告、重生成和保守降级 |
| Analytics | 驾驶舱、市场状态、区间/盘中收益、指数对比和收益日历 |
| System | 数据源配置、健康状态、AI 运行配置和日志查询 |

## 关键边界

- Controller 只做参数和响应处理，业务逻辑放在 Service。
- 用户级数据归属来自 `UserContext`，不信任前端传入的 `user_id`。
- `@DataScope` 对账户、持仓、交易、策略、风险偏好和 AI 报告等资源做所有权校验。
- 所有金额使用 `BigDecimal`，时间使用 `java.time`。
- 外部接口调用设置超时并记录 `api_call_log`；日志对密码、Token、Secret 和 API Key 脱敏。
- AI 只能解释结构化上下文，异常时降级为 `WATCH`；量化硬约束不能被 AI 或 ML 覆盖。
- 交易记录只用于模拟记账，系统不提供真实下单能力。

## 本地启动

先按[本地开发与联调指南](../docs/deploy/local-integration.md)启动 MySQL、Redis、量化引擎并完成 `001`–`010` 数据库结构初始化。

```powershell
Copy-Item .env.docker.example .env
mvn spring-boot:run
```

如果用户 Maven 仓库不可写：

```powershell
mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run
```

常用地址：

| 地址 | 用途 |
| --- | --- |
| <http://127.0.0.1:8080/api/health> | 服务健康检查 |
| <http://127.0.0.1:8080/api/health/dependencies> | MySQL / Redis 依赖检查 |
| <http://127.0.0.1:8080/doc.html> | Knife4j |
| <http://127.0.0.1:8080/swagger-ui.html> | Swagger UI |
| <http://127.0.0.1:8080/v3/api-docs> | OpenAPI JSON |

## 配置

Spring 会从模块目录、仓库根目录等位置加载 `quant-fund-server/.env`。不要提交真实密钥。

| 配置组 | 主要变量 |
| --- | --- |
| MySQL | `QUANTFUND_DB_URL`、`QUANTFUND_DB_USERNAME`、`QUANTFUND_DB_PASSWORD` |
| Redis | `QUANTFUND_REDIS_HOST`、`QUANTFUND_REDIS_PORT`、`QUANTFUND_REDIS_PASSWORD` |
| DeepSeek | `DEEPSEEK_ENABLED`、`DEEPSEEK_MOCK_ENABLED`、`DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL` |
| 基金数据 | `QUANTFUND_FUND_MOCK_FALLBACK_ENABLED` |
| Quant Engine | `QUANT_ENGINE_ENABLED`、`QUANT_ENGINE_BASE_URL`、`QUANT_ENGINE_MODEL_VERSION`、各类阈值与超时 |
| Scheduler | `QUANTFUND_SCHEDULER_ENABLED`、`QUANTFUND_SCHEDULER_SCREENER_ENABLED`、各市场节假日 |
| Screener | `QUANTFUND_SCREENER_*` 推荐等级和验证样本阈值 |

完整默认值见 [`application.yml`](src/main/resources/application.yml)，Compose 环境示例见 [`.env.docker.example`](.env.docker.example)。

默认行为：

- 基金数据优先使用东方财富真实数据源，Mock fallback 关闭。
- Python 量化引擎启用，地址为 `http://127.0.0.1:8091`，Java fallback 关闭。
- DeepSeek 路径启用但 Mock 关闭；没有 Key 或调用失败时返回保守结果。
- 定时任务和基金优选定时流水线启用，可分别通过环境变量关闭。

## API 分组

| 前缀 | 模块 |
| --- | --- |
| `/api/auth` | 用户与登录 |
| `/api/funds` | 基金数据 |
| `/api/portfolios`、`/api/holdings` | 账户与持仓 |
| `/api/trades`、`/api/investment-plans` | 模拟交易与定投 |
| `/api/strategies` | Java 策略与风险偏好 |
| `/api/quant`、`/api/backtests` | Python 量化与回测 |
| `/api/fund-screener` | 基金优选 |
| `/api/ai-analysis` | AI 分析 |
| `/api/dashboard`、`/api/analytics` | 驾驶舱与收益分析 |
| `/api/system` | 配置和日志 |

详细说明见 [文档中心](../docs/README.md#api-与模块文档)，实时请求/响应结构以 Knife4j/OpenAPI 为准。

## 定时任务

定时任务使用 `Asia/Shanghai` 时区，主要覆盖：

- 09:05 到期定投生成、09:10 到期在途交易结算；
- A 股交易时段每 2 分钟刷新盘中估值；
- 日内多个检查点生成量化信号，14:50/14:55 生成重点 AI 分析；
- 15:30–22:00 尝试同步正式净值；
- 23:00 持仓快照、周五 23:30 周复盘检查点；
- 21:30–23:50 基金优选同步、因子、评分和增量验证。

完整时间表见[定时任务文档](../docs/api/scheduler.md)。

## 测试

```powershell
mvn test
```

测试覆盖 Controller、Service、策略规则、数据源、AI 校验、AOP、调度、交易日历、基金优选、量化分析和回测编排。

## 延伸阅读

- [项目总体设计](../docs/01-project-overall-design.md)
- [本地开发与联调](../docs/deploy/local-integration.md)
- [API 与模块文档](../docs/README.md#api-与模块文档)
- [Python 量化引擎](../quant-engine/README.md)
