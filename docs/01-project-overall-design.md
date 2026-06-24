# QuantFund 项目总体设计

## 1. 设计目标

QuantFund 是一个 PC Web 优先、移动端兼容的基金量化交易辅助工具。系统核心不是后台管理，而是面向个人投资复盘和 15:00 前决策辅助的基金量化驾驶舱。

系统只提供参考建议，不做真实自动交易，不接入真实下单接口，不承诺收益。

核心能力：

- 多用户注册、登录、鉴权和数据隔离
- 账户、持仓、模拟交易记录管理
- 基金基础信息、历史净值、当天估值同步
- 量化策略信号生成
- DeepSeek AI 辅助分析
- 收益复盘和风险提示
- PC 端完整工作台体验，移动端完成核心操作

## 2. 总体架构

```text
用户浏览器
  |
  | Vue 3 + Vite + TypeScript + Element Plus + ECharts
  v
quant-fund-web
  |
  | Axios, token, RESTful API
  v
quant-fund-server
  |
  | Controller
  | Service
  | Strategy Engine
  | Fund Datasource Adapter
  | AI Analysis
  | Scheduler
  | AOP
  v
MySQL 8.x + Redis
  |
  | 外部只读数据源
  v
天天基金 / 东方财富公开数据源 / 备用 mock 数据源

DeepSeek API 作为可配置 AI 能力接入，默认允许关闭或使用 mock。
```

## 3. 后端架构

后端采用清晰分层结构，Controller 不承载复杂业务逻辑，核心业务由 Service、Strategy、Datasource、AI、Scheduler 等模块协作完成。

建议包结构：

```text
com.lk.quantfund
  annotation
  ai
  aspect
  auth
  common
  config
  constants
  controller
  datasource
  datasource.impl
  dto
  entity
  enums
  exception
  mapper
  scheduler
  service
  service.impl
  strategy
  strategy.context
  strategy.impl
  util
  vo
```

后端边界：

- Controller：参数接收、参数校验、统一响应。
- Service：业务编排、事务控制、数据归属校验入口。
- Mapper：数据库访问，不写业务决策。
- Strategy：量化策略计算和信号解释。
- Datasource：外部基金数据源适配、缓存、限流、降级。
- AI：DeepSeek 请求、JSON 解析、字段校验、失败兜底。
- Scheduler：交易日定时任务、净值同步、快照、复盘。
- AOP：登录校验、数据范围、操作日志、限流、防重复提交。
- Common / Exception：统一响应、错误码、全局异常处理。

## 4. 前端架构

前端 PC 端优先，使用 Vue 3 Composition API 和 TypeScript，页面、组件、API、状态管理分层清晰。

建议目录结构：

```text
src
  api
  assets
  components
    charts
    common
  layouts
  router
  stores
  styles
  types
  utils
  views
    analysis
    auth
    dashboard
    fund
    portfolio
    profile
    trade
```

前端边界：

- `api`：统一封装 Axios 请求，不在页面中散落 URL。
- `stores`：登录态、用户信息、系统配置、页面缓存状态。
- `router`：路由定义和 `meta.requiresAuth` 守卫。
- `components/charts`：收益走势、回撤走势、仓位分布等 ECharts 组件。
- `components/common`：空状态、加载状态、错误状态、业务提示。
- `layouts`：PC 侧边导航、顶部用户菜单、移动端底部导航。
- `views`：页面级业务编排，不写成大而全单文件组件。
- `styles`：全局变量、响应式断点、Element Plus 覆盖样式。

## 5. 核心数据流

### 5.1 登录数据流

```text
用户提交登录表单
  -> 前端校验
  -> POST /api/auth/login
  -> Sa-Token 校验账号密码
  -> BCrypt 验证密码
  -> 返回 token 和用户摘要
  -> Pinia 保存登录态
  -> Axios 后续请求自动携带 token
```

未登录或 token 过期：

```text
业务 API 返回 401
  -> Axios 响应拦截器清理登录态
  -> 跳转登录页
```

### 5.2 持仓估值刷新流

```text
手动刷新或定时任务触发
  -> 查询当前用户持仓基金
  -> Datasource 选择可用数据源
  -> Redis 检查 10 秒重复刷新限制
  -> 外部 API 获取当天估值
  -> 失败时读取最近缓存
  -> 写入 fund_estimate_intraday
  -> 记录 api_call_log
  -> 前端显示估值状态
```

当天估值仅作参考，最终净值以晚间正式净值为准。

### 5.3 策略信号生成流

```text
用户触发单基金分析 / 系统定时触发
  -> 聚合基金、持仓、账户、历史净值、估值数据
  -> 计算收益率、回撤、波动率、仓位占比
  -> 识别基金分类
  -> Strategy Engine 执行匹配策略
  -> 生成可解释信号
  -> 保存 strategy_signal
  -> 前端展示信号和触发原因
```

