# WSL Docker 一键启动

该方案只容器化三个应用服务：

- `quant-fund-web`
- `quant-fund-server`
- `quant-engine`

MySQL 和 Redis 继续使用 Windows 中现有的实例。Compose 不会启动数据库容器、执行初始化 SQL，也不会挂载或修改 Windows 数据目录。

## 前置条件

1. Docker Desktop 已启动。
2. Docker Desktop 已为 Ubuntu 开启 WSL Integration。
3. Windows MySQL 正在监听 `3306`，Redis 正在监听 `6379`。
4. `quant-fund-server/.env` 中保留现有数据库用户名、密码和其他私密配置。

基础镜像从 AWS Public ECR 的 Docker Official Images 同步入口拉取，以适配当前无法访问 `auth.docker.io` 的网络环境。Python 镜像内的 Debian 软件包使用清华大学 TUNA 镜像源。

容器通过 Docker Desktop 提供的 `host.docker.internal` 访问 Windows 服务。服务间通过 Compose DNS 名称通信：前端访问 `quant-fund-server:8080`，Java 后端访问 `quant-engine:8091`。

## 启动

在 Ubuntu WSL 中执行：

```bash
cd /mnt/d/code/personal/quant-fund
docker compose up -d --build
```

查看状态和日志：

```bash
docker compose ps
docker compose logs -f
```

启动完成后访问：

- Web：<http://127.0.0.1:5173>
- 后端健康检查：<http://127.0.0.1:8080/api/health>
- 后端依赖检查：<http://127.0.0.1:8080/api/health/dependencies>
- Python 引擎：<http://127.0.0.1:8091/api/v1/health>

## 停止

```bash
docker compose down
```

该命令只停止并删除三个应用容器和 Compose 网络，不会删除 Windows MySQL/Redis 数据。不要使用 `docker compose down -v`，否则会删除 Python 缓存卷；即使如此，也不会影响 Windows 数据库中的账号和业务数据。

## 可选端口

默认端口可在启动命令前覆盖：

```bash
WEB_PORT=5173 SERVER_PORT=8080 QUANT_ENGINE_PORT=8091 \
WINDOWS_MYSQL_PORT=3306 WINDOWS_REDIS_PORT=6379 \
docker compose up -d --build
```

如果数据库名不是 `quant_fund`，增加 `WINDOWS_MYSQL_DATABASE`：

```bash
WINDOWS_MYSQL_DATABASE=quant_fund docker compose up -d --build
```

## 排障

如果后端依赖检查显示 MySQL 或 Redis 为 `DOWN`：

1. 确认 Windows 服务仍在运行。
2. 确认 Windows 防火墙允许 Docker Desktop 后端访问对应端口。
3. 确认 MySQL 账号允许来自 Docker Desktop 的连接，而不只是 `localhost`。
4. 检查 `quant-fund-server/.env` 中的用户名和密码。

`quant-fund-server/.env` 通过 Compose 在运行时注入，并被 `.dockerignore` 排除，不会写入镜像。
