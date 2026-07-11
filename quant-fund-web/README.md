# QuantFund Web

`quant-fund-web` 是 QuantFund 的 Vue 3 单页应用，采用深色 Trading Terminal 风格，负责登录态、业务页面、数据可视化和用户操作。界面以桌面端为主，同时适配平板和手机。

![QuantFund Dashboard](qa-artifacts/readme-dashboard.png)

## 技术栈

- Vue 3 Composition API、TypeScript
- Vite、Vue Router、Pinia、Pinia persisted state
- Axios、Element Plus、ECharts
- Vitest、Playwright

## 页面与路由

| 路由 | 页面 | 主要能力 |
| --- | --- | --- |
| `/login`、`/register` | 登录与注册 | 用户认证、登录态初始化 |
| `/dashboard` | 首页总览 | 资产、市场状态、持仓、收益、风险和建议 |
| `/holdings` | 持仓列表 | 估值、收益、清仓、重算和净值同步 |
| `/holding-edit` | 持仓编辑 | 账户、基金搜索、持仓和模拟交易录入 |
| `/fund-detail` | 基金详情 | 净值、估值、重仓股、主题、排名和量化分析 |
| `/fund-screener` | 基金优选 | 因子排名、评分解释、全量刷新和验证闭环 |
| `/ai-analysis` | 智能分析 | 单持仓/账户 AI 报告和历史记录 |
| `/profit-analysis` | 收益分析 | 区间、盘中、指数对比和基金排行 |
| `/profit-calendar` | 收益日历 | 月度收益和每日热力图 |
| `/backtest-validation` | 回测验证 | 净值缓存、规则回测、ML 样本与 A/B 对比 |
| `/trades` | 交易流水 | 模拟交易、定投计划、在途记录和结算 |
| `/strategy-config` | 策略配置 | Java 策略参数和个人风险偏好 |
| `/system-config` | 系统配置 | 数据源、运行状态和日志 |
| `/profile` | 个人中心 | 资料和密码维护 |

所有业务页面都使用 `meta.requiresAuth`。未登录访问时跳转 `/login`，已登录用户访问登录/注册页时跳转驾驶舱。

## 本地启动

先启动 Spring Boot 后端；完整流程见[本地开发与联调](../docs/deploy/local-integration.md)。

```powershell
Copy-Item .env.example .env
npm install
npm run dev
```

默认地址：<http://127.0.0.1:5173>。

Vite 将 `/api` 代理到 `http://127.0.0.1:8080`：

```env
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api
```

## 真实接口与 Mock

- `VITE_USE_MOCK=false`：默认模式，使用 Spring Boot、真实基金数据源和登录态。
- `VITE_USE_MOCK=true`：前端演示模式，主要页面读取 `src/api/mock.ts`；回测等部分功能仍要求真实后端。
- `src/api/http.ts` 统一处理请求、响应解包、错误消息和 401 跳转。
- `src/api/auth.ts`、`src/api/quant.ts` 分别封装认证和业务 API。

Axios 会把登录返回的 token 写入 `Authorization` 头。HTTP 401 或业务码 401 都会清理本地登录态并跳转登录页。

## 目录结构

```text
src/
├─ api/          # Axios、认证 API、业务 API 和 Mock
├─ components/   # 图表、指标卡、状态和风险提示组件
├─ layouts/      # 桌面/移动导航布局
├─ router/       # 路由与登录守卫
├─ stores/       # 登录态和驾驶舱状态
├─ styles/       # 全局主题与响应式样式
├─ types/        # 领域模型与 API 类型
├─ utils/        # 格式化、交易和验证工具
└─ views/        # 页面组件
```

## 交互和安全约束

- API 请求统一封装，不在页面散落服务地址。
- 页面统一处理 loading、空数据和错误状态。
- 图表通过通用 `BaseChart` 组件封装。
- 所有建议页显示“仅供参考，不构成投资建议，不承诺收益”。
- 交易相关页面显示“仅为模拟操作，并非真实交易”。
- 前端只负责交互，用户数据归属和资源权限由后端强制校验。

## 脚本

```powershell
npm run dev             # 开发服务器
npm run typecheck       # TypeScript / Vue 类型检查
npm run test -- --run   # Vitest 单次运行
npm run build           # 类型检查并构建 dist
npm run preview         # 预览生产构建
```

## 视觉 QA

`qa-artifacts/visual-qa` 保存桌面、平板和手机页面截图，`scripts/visual-qa.mjs` 用于自动捕获页面。历史截图是特定版本的视觉证据，不替代当前功能测试。

如果 Windows 环境执行 `npm run build` 时在 Vite/esbuild 子进程阶段出现 `spawn EPERM`，先运行 `npm run typecheck` 排除类型问题，再在允许启动子进程的终端执行构建。

## 延伸阅读

- [项目 README](../README.md)
- [文档中心](../docs/README.md)
- [后端模块](../quant-fund-server/README.md)
