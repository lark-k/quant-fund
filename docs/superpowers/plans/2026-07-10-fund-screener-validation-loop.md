# Fund Screener Validation Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build cached manual and scheduled fund-screener backtests, aggregate them into an effectiveness/calibration view, and expose the closed loop on the fund screener page.

**Architecture:** One cached row represents one score date, horizon, bucket, and model version. A single incremental service powers both the manual API and the 23:50 scheduler; a read-only aggregation API computes statistically qualified metrics, effectiveness, and conservative calibration advice from cached rows.

**Tech Stack:** Java 21, Spring Boot 3.5, MyBatis-Plus, MySQL 8, JUnit 5/Mockito/AssertJ, Vue 3, TypeScript, Element Plus, Vitest, Vite.

## Global Constraints

- Do not change portfolio backtesting, trading, holdings, authentication, data-source, or AI-analysis behavior.
- Do not add scoring factors.
- Do not automatically modify production thresholds or historical recommendation levels.
- Keep the existing disclaimer and frame results as historical validation, not promised returns.
- Treat a metric as statistically sufficient only with at least 30 observations and 3 score dates.
- Use 60 NAV observations as the primary effectiveness horizon.

---

## File Structure

- `quant-fund-server/src/main/java/com/lk/quantfund/config/QuantFundProperties.java`: configurable score and validation policy.
- `quant-fund-server/src/main/java/com/lk/quantfund/service/FundScreenerBacktestService.java`: manual-run and validation-query contract.
- `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundScreenerBacktestServiceImpl.java`: eligibility, idempotent persistence, weighted aggregation, conclusion, and advice.
- `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerBacktestMetricVO.java`: flat bucket/horizon metric.
- `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerStrategyPolicyVO.java`: current tunable threshold policy.
- `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerValidationVO.java`: complete strategy-validation response.
- `quant-fund-server/src/main/java/com/lk/quantfund/controller/FundScreenerController.java`: manual POST, compatibility GET, and validation GET endpoints.
- `quant-fund-server/src/main/java/com/lk/quantfund/scheduler/ScheduledFundScreenerTaskService.java`: scheduler adapter.
- `quant-fund-server/src/main/java/com/lk/quantfund/scheduler/QuantFundScheduler.java`: 23:50 trigger.
- `docs/sql/010_fund_screener_validation_loop.sql`: duplicate cleanup and unique/cache query indexes.
- `quant-fund-web/src/types/domain.ts`: validation API types.
- `quant-fund-web/src/api/quant.ts` and `quant-fund-web/src/api/mock.ts`: real and mock API methods.
- `quant-fund-web/src/utils/fundScreenerValidation.ts`: pure bucket/horizon table transformation and labels.
- `quant-fund-web/src/utils/fundScreenerValidation.test.ts`: frontend transformation tests.
- `quant-fund-web/src/views/fund/FundScreenerView.vue`: strategy-validation panel.

### Task 1: Configuration-backed recommendation policy

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/config/QuantFundProperties.java`
- Modify: `quant-fund-server/src/main/resources/application.yml`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundQualityScoreServiceImpl.java`
- Test: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundQualityScoreServiceImplTest.java`

**Interfaces:**
- Produces: `QuantFundProperties.ScreenerStrategy` getters for `strongMinScore`, `strongTopPercent`, `watchMinScore`, `watchTopPercent`, `neutralMinScore`, `minValidationSamples`, and `minValidationScoreDates`.
- Preserves: default recommendation behavior `82/8%`, `72/20%`, and `58`.

- [ ] **Step 1: Write failing threshold tests**

Add tests that construct `QuantFundProperties`, change `strongMinScore` to `95`, refresh scores, and assert that a score below 95 is not `STRONG`; keep a default-policy test proving the current defaults.

- [ ] **Step 2: Run the focused tests and confirm the new configurable-policy test fails**

Run: `mvn "-Dmaven.repo.local=../.m2/repository" -Dtest=FundQualityScoreServiceImplTest test`

Expected: FAIL because `getScreenerStrategy()` and the policy-aware constructor do not exist.

- [ ] **Step 3: Add the policy configuration and use it in recommendation classification**

Add this shape to `QuantFundProperties`:

```java
private final ScreenerStrategy screenerStrategy = new ScreenerStrategy();
public ScreenerStrategy getScreenerStrategy() { return screenerStrategy; }

