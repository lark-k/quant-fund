# Fund Screener implementation prompt

你是接手 QuantFund 项目的 AI 编程代理。请在本地仓库 `D:\code\personal\quant-fund` 中实现“基金优选”独立模块，并保持现有业务逻辑不受影响。

本任务的目标是：新增一个全市场级别的基金优选模块，建立独立的数据表、服务、接口、调度任务和前端页面；同时将前端侧边栏中的“策略配置”菜单替换为“基金优选”。不要删除原策略配置代码，旧页面可以保留路由或保留文件，但不再作为主菜单入口展示。

## 关键原则

1. 基金优选必须作为独立模块实现，不能改造现有策略配置模块。
2. 不要复用 `strategy_config` 表保存基金优选配置。
3. 不要修改现有持仓、交易、策略信号、AI 分析的核心业务流程。
4. 全市场基金基础库、优选净值、因子、评分结果使用独立 `screener_*` 表。
5. 优选模块失败不能影响登录、持仓、交易、收益分析、AI 分析、基金详情页。
6. 现有 `FundQueryService`、`fund_info`、`fund_nav_daily` 可以作为参考或兜底读取，但基金优选模块不要反向污染这些现有业务表。
7. 前端只替换菜单入口：`策略配置` -> `基金优选`，旧 `StrategyConfigView.vue` 先保留。
8. 全市场任务必须支持开关、限流、失败重试、断点续跑，避免一次任务失败导致旧榜单不可用。
9. 评分结果要可解释，不能只返回一个黑盒分数。
10. 优先做可验证 MVP，再逐步扩展数据源和模型。

## 当前项目背景

技术栈：

- 后端：Spring Boot、MyBatis Plus、MySQL、Redis，主包名 `com.lk.quantfund`
- 前端：Vue 3、Vite、TypeScript、Pinia、Vue Router、Axios、Element Plus、ECharts
- Python 量化引擎：`quant-engine`
- 数据库：`quant_fund`
- 前端风格：PC 优先的深色交易终端风格

当前已有能力：

- 基金基础查询：`quant-fund-server/src/main/java/com/lk/quantfund/controller/FundController.java`
- 基金数据服务：`quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundQueryServiceImpl.java`
- 东方财富数据源适配：`quant-fund-server/src/main/java/com/lk/quantfund/datasource/impl/EastMoneyFundDataSourceAdapter.java`
- 基金基础表和净值表：`docs/sql/001_schema.sql`
- 调度入口：`quant-fund-server/src/main/java/com/lk/quantfund/scheduler/QuantFundScheduler.java`
- 前端路由：`quant-fund-web/src/router/index.ts`
- 前端主布局和菜单：`quant-fund-web/src/layouts/TerminalLayout.vue`
- 前端 API 封装：`quant-fund-web/src/api/quant.ts`
- 前端领域类型：`quant-fund-web/src/types/domain.ts`
- Python 已有净值因子计算参考：`quant-engine/app/features/nav_features.py`
- Python 已有评分参考：`quant-engine/app/strategies/scoring.py`

如果仓库根目录存在 `.codegraph/`，理解代码时优先使用 CodeGraph：

```powershell
cmd /c codegraph explore "FundController FundQueryServiceImpl FundScreener"
```

再使用 `rg`、文件读取等方式补充细节。

## 功能目标

实现全市场级别基金优选：

1. 同步全市场公募基金基础信息。
2. 为基金优选模块维护独立基金净值表。
3. 建立有效评分池，剔除不可评分基金。
4. 按基金类型计算多因子快照。
5. 生成每日基金质量评分和排名。
6. 提供优选榜单接口和单基金解释接口。
7. 前端新增“基金优选”页面。
8. 将前端侧边栏“策略配置”菜单替换成“基金优选”。
9. 旧策略配置页面保留但隐藏主菜单入口。

## 推荐交付范围

MVP 第一版覆盖：

- 主动权益基金
- 混合基金
- 指数基金

MVP 第一版暂时排除：

