# Fund Screener Batch 6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add independent scheduled fund-screener maintenance tasks and replace the backtest placeholder with a lightweight, verifiable historical Top N check.

**Architecture:** Keep the screener pipeline isolated from existing holding/trade schedulers by introducing a small scheduler adapter service that converts `FundScreenerTaskResultVO` into the existing `SchedulerTaskResult` log shape. Backtest reads only `screener_quality_score` and `screener_fund_nav_daily`, checks future NAV after historical score dates, and returns task counts plus concise summaries without mutating existing business tables.

**Tech Stack:** Spring Boot scheduled tasks, MyBatis Plus, JUnit 5, Mockito.

---

### Task 1: Scheduler Adapter and Cron Wiring

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/config/QuantFundProperties.java`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/scheduler/QuantFundScheduler.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/scheduler/ScheduledFundScreenerTaskService.java`
- Create: `quant-fund-server/src/test/java/com/lk/quantfund/scheduler/ScheduledFundScreenerTaskServiceTest.java`

- [ ] Add a failing test that `ScheduledFundScreenerTaskService.refreshQualityScore()` delegates to `FundQualityScoreService.refreshScore()` and adapts success/failure counts.
- [ ] Add a failing test that skipped screener task results do not count as success or failure.
- [ ] Implement `ScheduledFundScreenerTaskService` with methods `syncUniverse`, `syncNav`, `rebuildUniverse`, `refreshFactors`, and `refreshQualityScore`.
- [ ] Add `quantfund.scheduler.screener-enabled` property, defaulting to `false` to avoid surprise all-market network jobs.
- [ ] Wire `QuantFundScheduler` cron methods:
  - `21:30` `SCREENER_SYNC_UNIVERSE`
  - `22:00` `SCREENER_SYNC_NAV`
  - `23:00` `SCREENER_REBUILD_UNIVERSE`
  - `23:20` `SCREENER_REFRESH_FACTORS`
  - `23:40` `SCREENER_REFRESH_QUALITY_SCORE`
- [ ] Run `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=ScheduledFundScreenerTaskServiceTest" test`.

### Task 2: Lightweight Screener Backtest

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/controller/FundScreenerController.java`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundScreenerBacktestServiceImpl.java`
- Modify: `quant-fund-server/src/test/java/com/lk/quantfund/controller/FundScreenerControllerTest.java`
- Create: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundScreenerBacktestServiceImplTest.java`

- [ ] Add a failing service test with two historical scores and future NAV points; verify the service returns `SUCCESS`, counts evaluated funds, and reports average forward return.
- [ ] Add a failing service test that a score without future NAV is counted as failure and old data is not modified.
- [ ] Implement backtest read path from `screener_quality_score` and `screener_fund_nav_daily`.
- [ ] Expose `GET /api/fund-screener/backtest` from the controller.
- [ ] Run `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundScreenerBacktestServiceImplTest,FundScreenerControllerTest" test`.

### Task 3: Verification

- [ ] Run fund screener targeted tests: `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundUniverseServiceImplTest,FundScreenerNavServiceImplTest,FundFactorServiceImplTest,FundQualityScoreServiceImplTest,FundScreenerBacktestServiceImplTest,FundScreenerControllerTest,ScheduledFundScreenerTaskServiceTest" test`.
- [ ] Run full backend tests: `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" test`.
- [ ] Run frontend build: `npm.cmd run build` in `quant-fund-web`.