public static class ScreenerStrategy {
    private BigDecimal strongMinScore = new BigDecimal("82.0000");
    private int strongTopPercent = 8;
    private BigDecimal watchMinScore = new BigDecimal("72.0000");
    private int watchTopPercent = 20;
    private BigDecimal neutralMinScore = new BigDecimal("58.0000");
    private int minValidationSamples = 30;
    private int minValidationScoreDates = 3;
    // conventional getters and setters
}
```

Inject `QuantFundProperties` into the production constructor, retain a test-compatible constructor that creates defaults, and replace hard-coded values in `recommendLevel` with the policy getters. Add matching `quantfund.screener-strategy` environment-backed YAML defaults.

- [ ] **Step 4: Re-run the focused tests**

Expected: all `FundQualityScoreServiceImplTest` tests PASS.

- [ ] **Step 5: Commit**

```text
feat(fund-screener): make recommendation thresholds configurable
```

### Task 2: Validation contracts and cached aggregation

**Files:**
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerBacktestMetricVO.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerStrategyPolicyVO.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerValidationVO.java`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/FundScreenerBacktestService.java`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundScreenerBacktestServiceImpl.java`
- Test: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundScreenerBacktestServiceImplTest.java`

**Interfaces:**
- Produces: `FundScreenerTaskResultVO runIncremental()` and `FundScreenerValidationVO getValidation()`.
- Preserves: `backtest()` as a compatibility alias to `runIncremental()`.

- [ ] **Step 1: Write failing aggregation and conclusion tests**

Use cached `ScreenerBacktestResult` rows to assert sample-weighted metrics, min/max score-date metadata, `statisticallySignificant`, and all four statuses: `EFFECTIVE`, `NEUTRAL`, `FAILED`, `INSUFFICIENT`.

Core assertion shape:

```java
FundScreenerValidationVO validation = service.getValidation();
assertThat(validation.status()).isEqualTo("EFFECTIVE");
assertThat(validation.metrics()).filteredOn(m -> m.bucketName().equals("TOP_10") && m.horizonDays() == 60)
        .singleElement().extracting(FundScreenerBacktestMetricVO::sampleCount).isEqualTo(45);
```

- [ ] **Step 2: Run the focused tests and confirm RED**

Expected: compilation failure because the validation records and service methods do not exist.

- [ ] **Step 3: Implement the response records and weighted aggregation**

Use immutable records with these contracts:

```java
public record FundScreenerBacktestMetricVO(String bucketName, int horizonDays,
        int sampleCount, int scoreDateCount, double avgForwardReturn, double winRate,
        double avgExcessReturn, double maxDrawdown, boolean statisticallySignificant) {}

public record FundScreenerStrategyPolicyVO(BigDecimal strongMinScore, int strongTopPercent,
        BigDecimal watchMinScore, int watchTopPercent, BigDecimal neutralMinScore,
        int minValidationSamples, int minValidationScoreDates) {}

public record FundScreenerValidationVO(String latestRunDate, String earliestScoreDate,
        String latestScoreDate, String status, String conclusion, List<String> calibrationAdvice,
        FundScreenerStrategyPolicyVO policy, List<FundScreenerBacktestMetricVO> metrics) {}
