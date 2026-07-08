# Fund Screener Batch 1 Design

## Scope

Batch 1 introduces the fund screener module skeleton without changing existing trading, holding, strategy signal, analytics, AI analysis, or fund detail flows.

This batch delivers:
- Independent `screener_*` database tables.
- Backend entity, mapper, service, controller, DTO, and VO skeletons.
- A `/api/fund-screener` controller surface with paged rank and task result placeholders.
- A `/fund-screener` frontend route and usable static workbench page.
- Sidebar menu replacement from strategy config to fund screener while keeping `/strategy-config` and `StrategyConfigView.vue`.

## Architecture

The module is isolated under new `Screener*` database tables and new `FundScreener*` service contracts. Existing `FundQueryService`, `fund_info`, and `fund_nav_daily` remain untouched in this batch.

The first controller implementation returns safe empty results until later batches add universe sync, NAV sync, factor calculation, and scoring. This keeps the frontend route usable without pretending a score model is ready.

## Data Flow

Frontend `/fund-screener` calls `quantApi.fundScreenerRank()` and renders mock or backend data. Backend rank reads through `FundQualityScoreService`, which is a placeholder in batch 1 and returns an empty page.

Manual task endpoints return structured `FundScreenerTaskResultVO` placeholders so future batches can plug in real sync and score tasks without changing the frontend API shape.

## Error Handling

Batch 1 avoids external data source calls and long-running work. Later batches must preserve existing screener data on external failures and should summarize per-batch errors in task results.

## Testing

Backend TDD starts with a controller test that expects the new controller to expose paged ranking and refresh task behavior. Frontend verification uses the TypeScript build.

