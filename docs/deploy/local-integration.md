# QuantFund 本地开发与联调

本文从空环境启动完整 QuantFund：MySQL、Redis、Python 量化引擎、Spring Boot 后端和 Vue 前端。命令默认从仓库根目录执行，并以 Windows PowerShell 为例。

## 1. 环境要求

| 工具 | 要求/用途 |
| --- | --- |
| JDK | 21 |
| Maven | 3.9+ |
| Python | 3.11+ |
| Node.js / npm | 前端和 smoke 脚本 |
| Docker Compose | 推荐用于 MySQL 8.4 和 Redis 7.4 |

```powershell
java -version
mvn -version
python --version
node --version
docker compose version
```

## 2. 初始化数据库结构

### 2.1 启动 Compose 依赖

```powershell
docker compose up -d mysql redis
docker compose ps
```

| 服务 | 本机地址 | 凭据 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3307/quant_fund` | `quantfund_app` / `quantfund_app_password` |
| MySQL root | `127.0.0.1:3307` | `root` / `quantfund_root_password` |
| Redis | `127.0.0.1:6380` | 无密码，DB 0 |

等待两个容器变为 healthy 后再执行后续步骤。

### 2.2 执行 SQL

Compose 仅在**首次创建空 MySQL 数据卷**时自动执行 `001_schema.sql`。当前代码还依赖 `003`–`010`，新环境必须按编号执行一次：

```powershell
$schemaScripts = Get-ChildItem .\docs\sql\*.sql |
  Where-Object Name -NotIn @('001_schema.sql', '002_seed_demo.sql') |
  Sort-Object Name

foreach ($script in $schemaScripts) {
  Write-Host "Applying $($script.Name)"
  Get-Content -Raw -Encoding UTF8 $script.FullName |
    docker compose exec -T mysql mysql -uroot -pquantfund_root_password quant_fund
}
```

> `003`–`010` 是编号增量脚本，不是完整的幂等迁移框架，部分脚本不可重复运行。

需要本地演示数据时再执行：

```powershell
Get-Content -Raw -Encoding UTF8 .\docs\sql\002_seed_demo.sql |
  docker compose exec -T mysql mysql -uroot -pquantfund_root_password quant_fund
```

演示账号：

```text
username: quantdemo
password: QuantFund2026
```

`002_seed_demo.sql` 可重复执行，只重置 `quantdemo` 用户的演示业务数据。

### 2.3 使用已有 MySQL / Redis

不使用 Compose 时，在 MySQL 8.x 中按 `001`、`003`–`010` 的顺序执行脚本，再按需执行 `002`。将连接信息写入 `quant-fund-server/.env`：

```properties
QUANTFUND_DB_URL=jdbc:mysql://localhost:3306/quant_fund?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
QUANTFUND_DB_USERNAME=root
QUANTFUND_DB_PASSWORD=your_password
QUANTFUND_REDIS_HOST=localhost
QUANTFUND_REDIS_PORT=6379
```

## 3. 启动 Quant Engine

```powershell
cd .\quant-engine
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8091 --reload
```

不创建 `.env` 时会使用 `app/core/config.py` 的本地默认值。仓库 `quant-engine/.env.example` 中的早期模型版本值需要在复制后改为当前 `rule-v1.36.0`。

检查：

```powershell
curl.exe http://127.0.0.1:8091/api/v1/health
```

预期 `status` 为 `UP`，模型版本为 `rule-v1.36.0`。LightGBM 默认关闭，不影响普通量化和回测功能。

## 4. 启动 Server

打开新的 PowerShell，回到仓库根目录：

```powershell
cd .\quant-fund-server
Copy-Item .env.docker.example .env
mvn spring-boot:run
```

如果用户 Maven 仓库不可写：

```powershell
mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run
```

检查：

```powershell
curl.exe http://127.0.0.1:8080/api/health
curl.exe http://127.0.0.1:8080/api/health/dependencies
```

依赖结果中的 `database.status` 和 `redis.status` 应为 `UP`。

接口文档：

- Knife4j：<http://127.0.0.1:8080/doc.html>
- Swagger UI：<http://127.0.0.1:8080/swagger-ui.html>
- OpenAPI JSON：<http://127.0.0.1:8080/v3/api-docs>

## 5. 启动 Web

打开新的 PowerShell，回到仓库根目录：

```powershell
cd .\quant-fund-web
Copy-Item .env.example .env
npm install
npm run dev
```

打开 <http://127.0.0.1:5173>。默认配置：

```env
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api
```

Vite 会把 `/api` 代理到 `http://127.0.0.1:8080`。只有前端演示或后端不可用时才设置 `VITE_USE_MOCK=true`；回测等部分功能仍需要真实后端。