- 货币基金
- QDII
- 成立不足 1 年的基金
- 净值样本不足 120 或 250 天的基金
- 规模过小的基金
- 清盘、终止、异常状态基金
- 同一基金重复 C 类/E 类份额，默认保留主份额或 A 类份额

MVP 第一版评分指标：

- 近 20/60/120/250 日收益
- 近 250 日年化收益
- 近 60/120 日最大回撤
- 近 60/120 日年化波动率
- 近 60 日正收益天数占比
- 近 60 日趋势斜率
- 相对基准或跟踪指数的超额收益
- 同类排名百分位
- 净值样本数量
- 基金规模和基础信息完整度

## 数据库设计

新增 SQL 建议放到 `docs/sql/008_fund_screener.sql`。表名统一使用 `screener_` 前缀，避免影响现有表。

### screener_fund_universe

全市场基金基础库。

```sql
CREATE TABLE IF NOT EXISTS screener_fund_universe (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  fund_code VARCHAR(32) NOT NULL COMMENT 'Fund code',
  fund_name VARCHAR(128) NOT NULL COMMENT 'Fund name',
  fund_type VARCHAR(32) NOT NULL COMMENT 'Fund type',
  share_class VARCHAR(32) DEFAULT NULL COMMENT 'Share class: A/C/E/ETF/LOF etc',
  main_fund_code VARCHAR(32) DEFAULT NULL COMMENT 'Main fund code for duplicate share classes',
  company_name VARCHAR(128) DEFAULT NULL COMMENT 'Fund company',
  manager_name VARCHAR(128) DEFAULT NULL COMMENT 'Fund manager',
  establish_date DATE DEFAULT NULL COMMENT 'Establish date',
  fund_size DECIMAL(20,4) DEFAULT NULL COMMENT 'Fund size in 100M CNY or source unit',
  tracking_index VARCHAR(128) DEFAULT NULL COMMENT 'Tracking index',
  active_fund TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether actively managed fund',
  risk_level VARCHAR(32) DEFAULT NULL COMMENT 'Risk level',
  status VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/LIQUIDATING/TERMINATED/UNKNOWN',
  source_name VARCHAR(64) NOT NULL COMMENT 'Data source name',
  last_sync_time DATETIME(3) DEFAULT NULL COMMENT 'Last source sync time',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_screener_fund_universe_code (fund_code),
  KEY idx_screener_fund_universe_type (fund_type),
  KEY idx_screener_fund_universe_status (status),
  KEY idx_screener_fund_universe_main_code (main_fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fund screener market universe';
```

### screener_fund_nav_daily

基金优选专用净值表。不要把全市场大量净值直接写进现有 `fund_nav_daily`。

```sql
CREATE TABLE IF NOT EXISTS screener_fund_nav_daily (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  fund_code VARCHAR(32) NOT NULL COMMENT 'Fund code',
  nav_date DATE NOT NULL COMMENT 'NAV date',
  unit_nav DECIMAL(20,6) NOT NULL COMMENT 'Unit NAV',
  accumulated_nav DECIMAL(20,6) DEFAULT NULL COMMENT 'Accumulated NAV',
  daily_growth_rate DECIMAL(10,4) DEFAULT NULL COMMENT 'Daily growth rate percentage',
  source_name VARCHAR(64) NOT NULL COMMENT 'Data source name',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_screener_nav_code_date (fund_code, nav_date),
  KEY idx_screener_nav_date (nav_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fund screener daily NAV';
```

### screener_universe_filter

记录基金是否纳入评分池，以及为什么被排除。

```sql
CREATE TABLE IF NOT EXISTS screener_universe_filter (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  fund_code VARCHAR(32) NOT NULL COMMENT 'Fund code',
  included TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether included in scoring universe',
  universe_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'EQUITY/MIXED/INDEX/BOND/ETF/QDII/UNKNOWN',
  exclude_reason VARCHAR(512) DEFAULT NULL COMMENT 'Exclude reason',
  min_nav_days_passed TINYINT NOT NULL DEFAULT 0,
  size_filter_passed TINYINT NOT NULL DEFAULT 0,
  duplicate_filter_passed TINYINT NOT NULL DEFAULT 0,
  status_filter_passed TINYINT NOT NULL DEFAULT 0,
  last_rebuild_time DATETIME(3) DEFAULT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_screener_filter_code (fund_code),
  KEY idx_screener_filter_included_type (included, universe_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fund screener universe filter';
```

