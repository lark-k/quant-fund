# Fund Screener Validation Loop Design

## Goal

Turn fund screening into a verifiable, reviewable, and tunable closed loop that uses cached forward performance to show whether the current ranking system is identifying funds with better future returns.

The feature is decision support only. It must retain the existing investment disclaimer and must not place trades or automatically change production scoring thresholds.

## Current State and Gaps

The repository already contains a V2 score model and an initial backtest implementation. The current implementation:

- calculates 20/60/120-NAV-observation forward returns;
- defines `TOP_5`, `TOP_10`, `WATCH`, `NEUTRAL`, and `AVOID` buckets;
- inserts per-score-date bucket summaries into `screener_backtest_result`;
- exposes a text-only `GET /fund-screener/backtest` task endpoint.

It does not yet form a safe closed loop because it only reads the latest 200 score rows, inserts duplicate results, does not expose stored summaries, does not mark statistical sufficiency, has no scheduled incremental run, and does not provide an effectiveness conclusion or calibration advice in the UI.

## Chosen Approach

Persist one immutable validation unit per `(score_date, horizon_days, bucket_name, model_version)` and aggregate those units for presentation. Manual and scheduled runs share the same incremental service. Existing result keys are skipped, so the normal path does not recompute or duplicate completed work.

This approach is preferred over:

1. Storing only a single rolling aggregate, which loses score-date evidence and makes later review impossible.
2. Persisting every fund-level forward observation, which offers more analytical flexibility but creates unnecessary storage and migration scope for this iteration.

## Architecture

### Backtest execution service

`FundScreenerBacktestService` will expose two operations:

- `runIncremental()` scans historical score dates and persists only eligible, missing bucket/horizon results.
- `getValidation()` reads cached results and returns metadata, aggregated bucket metrics, effectiveness, and calibration advice.

The existing manual endpoint becomes `POST /api/fund-screener/backtest`, because it mutates cached validation state. A separate `GET /api/fund-screener/backtest/validation` endpoint returns the stored validation view.

### Eligibility and incremental behavior

For each historical score date and horizon:

- group all score rows for that date;
- find each fund's base NAV on or before the score date;
- require the requested number of valid NAV observations strictly after the score date;
- include only funds that have both base NAV and the full future window;
- skip the whole `(score_date, horizon)` when no fund is eligible;
- calculate each requested bucket from the eligible observations;
- persist only non-empty buckets;
- skip an existing `(score_date, horizon, bucket, model_version)` result.

There is no arbitrary row limit. Score rows are ordered by score date and quality score, and NAV data is cached per fund during one run.

The database adds a unique key on `(score_date, horizon_days, bucket_name, model_version)`. The service checks before insert, while the unique key provides concurrency protection if manual and scheduled runs overlap.

### Bucket definitions

- `TOP_5`: top `ceil(date population * 5%)`, minimum one fund.
- `TOP_10`: top `ceil(date population * 10%)`, minimum one fund.
- `WATCH`, `NEUTRAL`, `AVOID`: the stored recommendation level on that score date.

`STRONG` is intentionally not a separate validation row because top-percentile buckets validate ranking quality independently of the current production threshold, while `WATCH`, `NEUTRAL`, and `AVOID` validate recommendation-level calibration.

### Metrics

Each cached score-date result stores:

- sample count;
- average forward return;
- positive-return win rate;
- average excess return against all eligible funds on the same score date and horizon;
- worst maximum drawdown observed within the forward window.

The validation query aggregates cached rows using sample-count weighting for average return, win rate, and excess return. The aggregate maximum drawdown is the worst cached drawdown. It also reports the number and range of contributing score dates.

### Statistical sufficiency

A displayed aggregate is statistically sufficient only when both conditions hold:

- total fund observations are at least 30;
- at least 3 distinct score dates contribute.

These thresholds are centralized in strategy configuration so they can be tuned without changing aggregation code. Insufficient rows remain visible and are explicitly labeled `不具备统计意义`; they are excluded from effectiveness decisions.

### Effectiveness conclusion

The primary decision horizon is 60 NAV observations. If its `TOP_10`, `NEUTRAL`, or `AVOID` rows are insufficient, the system reports `INSUFFICIENT` rather than inferring effectiveness from a small sample.

With sufficient 60-day data:

- `EFFECTIVE`: `TOP_10` excess return is positive, `TOP_10` average return is above `NEUTRAL`, and `AVOID` average return is not above `NEUTRAL`.
- `FAILED`: `TOP_10` excess return is negative and `AVOID` average return is at least `TOP_10`.
- `NEUTRAL`: all other cases.

The API returns a Chinese evidence sentence containing the actual 60-day excess return, win rate, and comparisons. The UI displays this sentence without recreating business rules.

### Calibration policy

Production recommendation thresholds remain unchanged automatically. Current values are exposed as configuration-backed policy:

- strong minimum score: 82;
- strong top percentile: 8%;
- watch minimum score: 72;
- watch top percentile: 20%;
- neutral minimum score: 58.

The validation response returns conservative advice:

- insufficient samples: keep thresholds and continue collecting observations;
- effective: keep thresholds and monitor stability;
- neutral: review/tighten WATCH admission before adding score factors;
- failed: do not loosen thresholds; review score weights and recommendation boundaries using the cached date-level evidence.

Advice is informational and never writes threshold configuration or historical scores. This protects current business behavior from short-lived market regimes and small samples.

## API Contract

### Manual incremental run

`POST /api/fund-screener/backtest`

Returns the existing task result envelope. Counts represent newly persisted validation rows, skipped existing rows, and failed units. Re-running with unchanged data creates no duplicates and reports skipped work.

### Validation query

`GET /api/fund-screener/backtest/validation`

Returns:

- latest run date;
- earliest and latest available score date;
- effectiveness status and conclusion;
- current threshold policy and calibration advice;
- aggregated rows for every available bucket/horizon;
- per row: horizon, bucket, average return, win rate, excess return, max drawdown, sample count, score-date count, and statistical-sufficiency flag.

An empty database returns a valid empty response with `INSUFFICIENT`, not an error.

## Automatic Task

Add a screener task at 23:50 Asia/Shanghai on weekdays, after the 23:40 quality-score refresh. It calls the same incremental method as the manual endpoint and uses the existing scheduler enable flags and task logging path.

Task name: `SCREENER_INCREMENTAL_BACKTEST`.

The scheduled adapter preserves success, failure, and skipped counts so operations logs can distinguish new results from already-cached units.

## UI

Add a `策略验证` section to the existing fund screener page without changing ranking or detail behavior.

The section contains:

- latest backtest run date and available score-date range;
- effectiveness status badge and evidence sentence;
- a manual `运行增量回测` button;
- current production thresholds and calibration advice;
- a table with rows for `TOP_5`, `TOP_10`, `WATCH`, `NEUTRAL`, and `AVOID`, and columns for 20/60/120-day average return, win rate, excess return, maximum drawdown, sample count, and sufficiency.

After a manual run, the page reloads validation data. Running the backtest does not refresh rankings or touch unrelated portfolio, trade, general backtest, or AI-analysis flows.

## Error Handling

- Missing NAV windows are normal eligibility misses, not task failures.
- Invalid/missing base NAV excludes only that fund observation.
- A database insert conflict caused by a concurrent run is treated as already cached, not a fatal task failure.
- Unexpected errors are collected in the existing task result and scheduler task log.
- Validation query failures show a page message while leaving the ranking table usable.

## Database Migration

Create a new additive migration that:

- removes pre-existing duplicate logical rows by retaining the newest row;
- adds the unique result key;
- adds a covering query index for model version, horizon, bucket, and score date.

No existing table or column is removed or repurposed.

## Testing

Backend tests cover:

- eligibility at exactly 20/60/120 future NAV observations;
- no arbitrary score-row limit;
- all five bucket calculations;
- idempotent incremental reruns;
- weighted aggregation and date ranges;
- statistical sufficiency;
- effective, neutral, failed, and insufficient conclusions;
- manual POST and validation GET contracts;
- scheduled delegation at the new task method;
- preservation of existing screener controller, scoring, and scheduler behavior.

Frontend tests cover the pure validation-view transformations and labels. Type checking and production build verify Vue integration. The full backend suite and frontend build are required before completion.

## Scope Boundaries

- Do not add new scoring factors in this iteration.
- Do not automatically change thresholds or historical recommendation levels.
- Do not change general portfolio backtesting.
- Do not change trading, holdings, data-source, authentication, or AI-analysis behavior.
- Do not claim predictive certainty; all UI copy remains framed as historical validation and selection reference.
