# Fund Screener Batch 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the screener scoring universe and sync independent screener NAV data.

**Architecture:** `FundUniverseServiceImpl.rebuildUniverse()` reads `screener_fund_universe` and `screener_fund_nav_daily`, writes inclusion/exclusion decisions to `screener_universe_filter`, and does not touch existing strategy or holding tables. `FundScreenerNavServiceImpl.syncNav()` reads included funds from `screener_universe_filter`, fetches historical NAV through existing data-source adapters, and upserts only into `screener_fund_nav_daily`.

**Tech Stack:** Spring Boot, MyBatis Plus, JUnit 5, Mockito, existing fund datasource adapters.

---

### Task 1: Universe Filter Rebuild

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundUniverseServiceImpl.java`
- Modify: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundUniverseServiceImplTest.java`

- [ ] Add failing tests for included supported funds, C/E duplicate exclusion, unsupported type exclusion, abnormal status exclusion, and NAV sample exclusion.
- [ ] Implement filter rebuild and idempotent `screener_universe_filter` upsert.
- [ ] Run `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundUniverseServiceImplTest" test`.

### Task 2: Screener NAV Sync

**Files:**
- Create: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundScreenerNavServiceImplTest.java`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundScreenerNavServiceImpl.java`

- [ ] Add failing tests for included-only sync, idempotent upsert by `fund_code + nav_date`, incremental start date, and datasource failure preservation.
- [ ] Implement adapter querying and independent NAV upsert.
- [ ] Run `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundScreenerNavServiceImplTest" test`.

### Task 3: Verification

**Files:**
- No new files.

- [ ] Run targeted tests for universe and NAV sync.
- [ ] Run full backend test suite.
- [ ] Run frontend build to ensure API surface remains consistent.

