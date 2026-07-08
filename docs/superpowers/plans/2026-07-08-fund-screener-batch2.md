# Fund Screener Batch 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement fund screener universe synchronization into `screener_fund_universe`.

**Architecture:** `FundUniverseServiceImpl` reads enabled `FundUniverseDataSourceAdapter` implementations, selects the first successful non-empty all-market result, normalizes market fund fields, and upserts by `fund_code`. The existing EastMoney adapter also implements the universe adapter using the public fund-code list endpoint, while failures return structured task results without deleting old screener data.

**Tech Stack:** Spring Boot, MyBatis Plus, JUnit 5, Mockito, WebClient, MySQL.

---

### Task 1: Service TDD

**Files:**
- Create: `quant-fund-server/src/test/java/com/lk/quantfund/service/impl/FundUniverseServiceImplTest.java`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundUniverseServiceImpl.java`

- [ ] Write failing tests for insert, update, and datasource failure preserving old data.
- [ ] Implement adapter selection and screener universe upsert.
- [ ] Run `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=FundUniverseServiceImplTest" test`.

### Task 2: EastMoney Universe Adapter

**Files:**
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/datasource/impl/EastMoneyFundDataSourceAdapter.java`
- Modify: `quant-fund-server/src/main/java/com/lk/quantfund/config/QuantFundProperties.java`
- Modify: `quant-fund-server/src/test/java/com/lk/quantfund/datasource/impl/EastMoneyFundDataSourceAdapterTest.java`

- [ ] Add `eastMoneyFundListUrl` property defaulting to `https://fund.eastmoney.com/js/fundcode_search.js`.
- [ ] Implement `listAllFunds()` parsing EastMoney's fund code search JavaScript array.
- [ ] Implement `listFundsByCategory(category)` by filtering normalized fund type/category text.
- [ ] Run `mvn.cmd "-Dmaven.repo.local=..\.m2\repository" "-Dtest=EastMoneyFundDataSourceAdapterTest" test`.

### Task 3: Verification

**Files:**
- No new files.

- [ ] Run targeted service and datasource tests.
- [ ] Run full backend tests.