### screener_factor_snapshot

每日因子快照。

```sql
CREATE TABLE IF NOT EXISTS screener_factor_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  fund_code VARCHAR(32) NOT NULL COMMENT 'Fund code',
  factor_date DATE NOT NULL COMMENT 'Factor date',
  return_20d DECIMAL(10,4) DEFAULT NULL,
  return_60d DECIMAL(10,4) DEFAULT NULL,
  return_120d DECIMAL(10,4) DEFAULT NULL,
  return_250d DECIMAL(10,4) DEFAULT NULL,
  annual_return_250d DECIMAL(10,4) DEFAULT NULL,
  volatility_60d DECIMAL(10,4) DEFAULT NULL,
  volatility_120d DECIMAL(10,4) DEFAULT NULL,
  max_drawdown_60d DECIMAL(10,4) DEFAULT NULL,
  max_drawdown_120d DECIMAL(10,4) DEFAULT NULL,
  positive_day_ratio_60d DECIMAL(10,4) DEFAULT NULL,
  trend_slope_60d DECIMAL(10,4) DEFAULT NULL,
  excess_return_60d DECIMAL(10,4) DEFAULT NULL,
  excess_return_120d DECIMAL(10,4) DEFAULT NULL,
  peer_percentile DECIMAL(10,4) DEFAULT NULL,
  fund_size DECIMAL(20,4) DEFAULT NULL,
  nav_sample_size INT NOT NULL DEFAULT 0,
  source_name VARCHAR(64) NOT NULL DEFAULT 'SCREENER',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_screener_factor_code_date (fund_code, factor_date),
  KEY idx_screener_factor_date (factor_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fund screener factor snapshot';
```

### screener_quality_score

最终榜单评分。

```sql
CREATE TABLE IF NOT EXISTS screener_quality_score (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  fund_code VARCHAR(32) NOT NULL COMMENT 'Fund code',
  score_date DATE NOT NULL COMMENT 'Score date',
  quality_score DECIMAL(10,4) NOT NULL DEFAULT 0,
  return_score DECIMAL(10,4) NOT NULL DEFAULT 0,
  risk_score DECIMAL(10,4) NOT NULL DEFAULT 0,
  stability_score DECIMAL(10,4) NOT NULL DEFAULT 0,
  excess_score DECIMAL(10,4) NOT NULL DEFAULT 0,
  peer_score DECIMAL(10,4) NOT NULL DEFAULT 0,
  liquidity_score DECIMAL(10,4) NOT NULL DEFAULT 0,
  data_score DECIMAL(10,4) NOT NULL DEFAULT 0,
  rank_no INT DEFAULT NULL,
  rank_percentile DECIMAL(10,4) DEFAULT NULL,
  recommend_level VARCHAR(32) NOT NULL DEFAULT 'NEUTRAL' COMMENT 'STRONG/WATCH/NEUTRAL/AVOID',
  reasons_json JSON NOT NULL,
  risks_json JSON NOT NULL,
  model_version VARCHAR(64) NOT NULL DEFAULT 'screener-rule-v1',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_screener_score_code_date (fund_code, score_date),
  KEY idx_screener_score_date_score (score_date, quality_score),
  KEY idx_screener_score_level (recommend_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fund screener quality score';
```

## 后端设计

新增独立包和类。不要把基金优选逻辑塞进 `StrategyServiceImpl` 或现有策略规则中。

建议新增：

