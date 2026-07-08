# Fund Screener Batch 4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate transparent screener factor snapshots and quality scores, then expose real rank/explain data.

**Architecture:** `FundFactorServiceImpl` reads included funds and `screener_fund_nav_daily`, computes trailing returns, drawdown, volatility, positive-day ratio, trend slope, sample size, and fund size, then upserts `screener_factor_snapshot`. `FundQualityScoreServiceImpl` reads factor snapshots plus universe data, calculates transparent weighted scores by fund type, upserts `screener_quality_score`, and maps score rows into rank/explain VOs.

**Tech Stack:** Spring Boot, MyBatis Plus, JUnit 5, Mockito, Jackson JSON.

---

### Task 1: Factor Snapshots

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundFactorServiceImpl.java`
- Create: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundFactorServiceImplTest.java`

- [ ] Add failing tests for return, max drawdown, sample size, and upsert behavior.
- [ ] Implement factor calculations using only NAV points at or before the factor date.
- [ ] Run `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundFactorServiceImplTest" test`.

### Task 2: Quality Scores and Query

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundQualityScoreServiceImpl.java`
- Create: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundQualityScoreServiceImplTest.java`

- [ ] Add failing tests for score range, reasons/risks JSON, rank paging, and explain mapping.
- [ ] Implement transparent scoring and score upsert.
- [ ] Implement rank and explain query from screener tables.
- [ ] Run `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundQualityScoreServiceImplTest" test`.

### Task 3: Verification

- [ ] Run targeted tests for factor and quality services.
- [ ] Run full backend tests.
- [ ] Run frontend build.

