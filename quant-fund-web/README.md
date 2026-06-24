# quant-fund-web

QuantFund 前端模块，系统标题统一为 **QuantFund - AI Fund Quant Dashboard**。

本批已生成 Vue 3 + Vite + TypeScript 前端工程，并按 Product Design 选定的第 2 个视觉方向 **Trading Terminal** 实现深色专业基金量化驾驶舱。

## 技术栈

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Axios
- Element Plus
- ECharts
- 响应式 CSS

## 启动

```bash
npm install
npm run dev
```

默认使用后端真实接口：

```env
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api
```

请求会走 Vite 代理到 `http://127.0.0.1:8080/api`，由后端接入东方财富等真实基金数据源。只有开发演示或后端不可用时，才显式设置 `VITE_USE_MOCK=true` 使用前端 mock。

完整本地联调流程见 `../docs/deploy/local-integration.md`。

## 已生成目录结构

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

## 页面规划

PC 端核心页面已生成：

- 基金量化驾驶舱
- 基金详情页
- AI 量化分析页
- 盈亏分析页
- 盈亏日历页
- 持仓编辑页
- 交易记录页
- 系统配置页
- 登录页
- 注册页
- 个人资料页
- 修改密码页

移动端核心能力：

- 查看总资产和当日收益
- 查看持仓列表
- 查看基金详情
- 查看 AI 今日建议
- 手动刷新估值
- 添加交易记录
- 同步加仓 / 减仓 / 定投 / 转换

## 已实现交互

- 路由守卫：业务页需要登录，已登录访问登录页会跳转驾驶舱。
- Axios 拦截器：自动携带 token，遇到 401 清理登录态并跳转登录页。
- Mock API：仅作为开发演示兜底；最终交付默认使用后端真实接口和真实基金数据源。
- 图表：收益走势、仓位分布、盈亏日历柱状图。
- 状态：loading、空状态、提示消息、模拟交易弹窗。
- 响应式：桌面优先，移动端可折叠导航和双列日历。

## 前端工程约束

- 使用 Vue 3 Composition API。
- 页面、组件、API、状态管理分层清晰。
- API 请求必须统一封装。
- 401 自动清理登录态并跳转登录页。
- 路由使用 `meta.requiresAuth`。
- 登录后访问登录页自动跳转基金量化驾驶舱。
- 图表组件可复用。
- 表格、卡片、筛选器、弹窗模块化。
- 统一 loading、空数据、错误状态。
- 不写大而全单文件组件。
- PC 端优先，响应式兼容移动端。
- 买卖相关页面必须展示“仅为模拟操作，并非真实交易”。

## 风险提示

所有买卖建议均展示：

> 仅供参考，不构成投资建议，不承诺收益

所有交易相关页面均展示：

> 仅为模拟操作，并非真实交易