```

Aggregate average return, win rate, and excess return with `sampleCount` weights; use the worst minimum drawdown. Return `INSUFFICIENT` on empty data. Apply the exact 60-day effectiveness rules from the design.

- [ ] **Step 4: Re-run focused tests and commit**

Expected: PASS.

Commit: `feat(fund-screener): aggregate cached strategy validation`

### Task 3: Idempotent incremental backtest persistence

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundScreenerBacktestServiceImpl.java`
- Create: `docs/sql/010_fund_screener_validation_loop.sql`
- Test: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundScreenerBacktestServiceImplTest.java`

**Interfaces:**
- Consumes: historical `ScreenerQualityScore` and `ScreenerFundNavDaily` rows.
- Produces: at most one active result for `(scoreDate, horizonDays, bucketName, modelVersion)`.

- [ ] **Step 1: Write failing incremental tests**

Cover exactly 20/60/120 future observations, all five buckets, missing-window exclusion, removal of the old 200-row limit, and a rerun whose existing keys are counted as skipped with no insert.

- [ ] **Step 2: Run focused tests and confirm failures come from old non-idempotent behavior**

Expected failures include duplicate `insert` calls and missing `skippedCount`.

- [ ] **Step 3: Implement minimal incremental execution**

Load all score rows, group by date, preload existing result keys for model version `screener-rule-v2`, cache NAV by fund during the run, and execute horizons `[20, 60, 120]` and buckets `[TOP_5, TOP_10, WATCH, NEUTRAL, AVOID]`. Missing NAV is an eligibility miss. Count inserted, existing, and unexpected failures separately.

Add migration SQL that removes older duplicates and then adds:

```sql
ALTER TABLE screener_backtest_result
  ADD UNIQUE KEY uk_screener_backtest_unit (score_date, horizon_days, bucket_name, model_version),
  ADD KEY idx_screener_backtest_validation (model_version, horizon_days, bucket_name, score_date);
```

- [ ] **Step 4: Run the focused test class and commit**

Expected: PASS.

Commit: `feat(fund-screener): persist incremental backtest results idempotently`

### Task 4: Manual and read-only validation APIs

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/controller/FundScreenerController.java`
- Test: `quant-fund-server/src/test/java/com/lk/quantfund/controller/FundScreenerControllerTest.java`

**Interfaces:**
- Produces: `POST /api/fund-screener/backtest` and `GET /api/fund-screener/backtest/validation`.
- Preserves: legacy `GET /api/fund-screener/backtest`.

- [ ] **Step 1: Write failing controller delegation tests**

Assert POST delegates to `runIncremental`, validation GET delegates to `getValidation`, and legacy GET still delegates to `backtest`.

- [ ] **Step 2: Run `FundScreenerControllerTest` and confirm RED**

Expected: missing controller methods.

- [ ] **Step 3: Add the endpoints**

Use separate `@PostMapping("/backtest")`, `@GetMapping("/backtest/validation")`, and the retained `@GetMapping("/backtest")` methods, each returning the existing `ApiResponse.success(...)` envelope and fund-screener rate limits.

- [ ] **Step 4: Re-run and commit**

Expected: PASS.

Commit: `feat(fund-screener): expose strategy validation APIs`

### Task 5: 23:50 automatic incremental validation

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/scheduler/ScheduledFundScreenerTaskService.java`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/scheduler/QuantFundScheduler.java`
- Test: `quant-fund-server/src/test/java/com/lk/quantfund/scheduler/ScheduledFundScreenerTaskServiceTest.java`

**Interfaces:**
- Produces: `SchedulerTaskResult runIncrementalBacktest()`.
- Trigger: weekday cron `0 50 23 ? * MON-FRI`, zone `Asia/Shanghai`, task name `SCREENER_INCREMENTAL_BACKTEST`.

- [ ] **Step 1: Write a failing scheduler-adapter test**

Mock `FundScreenerBacktestService.runIncremental()` with success/skipped counts and verify the adapter preserves them.

- [ ] **Step 2: Run the scheduler test and confirm RED**

Expected: constructor/method missing.

- [ ] **Step 3: Inject the backtest service, add the adapter method, and add the cron trigger**

The scheduler method is:

```java
@Scheduled(cron = "0 50 23 ? * MON-FRI", zone = ZONE)
public void runScreenerIncrementalBacktest() {
    runScreenerTask("SCREENER_INCREMENTAL_BACKTEST", scheduledFundScreenerTaskService::runIncrementalBacktest);
}
```

Update the adapter so `skippedCount` is represented in scheduler results without turning cached work into failure.

- [ ] **Step 4: Run scheduler and controller regression tests and commit**

Expected: PASS.

Commit: `feat(fund-screener): schedule nightly incremental validation`

### Task 6: Strategy-validation UI and frontend tests

**Files:**
- Modify: `quant-fund-web/package.json`
- Modify: `quant-fund-web/package-lock.json`
- Modify: `quant-fund-web/src/types/domain.ts`
- Modify: `quant-fund-web/src/api/quant.ts`
- Modify: `quant-fund-web/src/api/mock.ts`
- Create: `quant-fund-web/src/utils/fundScreenerValidation.ts`
- Create: `quant-fund-web/src/utils/fundScreenerValidation.test.ts`
- Modify: `quant-fund-web/src/views/fund/FundScreenerView.vue`

**Interfaces:**
- Consumes: `FundScreenerValidation` flat metrics.
- Produces: five bucket rows with 20/60/120 nested metrics and Chinese labels.

- [ ] **Step 1: Add Vitest and write failing pure transformation tests**

Test that a flat `TOP_10/60` metric appears under `row.horizons[60]`, that all five buckets remain ordered, and that insufficient metrics receive `不具备统计意义`.

- [ ] **Step 2: Run `npm test -- --run` and confirm RED**

Expected: module/function missing.

- [ ] **Step 3: Add types, API methods, mock validation data, and the pure transformer**

Expose:

```ts
runFundScreenerBacktest(): Promise<FundScreenerTaskResult>
fundScreenerValidation(): Promise<FundScreenerValidation>
```

Use POST `/fund-screener/backtest` and GET `/fund-screener/backtest/validation`.

- [ ] **Step 4: Add the strategy-validation panel**

Load ranking and validation independently on mount. The panel must keep working states separate, show metadata/status/advice/policy, render all requested metrics for 20/60/120, label insufficient samples, and reload validation after a manual incremental run. A validation error must not clear ranking data.

- [ ] **Step 5: Run frontend tests, typecheck, and production build**

Run: `npm test -- --run`, `npm run typecheck`, `npm run build`.

Expected: all exit 0.

- [ ] **Step 6: Commit**

Commit: `feat(fund-screener): add strategy validation workspace`

### Task 7: Full regression and requirement audit

**Files:**
- Verify all files above; modify only to fix demonstrated failures.

- [ ] **Step 1: Run focused backend tests**

```text
mvn "-Dmaven.repo.local=../.m2/repository" -Dtest=FundQualityScoreServiceImplTest,FundScreenerBacktestServiceImplTest,FundScreenerControllerTest,ScheduledFundScreenerTaskServiceTest test
```

- [ ] **Step 2: Run the complete backend suite**

Run: `mvn "-Dmaven.repo.local=../.m2/repository" test`

Expected: BUILD SUCCESS with zero failed tests.

- [ ] **Step 3: Run frontend verification**

Run: `npm test -- --run && npm run typecheck && npm run build` from `quant-fund-web` using separate PowerShell commands.

Expected: all exit 0.

- [ ] **Step 4: Audit the final diff against P0–P3**

Confirm manual execution, cached eligible horizons, sample sufficiency, scheduled idempotent execution, effectiveness/calibration response, UI metadata/table/conclusion, migration, backward compatibility, and unchanged unrelated modules.

- [ ] **Step 5: Run `git diff --check` and inspect `git status --short`**

Expected: no whitespace errors; only intentional files changed.

- [ ] **Step 6: Commit any verification-only fixes**

Commit only if failures required changes, using `fix(fund-screener): address validation loop regressions`.
