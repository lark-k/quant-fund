# QuantFund

QuantFund 是一个基金量化交易辅助工具，中文展示名为 **QuantFund 基金量化驾驶舱**，前端系统标题统一为 **QuantFund - AI Fund Quant Dashboard**。

本项目目标是帮助用户基于基金历史数据、当天估值、持仓收益、账户仓位、量化规则和 AI 分析，在每天 15:00 前获得买入、卖出、持有、转换等参考建议。系统不直接自动下单，不接入真实交易下单接口，所有买卖操作仍由用户在支付宝、天天基金、券商等原平台完成。

所有买卖建议必须展示：

> 仅供参考，不构成投资建议，不承诺收益。

所有买卖相关页面还必须展示：

> 仅为模拟操作，并非真实交易。

## 项目命名

| 类型 | 名称 |
| --- | --- |
| 项目名称 | QuantFund |
| 项目根目录 | quant-fund |
| 后端模块 | quant-fund-server |
| 前端模块 | quant-fund-web |
| 数据库 | quant_fund |
| Java 基础包名 | com.lk.quantfund |
| 前端标题 | QuantFund - AI Fund Quant Dashboard |

## 当前已生成范围

当前项目已按分批方式生成到后端核心模块、前端核心页面、响应式适配、测试与文档补充阶段。

当前已包含：

- 项目架构设计
- MySQL 初始化建表 SQL
- Spring Boot 后端工程
- 用户登录、注册、鉴权和用户数据隔离基础能力
- 企业级注解与 AOP 横切能力
- 基金数据源、账户、持仓、模拟交易、策略、AI、定时任务、系统管理、驾驶舱、盈亏分析等后端模块
- Vue 3 + Vite + TypeScript 前端工程
- Product Design 选定视觉方向的 PC 端基金量化驾驶舱
- 登录、注册、基金详情、持仓、交易、AI 分析、盈亏分析、盈亏日历、策略配置、系统配置、个人资料等前端页面
- 响应式移动端核心能力
- 后端单元测试示例、AOP 测试示例、Mock 数据和启动说明
- Docker Compose 本地 MySQL / Redis 依赖服务

当前仍需继续完善：

- 前后端真实接口联调
- 生产部署脚本
- 更多端到端测试和移动端截图复验

## 设计与结构

- 数据流设计
- 核心业务流程
- 模块边界说明
- 技术选型说明

## 顶层结构

```text
quant-fund/
  README.md
  docs/
    01-project-overall-design.md
  docs/
    sql/
      001_schema.sql
  quant-fund-server/
    pom.xml
    README.md
    src/
  quant-fund-web/
    README.md
```

后续批次会继续补充：

- `quant-fund-server`：Java 17/21 + Spring Boot 3.x 后端服务。
- `quant-fund-web`：Vue 3 + Vite + TypeScript 前端应用。
- `docs/sql`：MySQL 初始化脚本和测试数据。
- `docs/api`：RESTful API 说明。
- `docs/deploy`：本地启动、Docker、联调说明。

## 技术栈

后端：

- Java 17 或 Java 21
- Spring Boot 3.x
- MySQL 8.x
- MyBatis Plus
- Redis
- Sa-Token
- BCrypt
- Spring Scheduler 或 Quartz
- WebClient 或 OkHttp
- Maven
- Knife4j / Swagger OpenAPI
- Lombok
- MapStruct 可选
- Hutool 可选

前端：

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Axios
- Element Plus
- ECharts
- 响应式 CSS

## 架构文档

总体设计见 [docs/01-project-overall-design.md](docs/01-project-overall-design.md)。

后端模块设计见 [quant-fund-server/README.md](quant-fund-server/README.md)。

前端模块设计见 [quant-fund-web/README.md](quant-fund-web/README.md)。

本地前后端联调见 [docs/deploy/local-integration.md](docs/deploy/local-integration.md)。

## 分批计划

1. 项目总体设计
2. 数据库和后端基础工程
3. 用户登录、注册与鉴权模块
4. 企业级注解与 AOP 横切能力
5. 基金数据源模块
6. 持仓、账户、交易模块
7. 策略引擎模块
8. AI 分析模块
9. 定时任务模块
10. 前端基础工程
11. PC 端核心页面
12. 响应式和手机端兼容
13. 测试数据、联调和文档

## 本地启动说明

第二批已生成后端基础工程。需要先准备 MySQL 8.x 和 Redis。

推荐使用项目自带 Compose 依赖服务：

```bash
docker compose up -d mysql redis
cd quant-fund-server
copy .env.docker.example .env
```

初始化数据库：

```bash
mysql -uroot -p < docs/sql/001_schema.sql
```

启动后端：

```bash
cd quant-fund-server
mvn spring-boot:run
```

如果当前机器的用户级 Maven 仓库不可写，可使用项目内本地仓库运行验证：

```bash
cd quant-fund-server
mvn "-Dmaven.repo.local=.m2/repository" test
```

后端环境变量示例见 [quant-fund-server/.env.example](quant-fund-server/.env.example)，不要把真实密钥写入代码或提交到仓库。

认证接口文档见 [docs/api/auth.md](docs/api/auth.md)。

AOP 横切能力说明见 [docs/api/aop.md](docs/api/aop.md)。

基金数据源模块说明见 [docs/api/fund-datasource.md](docs/api/fund-datasource.md)。
账户、持仓和模拟交易接口说明见 [docs/api/portfolio-trade.md](docs/api/portfolio-trade.md)。
策略引擎接口说明见 [docs/api/strategy.md](docs/api/strategy.md)。
AI 分析接口说明见 [docs/api/ai-analysis.md](docs/api/ai-analysis.md)。
定时任务模块说明见 [docs/api/scheduler.md](docs/api/scheduler.md)。
系统配置与日志接口说明见 [docs/api/system-management.md](docs/api/system-management.md)。
基金量化驾驶舱接口说明见 [docs/api/dashboard.md](docs/api/dashboard.md)。
盈亏分析与盈亏日历接口说明见 [docs/api/analytics.md](docs/api/analytics.md)。

健康检查：

```text
GET http://localhost:8080/api/health
```

DeepSeek 和外部基金数据源已经按可配置适配器接入。基金数据默认走东方财富等真实数据源，mock fallback 默认关闭；只有开发演示或外部接口故障演练时才显式开启。AI 分析默认走真实 DeepSeek 配置，未配置 Key 或调用失败时返回明确的保守兜底结果，不再把 mock 当作真实分析。
