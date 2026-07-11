# QuantFund Fund Datasource Module

基金数据源模块为基金详情、持仓、市场读数、回测净值缓存和基金优选提供统一的外部数据入口。

## Capabilities

- 基金搜索：`GET /api/funds/search?keyword=白酒&mode=FUZZY`
  - `mode=FUZZY`：模糊搜索，默认值，调用东方财富基金搜索源返回候选基金。
  - `mode=EXACT`：精确搜索，在真实数据源返回结果中按基金代码、基金全名或拼音精确匹配。
  - 东方财富搜索响应会优先读取 `CODE`、`NAME`、`JP`、`FundBaseInfo.FTYPE`，用于返回基金代码、名称、拼音和细分基金类型。
- 基金基础信息：`GET /api/funds/{fundCode}`
- 历史净值：`GET /api/funds/{fundCode}/nav`
- 当天估值：`GET /api/funds/{fundCode}/estimate`
- 手动刷新估值：`POST /api/funds/{fundCode}/refresh-estimate`
- 重仓股票：`GET /api/funds/{fundCode}/heavy-stocks`
- 关联主题：`GET /api/funds/{fundCode}/themes`
- 同类排名：`GET /api/funds/{fundCode}/peer-rank`

All endpoints are under `/api/**`, so Sa-Token login interception applies.

## Datasource Design

The module is adapter-based:

- `FundDataSourceAdapter`: unified datasource interface.
- `EastMoneyFundDataSourceAdapter`: configurable EastMoney/Tiantian Fund compatible public endpoint adapter.
- `MockFundDataSourceAdapter`: optional local fallback adapter for development and external API outage.

The service tries enabled adapters by priority. If a higher-priority adapter fails or returns no data, the next enabled adapter is tried.

## Cache, Rate Limit, and Fallback

- Search results are cached for 10 minutes.
- Fund basic information is cached for 6 hours.
- Historical NAV is cached for 12 hours.
- Intraday estimates are cached for 5 minutes.
- Manual estimate refresh uses Redis cooldown, default 10 seconds per fund.
- If an estimate refresh fails and a previous estimate cache exists, the cached result is returned with `delayed = true`.
- External API failures are recorded in `api_call_log`.
- The EastMoney public search endpoint was checked on 2026-06-24 with both `161725` and URL-encoded `白酒`; both returned HTTP 200 and fund candidates.

## Configuration

```yaml
quantfund:
  fund-data-source:
    timeout-ms: 5000
    refresh-interval-seconds: 120
    manual-refresh-cooldown-seconds: 10
    mock-fallback-enabled: false
    east-money-enabled: true
    east-money-search-url: https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx
    east-money-estimate-url: https://fundgz.1234567.com.cn/js/{fundCode}.js
    east-money-historical-nav-url: https://api.fund.eastmoney.com/f10/lsjz
```

## Notes

- Intraday estimates are reference data only. The final NAV is the official evening NAV.
- External public endpoints may change, rate limit, or become unavailable.
- Production and default local integration use real fund datasource adapters first. Mock fallback is disabled by default and should only be explicitly enabled for development demos or outage drills.