```text
quant-fund-server/src/main/java/com/lk/quantfund/controller/FundScreenerController.java
quant-fund-server/src/main/java/com/lk/quantfund/service/FundUniverseService.java
quant-fund-server/src/main/java/com/lk/quantfund/service/FundScreenerNavService.java
quant-fund-server/src/main/java/com/lk/quantfund/service/FundFactorService.java
quant-fund-server/src/main/java/com/lk/quantfund/service/FundQualityScoreService.java
quant-fund-server/src/main/java/com/lk/quantfund/service/FundScreenerBacktestService.java
quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundUniverseServiceImpl.java
quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundScreenerNavServiceImpl.java
quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundFactorServiceImpl.java
quant-fund-server/src/main/java/com/lk/quantfund/service/impl/FundQualityScoreServiceImpl.java
quant-fund-server/src/main/java/com/lk/quantfund/datasource/FundUniverseDataSourceAdapter.java
```

新增实体和 Mapper：

```text
entity/ScreenerFundUniverse.java
entity/ScreenerFundNavDaily.java
entity/ScreenerUniverseFilter.java
entity/ScreenerFactorSnapshot.java
entity/ScreenerQualityScore.java
mapper/ScreenerFundUniverseMapper.java
mapper/ScreenerFundNavDailyMapper.java
mapper/ScreenerUniverseFilterMapper.java
mapper/ScreenerFactorSnapshotMapper.java
mapper/ScreenerQualityScoreMapper.java
```

新增 DTO/VO：

```text
dto/screener/FundScreenerQueryRequest.java
dto/screener/FundUniverseSyncRequest.java
vo/screener/FundScreenerRankItemVO.java
vo/screener/FundScreenerExplainVO.java
vo/screener/FundScreenerScoreBreakdownVO.java
vo/screener/FundScreenerTaskResultVO.java
datasource/model/MarketFundDTO.java
```

### 数据源接口

新增全市场基金列表数据源接口：

```java
public interface FundUniverseDataSourceAdapter {
    String sourceName();

    int priority();

    boolean enabled();

    List<MarketFundDTO> listAllFunds();

    List<MarketFundDTO> listFundsByCategory(String category);
}
```

注意：

- 外部数据源失败时不要清空 `screener_fund_universe`。
- 记录失败日志，保留旧数据和旧评分。
- 若无法稳定获取全市场列表，允许第一版支持本地 CSV 导入或手动 seed 作为兜底，但接口和表结构必须按全市场设计保留。
- 不要在查询榜单时实时请求外部全市场数据。

### 服务职责

`FundUniverseService`：

- 同步全市场基金列表到 `screener_fund_universe`
- 标准化基金类型、份额类别、状态
- 识别 A/C/E 等重复份额并设置 `main_fund_code`

`FundScreenerNavService`：

- 对 `screener_universe_filter.included = true` 的基金增量同步净值
- 按 `fund_code + nav_date` 幂等 upsert
- 支持按日期缺口补齐

`FundFactorService`：

- 基于 `screener_fund_nav_daily` 计算因子
- 写入 `screener_factor_snapshot`
- 因子计算必须只使用当前 `factor_date` 及之前的数据，避免未来函数

`FundQualityScoreService`：

- 基于因子和基金类型生成各维度评分
- 写入 `screener_quality_score`
- 按基金类型分别排名
- 生成 `reasons_json` 和 `risks_json`

`FundScreenerBacktestService`：

- 后续增强项。第一版可以只提供接口骨架或轻量实现。
- 回测必须在历史 T 日只使用 T 日以前数据计算评分，再看未来收益。

## 评分规则

评分必须分类型，不要把主动权益、债券、指数基金混在一起比较。

主动权益 / 混合：

```text
收益能力 35%
风险控制 25%
稳定性 15%
超额收益 15%
同类排名 5%
数据质量 5%
```

指数 / ETF：

```text
收益表现 25%
回撤控制 20%
跟踪质量 25%
规模流动性 20%
数据质量 10%
```

债券基金后续扩展：

```text
收益能力 25%
回撤控制 35%
稳定性 25%
规模流动性 10%
数据质量 5%
```

第一版可以只实现主动权益、混合、指数基金。对于暂不支持的类型，写明 `exclude_reason`。

评分建议：

