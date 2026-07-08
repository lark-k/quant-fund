# Fund Screener Batch 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the fund screener module skeleton, database tables, and frontend menu entry.

**Architecture:** The implementation creates isolated `screener_*` tables and separate backend contracts under the existing `com.lk.quantfund` package. The controller returns safe placeholder results while preserving stable API shapes for later batches. The frontend adds a PC-first dark workbench route and keeps the old strategy config route.

**Tech Stack:** Spring Boot, MyBatis Plus, JUnit 5, Mockito, Vue 3, Vite, TypeScript, Element Plus, MySQL.

---

### Task 1: Backend Controller Contract

**Files:**
- Create: `quant-fund-server/src/test/java/com/lk/quantfund/controller/FundScreenerControllerTest.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/controller/FundScreenerController.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/FundQualityScoreService.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundQualityScoreServiceImpl.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/dto/screener/FundScreenerQueryRequest.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerRankItemVO.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerTaskResultVO.java`

- [ ] **Step 1: Write failing controller test**

Test instantiates `FundScreenerController`, verifies `rank()` delegates to `FundQualityScoreService`, and verifies `refreshScore()` returns a task result.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundScreenerControllerTest" test`

- [ ] **Step 3: Implement minimal controller and service skeleton**

Create the controller, request, VO, service interface, and empty service implementation.

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven test command.

### Task 2: Database and Mapper Skeleton

**Files:**
- Create: `docs/sql/008_fund_screener.sql`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/entity/ScreenerFundUniverse.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/entity/ScreenerFundNavDaily.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/entity/ScreenerUniverseFilter.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/entity/ScreenerFactorSnapshot.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/entity/ScreenerQualityScore.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/mapper/ScreenerFundUniverseMapper.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/mapper/ScreenerFundNavDailyMapper.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/mapper/ScreenerUniverseFilterMapper.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/mapper/ScreenerFactorSnapshotMapper.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/mapper/ScreenerQualityScoreMapper.java`

- [ ] **Step 1: Add SQL and entity/mapper classes**

Use `screener_` table names only and keep `fund_nav_daily` untouched.

- [ ] **Step 2: Apply SQL to local MySQL**

Run the SQL against database `quant_fund` with the provided local credentials.

### Task 3: Remaining Backend Skeleton

**Files:**
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/FundUniverseService.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/FundScreenerNavService.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/FundFactorService.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/FundScreenerBacktestService.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundUniverseServiceImpl.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundScreenerNavServiceImpl.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundFactorServiceImpl.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundScreenerBacktestServiceImpl.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/datasource/FundUniverseDataSourceAdapter.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/datasource/model/MarketFundDTO.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/dto/screener/FundUniverseSyncRequest.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerExplainVO.java`
- Create: `quant-fund-server/src/main/java/com/lk/quantfund/vo/screener/FundScreenerScoreBreakdownVO.java`

- [ ] **Step 1: Add interfaces and no-op implementations**

Methods return structured task results and empty optional data until later batches implement real behavior.

### Task 4: Frontend Route, Types, API, Mock, and Page

**Files:**
- Modify: `quant-fund-web/src/router/index.ts`
- Modify: `quant-fund-web/src/layouts/TerminalLayout.vue`
- Modify: `quant-fund-web/src/types/domain.ts`
- Modify: `quant-fund-web/src/api/quant.ts`
- Modify: `quant-fund-web/src/api/mock.ts`
- Create: `quant-fund-web/src/views/fund/FundScreenerView.vue`

- [ ] **Step 1: Add frontend types and API methods**

Add rank query, rank item, explain, task result types and `quantApi` methods.

- [ ] **Step 2: Add mock fund screener data**

Mock rank returns a small paged list and explain returns selected item details.

- [ ] **Step 3: Add route and replace sidebar menu**

Add `/fund-screener`, keep `/strategy-config`, and replace the desktop sidebar label/target.

- [ ] **Step 4: Build usable static workbench page**

Implement filters, metric tiles, table, details drawer, loading, empty, and error states.

### Task 5: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run backend test**

Run: `cd quant-fund-server; mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundScreenerControllerTest" test`

- [ ] **Step 2: Run frontend build**

Run: `cd quant-fund-web; npm.cmd run build`

- [ ] **Step 3: Verify database tables exist**

Run a MySQL metadata query against `quant_fund` for the five `screener_*` tables.

