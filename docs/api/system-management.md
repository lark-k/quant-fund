# 系统配置与日志接口

系统配置接口用于前端“系统配置页”，日志接口用于排查数据源、AI、策略和操作问题。所有接口都需要登录。

## 数据源配置

### 查询数据源配置

```http
GET /api/system/data-sources
```

返回当前可见的数据源配置：

- `userId = null` 的全局默认配置。
- 当前用户自己的覆盖配置。
- 如果同名数据源同时存在全局配置和用户覆盖配置，返回用户覆盖配置。

### 新增或保存用户数据源配置

```http
POST /api/system/data-sources
```

请求体：

```json
{
  "sourceName": "EAST_MONEY",
  "baseUrl": "https://api.fund.eastmoney.com",
  "timeoutMs": 5000,
  "refreshIntervalSeconds": 120,
  "rateLimitPerMinute": 60,
  "enabled": true,
  "priority": 100,
  "configJson": "{}"
}
```

同一用户下同名 `sourceName` 会更新为最新配置。

### 更新数据源配置

```http
PUT /api/system/data-sources/{id}
```

如果 `{id}` 指向当前用户自己的配置，则直接更新。

如果 `{id}` 指向全局配置，系统不会修改全局配置，而是为当前用户生成一份覆盖配置。

## 操作日志

```http
GET /api/system/operation-logs?pageNo=1&pageSize=20&module=portfolio&success=true
```

只返回当前登录用户自己的操作日志，避免跨用户泄露请求参数、IP、User-Agent 等信息。

## API 调用日志

```http
GET /api/system/api-call-logs?pageNo=1&pageSize=20&provider=EAST_MONEY&success=false
```

返回当前登录用户相关 API 调用日志，以及没有用户上下文的系统级 API 调用日志。

日志中的 URL、错误信息在写入时已经经过敏感信息脱敏。

## 安全边界

- 普通用户不能修改其他用户的数据源配置。
- 普通用户不能查询其他用户操作日志。
- 数据源配置不保存 API Key、密码、token 等敏感信息。
- 系统配置页展示的 AI 或策略配置仍需标注“仅供参考，不构成投资建议，不承诺收益”。