## 6. DeepSeek 配置

`.env.docker.example` 默认使用本地 Mock AI，方便没有密钥时启动。使用真实 DeepSeek 时修改 `quant-fund-server/.env`：

```properties
DEEPSEEK_ENABLED=true
DEEPSEEK_MOCK_ENABLED=false
DEEPSEEK_API_KEY=your_deepseek_api_key
DEEPSEEK_MODEL=deepseek-v4-flash
```

真实 Key 只能放在 `.env` 或系统环境变量中。缺少 Key、超时或返回格式不合法时，后端返回带 `fallbackUsed=true` 的保守 `WATCH`，不会把 Mock 内容伪装成真实分析。

## 7. 联调检查

### 7.1 基础闭环

- 注册/登录后能获取 token 和用户信息；
- 后续请求自动携带 `Authorization`；
- 创建账户、搜索基金、加入持仓并回读；
- `GET /api/quant/health` 能访问 8091 量化引擎；
- 基金详情能获取真实净值、估值或明确的外部数据错误；
- 模拟交易完成后持仓和账户汇总同步变化；
- 回测页能够先刷新本地净值再运行批量回测。

### 7.2 真实数据 smoke

在仓库根目录运行：

```powershell
node .\tools\real-fund-detail-smoke.mjs
node .\tools\real-fund-holding-smoke.mjs
```

指定后端和搜索词：

```powershell
node .\tools\real-fund-holding-smoke.mjs `
  --base-url=http://127.0.0.1:8080 `
  --exact-keyword=161725 `
  --fuzzy-keyword=白酒
```

脚本创建临时 smoke 用户，不依赖前端 Mock，也不需要真实密钥。

## 8. 常见问题

### 后端提示表或字段不存在

确认 `003`–`010` 已按编号执行一次。基金优选依赖 `008`–`010`，量化信号和回测依赖 `006`。

### 修改 SQL 后重启 MySQL 未生效

`/docker-entrypoint-initdb.d` 只在空数据卷初始化时执行。已有环境应手动执行新增脚本。仅在确认可以丢弃本地数据时运行 `docker compose down -v`。

### 注册或登录返回 500

先查看 `/api/health/dependencies`，确认 `.env` 中的数据库用户名、密码、端口与实际环境一致。

### 量化分析或回测失败

确认 `http://127.0.0.1:8091/api/v1/health` 可访问，并检查后端 `QUANT_ENGINE_BASE_URL`。默认 `QUANT_ENGINE_FALLBACK_TO_JAVA_RULES=false`。

### 基金数据为空

真实模式依赖东方财富公开接口和当前网络。查看系统配置页的数据源健康状态、`api_call_log` 和后端日志。基金 Mock fallback 默认关闭。

### 前端 401

重新登录并检查本地 token。前端遇到 HTTP 401 或业务码 401 会自动清理登录态并跳转登录页。

### `npm run build` 出现 `spawn EPERM`

先执行 `npm run typecheck` 排除类型错误，再在允许 Vite/esbuild 创建子进程的终端运行构建。

## 9. 停止与清理

停止依赖但保留数据：

```powershell
docker compose down
```

删除本地 MySQL/Redis 数据卷：

```powershell
docker compose down -v
```

> `down -v` 会永久删除 Compose 中的本地数据库和缓存数据。
