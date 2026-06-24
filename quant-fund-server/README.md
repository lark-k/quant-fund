# quant-fund-server

QuantFund 后端模块，基础包名统一为 `com.lk.quantfund`。

本模块已在第二批生成 Spring Boot 3.x 基础工程。当前包含启动类、基础配置、统一响应结构、全局异常处理、基础枚举、MyBatis Plus 配置和 OpenAPI 配置。

## 技术栈

- Java 17 或 Java 21
- Spring Boot 3.x
- Maven
- MySQL 8.x
- MyBatis Plus
- Redis
- Sa-Token
- BCrypt
- Spring Scheduler 或 Quartz
- WebClient 或 OkHttp
- Knife4j / Swagger OpenAPI
- Lombok
- MapStruct 可选
- Hutool 可选

## 目标包结构

```text
src/main/java/com/lk/quantfund
  QuantFundServerApplication.java
  annotation
  ai
  aspect
  auth
  common
  config
  constants
  controller
  datasource
  datasource/impl
  dto
  entity
  enums
  exception
  mapper
  scheduler
  service
  service/impl
  strategy
  strategy/context
  strategy/impl
  util
  vo
src/main/resources
  application.yml
  application-dev.yml
  mapper
```

## 分层职责

| 层 | 职责 |
| --- | --- |
| controller | 参数接收、参数校验、统一响应 |
| service | 业务编排、事务控制、用户数据隔离 |
| mapper | MyBatis Plus 数据访问 |
| entity | 数据库实体 |
| dto | 请求入参 |
| vo | 响应视图对象 |
| enums | 业务枚举、错误码、策略动作 |
| constants | Redis key、系统常量、配置 key |
| auth | Sa-Token 登录态、UserContext |
| annotation | 自定义注解 |
| aspect | 登录校验、数据范围、日志、限流、防重复提交 |
| datasource | 基金数据源统一接口 |
| strategy | 量化策略接口和上下文 |
| ai | DeepSeek 客户端、Prompt、JSON 校验和降级 |
| scheduler | 交易日定时任务 |
| exception | 全局异常和业务异常 |
| util | 脱敏、指纹、金额、时间等工具 |

## 关键工程约束

- Controller 不写复杂业务逻辑。
- Service 从登录上下文获取当前用户 id。
- 创建用户级数据时自动写入 user_id。
- 查询、修改、删除时必须校验 user_id。
- 所有金额使用 `BigDecimal`。
- 所有时间使用 `java.time`。
- 所有异常交给全局异常处理。
- 外部 API 调用必须有超时、重试、降级、限流。
- 日志不得打印密码、token、authorization、secret、apiKey 等敏感信息。
- AI 返回不合法时降级为 WATCH。

## 第二批已生成

第二批已在本目录生成：

- Maven `pom.xml`
- Spring Boot 启动类
- `application.yml` 配置
- `.env.example` 配置示例
- 通用响应结构
- 错误码和基础枚举
- 全局异常处理
- MyBatis Plus 配置
- Knife4j / Swagger 配置

## 当前启动方式

先初始化数据库：

```bash
mysql -uroot -p < ../docs/sql/001_schema.sql
```

再启动后端：

```bash
mvn spring-boot:run
```

本地测试：

```bash
mvn "-Dmaven.repo.local=.m2/repository" test
```

当用户级 Maven 仓库不可写时，上面的命令会把依赖缓存放在当前模块的 `.m2/repository`，该目录已被仓库忽略。

本地配置可参考 `.env.example`。示例文件不包含真实密钥。

健康检查：

```text
GET http://localhost:8080/api/health
```

OpenAPI 页面：

```text
http://localhost:8080/swagger-ui.html
```

Knife4j 页面：

```text
http://localhost:8080/doc.html
```

## 后续待完善

- 前后端真实接口联调
- Docker / 部署脚本
- 更多集成测试和端到端测试

## 第三批后端已生成

第三批后端部分已生成：

- 用户登录、注册、退出、当前用户、资料修改、改密接口
- 重置密码预留接口
- Sa-Token 统一登录拦截配置
- BCrypt 密码加密
- `UserContext` 当前用户上下文
- `user_account` 实体、Mapper、DTO、VO、Service
- 登录鉴权相关测试示例

## 第四批已生成

第四批已生成企业级注解与 AOP 横切能力：

- `@RequireLogin`
- `@DataScope`
- `@OperationLog`
- `@RateLimit`
- `@RepeatSubmit`
- `RequireLoginAspect`
- `DataScopeAspect`
- `OperationLogAspect`
- `RateLimitAspect`
- `RepeatSubmitAspect`
- `OperationLogEntity`
- `OperationLogMapper`
- `OperationLogService`
- `ResourceOwnerService`
- `ResourceOwnerLookup`
- `RedisKeyConstants`
- `SensitiveDataMaskUtil`
- `RequestFingerprintUtil`

说明文档见 `../docs/api/aop.md`。

## 第五批已生成

第五批已生成基金数据源模块：