- 所有子分数范围为 0 到 100。
- 综合分范围为 0 到 100。
- `quality_score >= 85` 可为 `STRONG`。
- `quality_score >= 75` 可为 `WATCH`。
- `quality_score >= 60` 可为 `NEUTRAL`。
- 低于 60 可为 `AVOID`。

推荐理由示例：

```json
[
  "近120日收益处于同类前20%",
  "近120日最大回撤低于同类中位数",
  "近60日正收益天数占比较高",
  "净值样本充足，评分可信度较高"
]
```

风险提示示例：

```json
[
  "近期涨幅较高，短线追高风险上升",
  "历史回撤不代表未来最大亏损",
  "基金优选结果仅供参考，不构成投资建议"
]
```

## 后端接口

新增 Controller 路径建议：

```text
/api/v1/fund-screener
```

如果当前系统实际 API 前缀不是 `/api/v1`，以 `SystemConstants.API_PREFIX` 为准。

### 查询榜单

```http
GET /api/v1/fund-screener/rank
```

参数：

```text
fundType
period=60d/120d/250d
riskLevel
minScore
minFundSize
excludeShareClassC
onlyActiveFund
recommendLevel
pageNo
pageSize
sortBy
```

返回建议：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "total": 100,
  "records": [
    {
      "fundCode": "000001",
      "fundName": "示例基金A",
      "fundType": "MIXED",
      "companyName": "示例基金公司",
      "managerName": "基金经理",
      "qualityScore": 86.5,
      "returnScore": 88.0,
      "riskScore": 78.0,
      "stabilityScore": 82.0,
      "excessScore": 81.0,
      "peerScore": 75.0,
      "dataScore": 95.0,
      "rankNo": 1,
      "rankPercentile": 1.2,
      "recommendLevel": "STRONG",
      "return60d": 8.2,
      "return120d": 16.4,
      "return250d": 28.0,
      "maxDrawdown120d": -9.8,
      "volatility120d": 18.6,
      "peerPercentile": 12.5,
      "scoreDate": "2026-07-03",
      "reasons": ["近120日收益强于同类"],
      "risks": ["近期涨幅较高，短线追高风险上升"],
      "disclaimer": "仅供参考，不构成投资建议，不承诺收益"
    }
  ]
}
```

### 单基金解释

```http
GET /api/v1/fund-screener/{fundCode}/explain
```

返回：

- 基础信息
- 当前评分
- 各维度拆解
- 核心因子
- 推荐理由
- 风险提示
- 数据日期
- 模型版本
- 免责声明

### 管理类接口

可以先做为登录用户可用，后续再加管理员权限控制：

```http
POST /api/v1/fund-screener/sync-universe
POST /api/v1/fund-screener/sync-nav
POST /api/v1/fund-screener/rebuild-universe
POST /api/v1/fund-screener/refresh-score
GET  /api/v1/fund-screener/backtest
```

管理接口必须有合理超时、任务结果和错误摘要，不能让前端无反馈地等待过久。

## 调度设计

新增独立调度任务，沿用现有调度和任务日志风格。

建议时间：

```text
21:30 SYNC_SCREENER_FUND_UNIVERSE
22:00 SYNC_SCREENER_NAV
23:00 REBUILD_SCREENER_UNIVERSE
23:20 REFRESH_SCREENER_FACTORS
23:40 REFRESH_SCREENER_QUALITY_SCORE
```

任务要求：

- 可通过配置关闭。
- 分批执行，比如每批 50 或 100 只基金。
- 每批失败不影响其他批次。
- 外部数据源失败时保留旧数据。
- 评分失败时保留旧榜单。
- 日志记录成功数、失败数、跳过数、耗时、错误摘要。

## 前端设计

新增页面：

```text
quant-fund-web/src/views/fund/FundScreenerView.vue
```

更新路由：

```text
path: 'fund-screener'
name: 'fund-screener'
component: () => import('@/views/fund/FundScreenerView.vue')
meta: { title: '基金优选' }
```

更新菜单：

当前侧边栏中：

```ts
{ to: '/strategy-config', label: '策略配置', icon: Histogram }
```

替换为：

```ts
{ to: '/fund-screener', label: '基金优选', icon: Histogram }
```

注意：

- 不要删除 `StrategyConfigView.vue`。
- `/strategy-config` 路由可以保留，避免用户旧链接失效。
- 移动端第一版可以不加基金优选入口，除非空间允许。
- 当前项目已有部分中文 mojibake，新增页面必须使用正常 UTF-8 中文。

### 页面结构

不要做营销落地页，首屏就是可用的基金优选工作台。

页面应包含：

1. 顶部筛选区
   - 基金类型
   - 周期
   - 推荐等级
   - 最低评分
   - 规模门槛
   - 是否排除 C 类
   - 刷新按钮

2. 核心指标区
   - 已覆盖基金数
   - 入选评分池基金数
   - 今日已评分基金数
   - 最新评分日期

3. 榜单表格
   - 排名
   - 基金代码/名称
   - 基金类型
   - 综合分
   - 近 3 月/6 月/1 年收益
   - 最大回撤
   - 波动率
   - 同类排名或百分位
   - 推荐等级
   - 操作

4. 详情抽屉或侧栏
   - 评分拆解
   - 推荐理由
   - 风险提示
   - 数据日期
   - 模型版本

5. 操作
   - 查看基金详情，跳转现有 `/fund-detail?fundCode=...`
   - 加入关注或候选池可后续实现
   - 创建定投计划可后续实现

### 前端类型和 API

在 `quant-fund-web/src/types/domain.ts` 中新增：

```ts
export type FundScreenerRecommendLevel = 'STRONG' | 'WATCH' | 'NEUTRAL' | 'AVOID'

