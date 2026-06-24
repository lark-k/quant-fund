# QuantFund AOP Cross-Cutting Capabilities

第四批实现以下横切能力：

- `@RequireLogin`：登录校验。
- `@DataScope`：用户数据归属校验。
- `@OperationLog`：操作日志。
- `@RateLimit`：接口限流。
- `@RepeatSubmit`：防重复提交。

## 使用示例

```java
@RequireLogin
@OperationLog(module = "holding", action = "delete", bizType = "FUND_HOLDING")
@DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "id", operation = DataOperation.DELETE)
@RepeatSubmit(intervalSeconds = 3)
@DeleteMapping("/{id}")
public ApiResponse<Void> deleteHolding(@PathVariable Long id) {
    holdingService.deleteHolding(id);
    return ApiResponse.success();
}
```

## Redis Keys

All Redis keys are centralized in `RedisKeyConstants`.

Examples:

- `quantfund:rate_limit:{scope}:{keyHash}`
- `quantfund:repeat_submit:{userId}:{requestHash}`
- `quantfund:estimate_cache:{fundCode}`
- `quantfund:ai_analysis_lock:{userId}:{fundCode}`

## Data Scope Extension

`DataScopeAspect` delegates resource ownership lookups to `ResourceOwnerService`.

Business modules should provide one `ResourceOwnerLookup` implementation for each supported resource type, for example:

```java
@Component
class FundHoldingOwnerLookup implements ResourceOwnerLookup {
    public ResourceType resourceType() {
        return ResourceType.FUND_HOLDING;
    }

    public Optional<Long> findOwnerUserId(Long resourceId) {
        return fundHoldingMapper.selectOwnerUserId(resourceId);
    }
}
```

This keeps AOP responsible for ownership enforcement only. It does not hide holding, trade, strategy, or AI business logic inside aspects.

## Logging Safety

`OperationLogAspect` masks sensitive request and response fields before persistence.

Masked keys include:

- password
- oldPassword
- newPassword
- passwordHash
- token
- tokenValue
- authorization
- secret
- apiKey

Operation log persistence failure is caught and logged as a warning so it does not affect the main business flow.