- `FundDataSourceAdapter`
- `EastMoneyFundDataSourceAdapter`
- `MockFundDataSourceAdapter`
- 基金搜索、基础信息、历史净值、当天估值、手动刷新接口
- 重仓股票、关联主题、同类排名接口
- Redis 缓存和手动刷新冷却
- 外部 API 调用日志 `api_call_log`
- `fund_info`、`fund_nav_daily`、`fund_estimate_intraday` 实体和 Mapper

说明文档见 `../docs/api/fund-datasource.md`。

## 第六批已生成

第六批已生成账户、持仓和模拟交易模块：

- `PortfolioAccount`、`FundHolding`、`TradeRecord` 实体和 Mapper
- 账户创建、更新、列表、详情、汇总、重算接口
- 持仓创建、更新、删除、列表、详情、收益重算接口
- 加仓、减仓、定投、转入、转出模拟交易记录接口
- 完成状态的模拟交易会同步更新持仓，并重算账户汇总
- `PORTFOLIO_ACCOUNT`、`FUND_HOLDING`、`TRADE_RECORD` 数据归属查询已接入 `@DataScope`
- 买卖相关响应包含“仅为模拟操作，并非真实交易”和“仅供参考，不构成投资建议，不承诺收益”

说明文档见 `../docs/api/portfolio-trade.md`。

## 第七批已生成

第七批已生成策略引擎模块：

- `StrategyConfig`、`StrategySignal`、`RiskProfile` 实体和 Mapper
- 基金分类服务、策略上下文和策略规则接口
- 回撤止盈、动态梯度止盈、固定分批止盈、仓位监控、回撤低吸、风险提示规则
- 单持仓策略分析、账户整体策略分析、策略信号查询接口
- 策略配置和风险偏好接口
- `STRATEGY_CONFIG`、`STRATEGY_SIGNAL`、`RISK_PROFILE` 数据归属查询已接入 `@DataScope`
- 策略信号响应包含“仅供参考，不构成投资建议，不承诺收益”

说明文档见 `../docs/api/strategy.md`。

## 第八批已生成

第八批已生成 AI 分析模块：

- DeepSeek OpenAI-compatible `/chat/completions` 客户端
- `response_format: {"type": "json_object"}` JSON 输出约束
- AI Prompt 模板、结构化输入、JSON 校验和 WATCH 降级
- `AiAnalysisReport` 实体和 Mapper
- 单持仓 AI 分析、账户持仓批量 AI 分析、历史报告、重新生成接口
- 默认 AI 关闭、mock 兜底开启，无真实 Key 也能本地启动
- `AI_ANALYSIS_REPORT` 数据归属查询已接入 `@DataScope`
- AI 响应包含“仅供参考，不构成投资建议，不承诺收益”

说明文档见 `../docs/api/ai-analysis.md`。

## 第九批已生成

第九批已生成定时任务模块：

- `@EnableScheduling` 已接入 Spring Scheduler
- 交易日判断服务，支持周末跳过和环境变量配置节假日
- 盘中估值刷新任务：09:30-11:30、13:00-15:00 交易时段
- 14:30、14:45、14:55 重点持仓 AI 分析任务
- 20:00 官方净值同步任务
- 23:00 持仓快照任务
- 周五 23:30 周复盘检查点任务
- `HoldingSnapshot`、`SchedulerTaskLog` 实体和 Mapper
- `scheduler_task_log` 任务执行日志表
- 批量任务随机延迟，降低外部数据源压力
- 定时任务不接入真实交易接口，只生成模拟分析和数据同步

说明文档见 `../docs/api/scheduler.md`。

## 第十批后端补充已生成

本批补齐系统配置与日志查询接口：

- `DataSourceConfig` 实体和 Mapper
- 数据源配置查询、新增/保存、更新接口
- 全局数据源配置 + 用户覆盖配置合并规则
- 操作日志分页查询接口
- API 调用日志分页查询接口
- 日志查询按当前用户隔离，避免跨用户泄露

说明文档见 `../docs/api/system-management.md`。

## 第十一批后端补充已生成

本批补齐基金量化驾驶舱聚合接口：

- `/api/dashboard/overview` 首页总览接口
- 账户资产总览聚合
- Top 10 持仓基金
- 仓位分布数据
- 近 30 天收益走势
- 最新策略信号
- 今日 AI 操作建议
- 今日估值刷新状态
- 风险预警数量和 AI 建议数量
- 响应统一包含投资建议免责声明

说明文档见 `../docs/api/dashboard.md`。

## 第十二批后端补充已生成

本批补齐盈亏分析与盈亏日历接口：

- `/api/analytics/profit` 盈亏分析接口
- `/api/analytics/profit-calendar` 盈亏日历接口
- 当日、本周、本月、今年、全部收益统计
- 选定区间收益走势
- 每日收益热力等级
- 盈利 TOP5 和亏损 TOP5
- 指数对比、盈利用户占比、跑赢指数统计预留状态说明
- 接口只读，不触发真实交易或买卖建议

说明文档见 `../docs/api/analytics.md`。