export interface FundScreenerRankItem {
  fundCode: string
  fundName: string
  fundType: string
  companyName?: string | null
  managerName?: string | null
  qualityScore: number
  returnScore: number
  riskScore: number
  stabilityScore: number
  excessScore: number
  peerScore: number
  liquidityScore?: number
  dataScore: number
  rankNo?: number | null
  rankPercentile?: number | null
  recommendLevel: FundScreenerRecommendLevel
  return60d?: number | null
  return120d?: number | null
  return250d?: number | null
  maxDrawdown120d?: number | null
  volatility120d?: number | null
  peerPercentile?: number | null
  scoreDate: string
  reasons: string[]
  risks: string[]
  disclaimer: string
}
```

在 `quant-fund-web/src/api/quant.ts` 中新增：

```ts
fundScreenerRank(params?: FundScreenerRankQuery): Promise<PageResponse<FundScreenerRankItem>>
fundScreenerExplain(fundCode: string): Promise<FundScreenerExplain>
refreshFundScreenerScore(): Promise<FundScreenerTaskResult>
```

如果 mock 模式需要页面可开发，给 `mock.ts` 增加少量 mock 榜单数据，但真实模式不能依赖 mock。

## 实施顺序

严格按批次推进，避免一次性大改。

### 批次 1：模块骨架和前端菜单

目标：

- 新增 SQL 文件。
- 新增实体、Mapper、Service、Controller 骨架。
- 新增前端 `FundScreenerView.vue` 空页面或静态骨架。
- 替换侧边栏菜单：`策略配置` -> `基金优选`。
- 保留 `/strategy-config` 路由和旧页面。

验收：

- 后端可启动。
- 前端可构建。
- 侧边栏显示“基金优选”。
- 点击进入 `/fund-screener`。
- 旧策略配置页面未删除。

### 批次 2：全市场基础库同步

目标：

- 实现 `FundUniverseDataSourceAdapter`。
- 同步全市场基金基础信息到 `screener_fund_universe`。
- 支持手动触发 `/fund-screener/sync-universe`。
- 失败不清空旧数据。

验收：

- 数据库出现全市场基金基础信息。
- 重复执行幂等。
- 外部源失败时有错误日志，旧数据保留。

### 批次 3：有效池和净值同步

目标：

- 实现 `screener_universe_filter` 重建。
- 剔除不支持类型、短历史、小规模、重复份额、异常状态基金。
- 为 included 基金增量同步净值到 `screener_fund_nav_daily`。

验收：

- 有明确 included/exclude_reason。
- 净值按 `fund_code + nav_date` 幂等 upsert。
- 可以断点续跑。

### 批次 4：因子和评分

目标：

- 计算 `screener_factor_snapshot`。
- 计算 `screener_quality_score`。
- 生成排名、推荐等级、理由、风险提示。
- 提供 `/fund-screener/rank` 和 `/fund-screener/{fundCode}/explain`。

验收：

- 榜单可分页查询。
- 返回数据不实时扫全量净值表。
- 评分可解释。

### 批次 5：前端完整页面

目标：

- 实现基金优选页面。
- 支持筛选、排序、分页、加载、空状态、错误状态。
- 支持查看评分解释。
- 支持跳转基金详情。

验收：

- 页面不出现大面积空白。
- 表格在桌面宽度下可读。
- 文案正常 UTF-8 中文。
- 不展示“策略配置”主菜单入口。

### 批次 6：调度和回测

目标：

- 加独立每日调度。
- 加任务日志和配置开关。
- 初步实现 Top N 历史验证。

验收：

- 调度任务可关闭。
- 任务失败不影响旧榜单。
- 回测无未来函数。

## 测试要求

后端至少补充：

- `FundUniverseServiceImplTest`
- `FundScreenerNavServiceImplTest`
- `FundFactorServiceImplTest`
- `FundQualityScoreServiceImplTest`
- `FundScreenerControllerTest` 或服务层集成测试

重点测试：

- 重复同步幂等。
- 数据源失败保留旧数据。
- 样本不足基金被排除。
- C 类重复份额被排除。
- 最大回撤、波动率、收益计算正确。
- 评分范围在 0 到 100。
- 排名按基金类型分别生成。
- reasons/risks 不为空。

前端至少验证：

- TypeScript 构建通过。
- 页面能加载 mock 数据。
- 筛选参数能传到 API。
- 空状态、错误状态可见。
- 点击“查看详情”跳转正确。

建议命令：

```powershell
cd D:\code\personal\quant-fund\quant-fund-server
mvn.cmd "-Dmaven.repo.local=..\.m2\repository" test
```

如果上面的 Maven 本地仓库路径不适配当前 shell，请参考项目现有 README 或 CI 命令调整，但不要使用会清空用户本地仓库的破坏性命令。

前端：

```powershell
cd D:\code\personal\quant-fund\quant-fund-web
npm.cmd run build
```

## 验收标准

功能验收：

- 侧边栏主菜单显示“基金优选”，不再显示“策略配置”。
- `/fund-screener` 页面可访问。
- `/strategy-config` 旧页面没有被删除。
- 基金优选模块有独立 `screener_*` 表。
- 全市场基金基础库可同步。
- 有效评分池可重建。
- 优选净值可增量同步。
- 因子快照可生成。
- 评分榜单可查询。
- 单基金评分解释可查询。
- 榜单返回推荐理由、风险提示和免责声明。
- 优选模块异常不影响持仓、交易、收益分析、AI 分析。

质量验收：

- 后端测试通过。
- 前端构建通过。
- 新增页面中文无 mojibake。
- 不存在明显文本重叠或表格溢出。
- 不出现“未来收益确定”“稳赚”“保证收益”等表达。
- 外部数据源失败不会清空历史榜单。

## 输出要求

每个批次完成后，最终回复必须包含：

```text
【本批已完成】
【新增/修改文件】
【验证结果】
【尚未完成】
【下一批建议】
【风险与注意事项】
```

不要输出 API key、密码、token。只说明配置是否存在、是否启用。

## 明确禁止

- 禁止删除现有策略配置页面文件。
- 禁止把基金优选参数写入 `strategy_config`。
- 禁止把全市场净值直接灌入现有 `fund_nav_daily` 作为第一版主路径。
- 禁止修改真实交易相关能力。
- 禁止让优选模块失败影响现有业务。
- 禁止无分页地一次返回全市场所有评分结果。
- 禁止查询榜单时实时全量计算所有基金因子。
