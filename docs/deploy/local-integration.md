# QuantFund 本地联调说明

本文档用于把 `quant-fund-server` 和 `quant-fund-web` 从 mock 浏览切换到本地真实接口联调。

## 1. 准备 MySQL 和 Redis

- MySQL 8.x：创建数据库 `quant_fund`。
- Redis：本地默认 `localhost:6379`。

### 方式 A：使用 Docker Compose 依赖服务

如果本机已有 MySQL / Redis 或不想使用本机 root 账号，可以直接启动项目专用依赖：

```bash
docker compose up -d mysql redis
```

Compose 会启动：

| 服务 | 容器 | 本机端口 | 说明 |
| --- | --- | --- | --- |
| MySQL 8.x | `quantfund-mysql` | `3307` | 自动初始化 `docs/sql/001_schema.sql` |
| Redis 7.x | `quantfund-redis` | `6380` | 开启 AOF 持久化 |

后端联调配置：

```bash
cd quant-fund-server
copy .env.docker.example .env
```

如果使用 PowerShell，也可以执行：

```powershell
Copy-Item .env.docker.example .env
```

### 方式 B：使用本机已有 MySQL / Redis

初始化数据库：

```bash
mysql -uroot -p < docs/sql/001_schema.sql
```

如需导入本地联调用的演示组合数据，可继续执行：

```bash
mysql -uroot -p < docs/sql/002_seed_demo.sql
```

演示账号：

```text
username: quantdemo
password: QuantFund2026
```

`002_seed_demo.sql` 可重复执行。脚本只会重置 `quantdemo` 演示用户下的账户、持仓、模拟交易、策略信号、AI 报告和相关配置，不会写入任何本机数据库密码。

后端环境变量示例见 `quant-fund-server/.env.example`。可以复制为 `quant-fund-server/.env` 并填写本机 MySQL / Redis 配置；`.env` 已被仓库忽略，不要把真实密码、Token、API Key 提交到仓库。

如果需要启用真实 DeepSeek 调用，在 `quant-fund-server/.env` 中设置：

```properties
DEEPSEEK_ENABLED=true
DEEPSEEK_MOCK_ENABLED=false
DEEPSEEK_API_KEY=your_deepseek_api_key
```

`DEEPSEEK_MOCK_ENABLED=false` 表示智能分析优先调用真实 DeepSeek；缺少 Key、调用失败或返回格式异常时会返回带 `fallbackUsed=true` 的保守 `WATCH` 兜底结果，不再伪装为 mock 分析。真实 API Key 只放在 `.env` 或系统环境变量中，不要写入源码、SQL、README 或提交记录。

## 2. 启动后端

```bash
cd quant-fund-server
mvn spring-boot:run
```

如果用户级 Maven 仓库不可写，可使用项目内仓库：

```bash
cd quant-fund-server
mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run
```

健康检查：

```text
GET http://localhost:8080/api/health
```

依赖检查：

```text
GET http://localhost:8080/api/health/dependencies
```

如果注册或登录接口返回 500，优先查看依赖检查中的 `database.status` 和错误信息，确认 `.env` 中的 `QUANTFUND_DB_USERNAME` / `QUANTFUND_DB_PASSWORD` 是否匹配本机 MySQL。

当使用 Compose 依赖时，依赖检查应显示：

```json
{
  "database": { "status": "UP" },
  "redis": { "status": "UP" }
}
```

接口文档：

```text
http://localhost:8080/doc.html
http://localhost:8080/swagger-ui.html
```

## 3. 启动前端

前端默认使用后端真实接口和真实基金数据源。复制或调整 `quant-fund-web/.env.example`：

```env
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api
```

只有在开发演示或后端不可用时，才显式设置 `VITE_USE_MOCK=true` 使用前端 mock 数据。

启动前端：

```bash
cd quant-fund-web
npm install
npm run dev
```

Vite 会把 `/api` 代理到 `http://127.0.0.1:8080`。

## 4. 联调检查点

- 登录、注册接口应返回后端 `ApiResponse.data` 中的 token 和用户信息。
- 前端 Axios 会自动携带 `Authorization` token。
- HTTP 401 或业务码 `401` 都会清理前端登录态并跳转登录页。
- 后端业务接口必须基于 `UserContext` 获取当前用户，不允许由前端传入 `user_id` 决定归属。
- 所有买卖建议继续展示：`仅供参考，不构成投资建议，不承诺收益`。
- 所有交易相关页面继续展示：`仅为模拟操作，并非真实交易`。

### 真实基金搜索入持仓 smoke

后端启动后，可以运行真实数据源联调脚本，验证“东方财富基金搜索 -> 新人账户创建 -> 加入持仓 -> 持仓回读”完整链路：

```bash
node tools/real-fund-holding-smoke.mjs
```

也可以指定后端地址和搜索词：

```bash
node tools/real-fund-holding-smoke.mjs --base-url=http://127.0.0.1:8080 --exact-keyword=161725 --fuzzy-keyword=白酒
```

脚本会创建一个临时 smoke 用户，不依赖前端 mock 数据，不写入真实密钥；如需覆盖用户名、密码、昵称或搜索词，可使用 `QUANTFUND_SMOKE_*` 环境变量或同名命令行参数。

## 5. 当前环境备注

当前机器上 `npm run build` 可能因为 Node 子进程权限返回 `spawn EPERM`，该问题发生在 Vite / esbuild 启动子进程阶段。`npm run typecheck` 可用于先验证 TypeScript 类型正确性。
如果直接运行 `node_modules/@esbuild/win32-x64/esbuild.exe --version` 成功，而普通沙箱内 `npm run build` 失败，可在允许子进程执行的终端中重新运行构建。

停止 Compose 依赖服务：

```bash
docker compose down
```

如果需要清空本地联调数据：

```bash
docker compose down -v
```