策略只生成参考信号，不承诺收益。

### 5.4 AI 分析流

```text
用户点击生成 AI 分析 / 定时任务触发
  -> 后端先计算量化指标
  -> 组织结构化 JSON 输入
  -> 调用 DeepSeek 或 mock AI
  -> 要求 AI 返回固定 JSON
  -> 后端解析、字段校验
  -> 非法返回降级 WATCH
  -> 保存 ai_analysis_report
  -> 前端展示建议、理由、风险提示
```

AI 不允许自由编造数据。数据不足时必须返回 WATCH。

## 6. 核心业务流程

### 6.1 用户首次使用

1. 注册账号。
2. 登录系统。
3. 创建账户，例如支付宝、天天基金、券商或手动账户。
4. 添加持仓基金。
5. 同步或手动录入交易记录。
6. 刷新当天估值。
7. 查看策略信号和 AI 建议。

### 6.2 每日 15:00 前决策

1. 系统在交易时间自动刷新持仓估值。
2. 用户打开基金量化驾驶舱。
3. 查看总资产、当日收益、仓位分布、风险预警。
4. 对重点基金生成 AI 分析。
5. 查看 BUY / SELL / HOLD / CONVERT / WATCH 建议。
6. 用户自行前往原交易平台操作。
7. 回到系统记录模拟交易。

### 6.3 晚间净值同步和复盘

1. 20:00 后同步正式净值。
2. 更新持仓收益和收益率。
3. 23:00 生成账户持仓快照。
4. 每周生成复盘报告。
5. 前端盈亏分析页展示收益走势、指数对比和日历热力图。

## 7. 模块边界

| 模块 | 负责 | 不负责 |
| --- | --- | --- |
| Auth | 注册、登录、退出、token、当前用户 | 基金业务计算 |
| User Data Scope | 用户级数据归属校验 | 前端传入 user_id 决定归属 |
| Portfolio | 账户、持仓、持仓收益 | 外部基金 API 细节 |
| Trade | 加仓、减仓、定投、转换模拟记录 | 真实下单 |
| Fund Datasource | 基金搜索、净值、估值、缓存、降级 | 持仓决策 |
| Strategy | 策略参数、信号生成、解释 | AI 自由判断 |
| AI Analysis | 结构化 AI 请求、JSON 校验、报告保存 | 直接替代策略引擎 |
| Risk | 仓位、回撤、连续亏损、数据延迟提示 | 承诺收益 |
| Scheduler | 定时刷新、同步、快照、复盘 | 页面展示 |
| Frontend | 用户工作台、页面状态、交互 | 业务安全边界 |

## 8. 数据库总体设计

第二批将生成完整 MySQL SQL。本批先确定表清单：

- `user_account`
- `portfolio_account`
- `fund_info`
- `fund_nav_daily`
- `fund_estimate_intraday`
- `fund_holding`
- `holding_snapshot`
- `trade_record`
- `investment_plan`
- `strategy_config`
- `strategy_signal`
- `ai_analysis_report`
- `api_call_log`
- `operation_log`
- `data_source_config`
- `risk_profile`

所有表包含：

- `id`
- `create_time`
- `update_time`
- `deleted`

所有用户级业务表包含：

- `user_id`
- user_id 相关索引

金额字段使用 `decimal`，不使用 `float` 或 `double`。

时间字段统一使用 `datetime`，精确时间使用 `datetime(3)`。

## 9. API 总体设计

API 统一以 `/api` 开头，返回统一响应结构。

主要分组：

- `/api/auth/**`
- `/api/accounts/**`
- `/api/funds/**`
- `/api/holdings/**`
- `/api/trades/**`
- `/api/strategies/**`
- `/api/ai-analysis/**`
- `/api/system/**`
- `/api/admin/**`

后端业务接口默认要求登录。未登录返回 401，数据不归属返回 403，资源不存在返回 404。

## 10. 风险与安全约束

- 不生成真实自动交易功能。
- 不接入真实下单接口。
- 不承诺收益。
- 所有买卖建议必须加风险提示。
- AI 建议不能作为绝对指令。
- 账号密码使用 BCrypt。
- API Key、密码、token 不写死到代码。
- 日志必须脱敏。
- 用户级业务数据必须基于登录上下文过滤。
- 前端传入的 `user_id` 不作为数据归属依据。

## 11. 后续批次衔接

第二批建议生成：

- MySQL 建表 SQL
- Spring Boot 项目骨架
- 通用响应结构
- 全局异常处理
- 基础枚举
- 基础配置
- Swagger / Knife4j 配置
- MyBatis Plus 配置

第三批再实现登录、注册、鉴权和前端登录注册流程。

