# QuantFund 盘中量化决策引擎落地方案

> 面向后续 AI 编程代理的工程实施文档。目标是在现有 QuantFund 前后端基础上，引入 Python 量化引擎，在交易日 15:00 前输出可回测、可解释、可落库、可展示的加仓、减仓、持有、观察、止盈、止损建议。

## 1. 背景与目标

### 1.1 当前项目现状

当前仓库根目录：

```text
D:\code\personal\quant-fund
```

已有模块：

```text
quant-fund-server    Spring Boot 3 / Java 21 / MyBatis Plus / MySQL / Redis
quant-fund-web       Vue 3 / Vite / TypeScript / Pinia / Element Plus / ECharts
docs/sql             MySQL 建表脚本
docs/api             API 文档
tools                真实数据源 smoke 脚本
```

后端已经具备：

```text
用户登录/注册/鉴权
账户 portfolio_account
持仓 fund_holding
基金基础信息 fund_info
历史净值 fund_nav_daily
盘中估值 fund_estimate_intraday
持仓快照 holding_snapshot
盘中组合快照 portfolio_intraday_snapshot
模拟交易 trade_record
策略配置 strategy_config
策略信号 strategy_signal
AI 分析报告 ai_analysis_report
调度任务 QuantFundScheduler
东方财富真实数据源 EastMoneyFundDataSourceAdapter
```

前端已经具备：

```text
首页总览 DashboardView
持仓列表 HoldingsView
基金详情 FundDetailView
智能分析 AiAnalysisView
收益分析 ProfitAnalysisView
收益日历
模拟交易
策略配置
系统配置
```

现有策略层：

```text
com.lk.quantfund.strategy.QuantStrategyRule
com.lk.quantfund.strategy.context.StrategyEvaluationContext
com.lk.quantfund.strategy.context.StrategySignalDraft
com.lk.quantfund.service.impl.StrategyServiceImpl
```

现有 AI 分析层：

```text
com.lk.quantfund.service.impl.AiAnalysisServiceImpl
ai_analysis_report
```

### 1.2 现有 AI 分析的问题

当前 AI 分析更适合做解释、总结、风险提示，不适合作为核心买卖判断来源，原因：

```text
1. 大模型无法稳定保证量化一致性。
2. 大模型难以严格避免未来函数和数据泄露。
3. 大模型建议不可直接回测。
4. 同一输入可能产生不同措辞甚至不同建议。
5. 无法独立验证胜率、最大回撤、夏普比率等量化指标。
```

因此需要引入独立量化引擎：

```text
量化引擎负责判断
大模型负责解释
Java 负责业务闭环
Vue 负责展示和交互
```

### 1.3 最终目标

在交易日 15:00 前，系统能够自动或手动生成操作建议：

```text
BUY       加仓
SELL      减仓/止盈/止损
HOLD      持有
WATCH     观察
CONVERT   转换，暂不作为第一版重点
```

每条建议必须包含：

```text
基金代码
基金名称
建议动作
建议金额
建议比例
综合评分
趋势评分
风险评分
仓位评分
机会评分
置信度
风险等级
建议原因
风险提示
核心指标
模型版本
有效期，默认今日 15:00 前
免责声明
```

## 2. 总体技术路线

### 2.1 推荐路线

第一阶段不要直接上深度学习。当前项目最适合：

```text
规则多因子评分模型
  -> Python 回测验证
  -> Java 接入量化信号
  -> 前端展示
  -> 后续再引入 LightGBM/XGBoost
```

最终架构：

```text
Vue 前端
  |
  | HTTP
  v
Java Spring Boot 后端
  | 1. 聚合用户、持仓、净值、估值、板块、交易、风险配置
  | 2. 调用 Python Quant Engine
  | 3. 保存 quant_signal / strategy_signal / ai_analysis_report
  | 4. 提供 Dashboard / AI / Strategy API
  v
Python Quant Engine
  | 1. 特征工程
  | 2. 规则多因子评分
  | 3. 回测
  | 4. 后续 ML 训练和推理
  v
返回结构化量化建议 JSON
```

### 2.2 为什么选规则多因子 + LightGBM/XGBoost

适合当前数据结构：

```text
基金历史净值：日频时间序列，样本量有限
盘中估值：质量不稳定，适合做参考因子
持仓成本/收益/仓位：强业务特征
基金类型/板块/指数：类别特征
交易记录：可用于后续用户行为特征
```

不建议第一版上 LSTM/Transformer：

```text
基金样本少
日频数据噪声大
净值披露延迟
容易过拟合
解释成本高
回测复杂
```

第一版算法核心：

```text
综合评分 totalScore =
  trendScore      * 0.30
  opportunityScore* 0.20
  riskScore       * 0.20
  positionScore   * 0.20
  momentumScore   * 0.10
```

注意：

```text
riskScore 越高代表风险越低。
如果内部实现用 riskPenalty，则最终需要转换成正向分数。
```

### 2.3 当前本机性能方案

目标机器：

```text
Lenovo Legion Y9000P
CPU: Intel Core Ultra 9
Memory: 32 GB
Disk: 1 TB SSD
OS: Windows
```

这台机器足够支撑本项目的本地单机量化研发：

```text
1. 盘中实时建议：轻量，主要是 Java 聚合数据 + Python 单次推理。
2. 单基金回测：很轻，通常毫秒级到 1 秒内。
3. 100-500 只基金批量规则回测：可在本机完成，通常秒级到分钟级。
4. 1000+ 基金 * 多参数网格：建议异步任务 + 分批 + 结果落盘。
5. LightGBM/XGBoost 训练：可本机跑，建议离线任务，不要阻塞盘中建议。
```

本机资源分配建议：

```text
MySQL + Redis + Java 后端 + Vue 前端 + Python Quant Engine 同机运行。
Python 批量回测时保留 6-8 GB 给系统和 Java/MySQL。
Python 单任务内存预算建议 <= 16 GB。
批量回测并发 workers 建议从 6 开始，最高不要超过 CPU 性能核/逻辑核的一半到三分之二。
LightGBM/XGBoost n_jobs 建议 6-10，避免把桌面卡死。
```

本机优先采用“进程内批量计算”，不要一只基金一次 HTTP：

```text
推荐：
Java 一次传入多只基金数据 -> Python 一个 batch 请求内用 pandas/numpy 批量计算。

避免：
Java 对 500 只基金循环调用 500 次 /api/v1/quant/analyze。
```

性能目标：

```text
单持仓实时分析：P95 < 300 ms，不含 Java 查库时间。
账户 5-20 只持仓分析：P95 < 2 s。
100 只基金规则回测：目标 < 30 s。
500 只基金规则回测：目标 < 3 min。
1000 只基金 * 20 参数组合：异步执行，目标 < 30 min，允许后台运行。
```

如果实测超过目标，优先优化：

```text
1. 数据读取：批量查库，减少 N+1。
2. 数据格式：Java 批量传输，Python 一次解析。
3. 特征计算：pandas groupby/vectorization，避免 Python 双重 for 循环。
4. 缓存：历史净值特征按 fundCode + endDate + window 缓存。
5. 并发：ProcessPoolExecutor 用于多基金回测，线程池用于 I/O。
```

## 3. 目标业务流程

### 3.1 盘中自动建议流程

```text
交易日 09:30-15:00
  -> 后端按现有规则刷新基金盘中估值
  -> 后端重算持仓收益
  -> Java 聚合持仓分析上下文
  -> 调 Python Quant Engine
  -> Python 返回量化信号
  -> Java 保存信号
  -> Dashboard 展示最新建议
```

建议调度时间：

```text
09:45  早盘观察建议
10:30  早盘中段更新
11:20  午前风险检查
13:30  午后趋势确认
14:30  尾盘预建议
14:50  今日正式操作建议
14:55  最终提醒
```

### 3.2 手动生成建议流程

```text
用户点击基金详情/智能分析页的“生成量化建议”
  -> Java 校验 holdingId 属于当前用户
  -> Java 补齐历史净值、估值、持仓、账户、风险配置
  -> Java POST Python /api/v1/quant/analyze
  -> Python 返回 QuantSignalResponse
  -> Java 保存 quant_signal
  -> Java 同步生成 strategy_signal
  -> Java 可选调用 AI 文案层生成 ai_analysis_report
  -> 前端展示
```

### 3.3 15:00 规则

```text
14:30 前：偏观察，不轻易给强买卖建议
14:30-14:50：生成尾盘预建议
14:50-14:57：生成最终操作建议
14:57 后：原则上不再建议买入，只提示复盘或次日计划
15:00 后：不生成“今日买入/卖出”建议，可生成复盘建议
```

实现时需要在 Java 和 Python 两侧都传入/识别：

```text
marketSession
decisionPhase
deadline
```

## 4. 新增 Python Quant Engine

### 4.1 新增目录

在仓库根目录新增：

```text
quant-engine/
  README.md
  pyproject.toml
  requirements.txt
  .env.example
  app/
    __init__.py
    main.py
    core/
      config.py
      logging.py
      schemas.py
      errors.py
    api/
      __init__.py
      health.py
      inference.py
      backtest.py
    features/
      __init__.py
      nav_features.py
      risk_features.py
      position_features.py
      market_features.py
      feature_builder.py
    strategies/
      __init__.py
      rule_model.py
      scoring.py
      action_mapper.py
      constraints.py
    backtest/
      __init__.py
      engine.py
      metrics.py
      simulator.py
    models/
      __init__.py
      train_lgbm.py
      predict.py
      registry.py
    tests/
      test_feature_builder.py
      test_rule_model.py
      test_action_mapper.py
      test_backtest_metrics.py
```

第一版只必须实现：

```text
app/main.py
app/api/health.py
app/api/inference.py
app/core/schemas.py
app/features/*
app/strategies/*
app/tests/*
```

### 4.2 Python 依赖

第一版先安装最小可运行依赖，降低 Windows 本地环境搭建失败概率。`quant-engine/requirements.txt`：

```text
fastapi>=0.115.0
uvicorn[standard]>=0.30.0
pydantic>=2.7.0
pydantic-settings>=2.3.0
numpy>=1.26.0
pandas>=2.2.0
pytest>=8.2.0
httpx>=0.27.0
python-dotenv>=1.0.1
joblib>=1.4.0
orjson>=3.10.0
```

后续回测、缓存、机器学习阶段再按需增加：

```text
scipy>=1.13.0
scikit-learn>=1.5.0
lightgbm>=4.3.0
xgboost>=2.0.0
pyarrow>=16.0.0
```

LightGBM/XGBoost 第一版不要启用，也不要作为 Phase 1 的验收前置条件。

依赖说明：

```text
joblib: 本地缓存和并行任务辅助。
pyarrow: 后续可把大批量历史净值缓存为 parquet，加速批量回测。
orjson: 大 JSON 请求响应序列化优化，可选。
```

### 4.3 Python 服务配置

`quant-engine/.env.example`：

```text
QUANT_ENGINE_HOST=127.0.0.1
QUANT_ENGINE_PORT=8091
QUANT_ENGINE_RULE_MODEL_VERSION=rule-v1.0.0
QUANT_ENGINE_DEFAULT_DEADLINE=15:00:00
QUANT_ENGINE_LOG_LEVEL=INFO
QUANT_ENGINE_WORKERS=1
QUANT_ENGINE_BACKTEST_WORKERS=6
QUANT_ENGINE_MAX_BATCH_FUNDS=500
QUANT_ENGINE_MAX_PARAM_GRID=100
QUANT_ENGINE_CACHE_DIR=.cache
QUANT_ENGINE_ENABLE_PARQUET_CACHE=true
QUANT_ENGINE_LGBM_N_JOBS=8
```

启动命令：

```bash
cd quant-engine
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8091 --reload
```

本机运行建议：

```text
开发调试：uvicorn app.main:app --host 127.0.0.1 --port 8091 --reload
盘中使用：uvicorn app.main:app --host 127.0.0.1 --port 8091 --workers 1
批量回测：不要开多个 uvicorn workers，使用服务内部 ProcessPoolExecutor 控制并行。
```

为什么 `QUANT_ENGINE_WORKERS=1`：

```text
1. 本项目先是本地单用户使用，HTTP 并发压力低。
2. 批量回测主要是 CPU 任务，应在任务层并行，而不是开多个 Web worker 抢内存。
3. 多 worker 会复制模型和缓存，32 GB 内存下不如单 worker + 任务池可控。
```

Python 配置类要求：

```python
class Settings(BaseSettings):
    host: str = "127.0.0.1"
    port: int = 8091
    rule_model_version: str = "rule-v1.0.0"
    default_deadline: str = "15:00:00"
    log_level: str = "INFO"
    backtest_workers: int = 6
    max_batch_funds: int = 500
    max_param_grid: int = 100
    cache_dir: str = ".cache"
    enable_parquet_cache: bool = True
    lgbm_n_jobs: int = 8
```

缓存目录：

```text
quant-engine/.cache/
  features/
  backtest/
  parquet/
  models/
```

`.gitignore` 必须忽略：

```text
quant-engine/.venv/
quant-engine/.cache/
quant-engine/models/artifacts/
```

### 4.4 Python API

#### 4.4.1 健康检查

```http
GET /api/v1/health
```

响应：

```json
{
  "status": "UP",
  "service": "quant-engine",
  "modelVersion": "rule-v1.0.0"
}
```

#### 4.4.2 单持仓量化分析

```http
POST /api/v1/quant/analyze
```

请求结构：

```json
{
  "requestId": "qf-20260628-145000-1001",
  "userId": 1,
  "account": {
    "accountId": 1,
    "totalAsset": 4434.72,
    "totalInvestAmount": 3915.99,
    "currentProfit": 518.73,
    "currentProfitRate": 13.25,
    "dailyProfit": 0,
    "equityPositionRate": 86.0,
    "maxSingleFundPositionRate": 30.35
  },
  "riskProfile": {
    "riskLevel": "MEDIUM",
    "maxEquityPositionRate": 70,
    "maxSingleFundPositionRate": 25,
    "drawdownAlertRate": 8,
    "dailyRiseAlertRate": 2,
    "dailyFallAlertRate": 2
  },
  "holding": {
    "holdingId": 1001,
    "fundCode": "025833",
    "fundName": "天弘电网设备特高压指数C",
    "fundType": "INDEX",
    "activeFund": false,
    "holdingAmount": 1050.0,
    "holdingShare": 1000.0,
    "holdingCost": 956.49,
    "holdingProfit": 93.51,
    "holdingProfitRate": 9.77,
    "dailyProfit": 0,
    "positionRate": 23.68,
    "currentEstimateNav": null,
    "latestOfficialNav": 1.05,
    "currentEstimateGrowthRate": 0,
    "relatedThemeName": "中证电网设备",
    "relatedThemeRate": 0,
    "marketStatus": "已收盘",
    "holdingDays": 30,
    "coreHolding": false,
    "watchFocus": true
  },
  "navSeries": [
    {
      "date": "2026-06-20",
      "nav": 1.0234,
      "accumulatedNav": 1.0234,
      "dailyGrowthRate": 0.34
    }
  ],
  "tradeRecords": [
    {
      "tradeType": "BUY",
      "tradeAmount": 500,
      "tradeShare": 500,
      "tradeNav": 1.0,
      "tradeTime": "2026-06-01 14:30:00"
    }
  ],
  "market": {
    "tradingDay": true,
    "trading": true,
    "decisionPhase": "FINAL_DECISION",
    "now": "2026-06-28 14:50:00",
    "deadline": "2026-06-28 15:00:00"
  }
}
```

响应结构：

```json
{
  "requestId": "qf-20260628-145000-1001",
  "fundCode": "025833",
  "holdingId": 1001,
  "action": "HOLD",
  "actionText": "建议持有观察",
  "suggestAmount": 0,
  "suggestRatio": 0,
  "confidence": 0.72,
  "riskLevel": "MEDIUM",
  "score": {
    "totalScore": 64.5,
    "trendScore": 58.0,
    "opportunityScore": 45.0,
    "riskScore": 70.0,
    "positionScore": 52.0,
    "momentumScore": 50.0
  },
  "metrics": {
    "return5d": -0.8,
    "return20d": 2.1,
    "return60d": 7.2,
    "volatility20d": 18.2,
    "maxDrawdown60d": -6.4,
    "ma5": 1.02,
    "ma20": 1.01,
    "ma60": 0.98,
    "ma20Deviation": 1.2,
    "consecutiveDownDays": 2
  },
  "reasons": [
    "当前持仓收益率 9.77%，已有一定盈利垫",
    "单基金仓位 23.68%，接近风险配置上限 25.00%",
    "近20日趋势中性，未触发强加仓条件"
  ],
  "risks": [
    "电网设备主题波动较高，不宜单次大额加仓",
    "建议以 15:00 前人工确认平台净值和交易规则为准"
  ],
  "modelName": "QuantRuleEngine",
  "modelVersion": "rule-v1.0.0",
  "deadline": "2026-06-28 15:00:00",
  "disclaimer": "仅供参考，不构成投资建议，不承诺收益"
}
```

#### 4.4.3 批量持仓分析

```http
POST /api/v1/quant/analyze-batch
```

请求：

```json
{
  "requestId": "qf-batch-20260628-145000-user1",
  "items": [
    {
      "...": "同 /api/v1/quant/analyze 单条请求"
    }
  ]
}
```

响应：

```json
{
  "requestId": "qf-batch-20260628-145000-user1",
  "results": [
    {
      "...": "同单条响应"
    }
  ],
  "successCount": 5,
  "failedCount": 0
}
```

### 4.5 Python 特征工程

#### 4.5.1 净值特征

输入：

```text
navSeries 按日期升序
字段：date, nav, accumulatedNav, dailyGrowthRate
```

输出：

```text
return1d
return3d
return5d
return10d
return20d
return60d
return120d
ma5
ma10
ma20
ma60
ma120
ma5Deviation
ma20Deviation
ma60Deviation
trendSlope20d
trendSlope60d
consecutiveUpDays
consecutiveDownDays
```

计算要求：

```text
收益率 = (latestNav / previousNav - 1) * 100
MA = 最近 N 个交易日 unit_nav 均值
MA 偏离 = (latestNav / MA - 1) * 100
trendSlope 可以用最近 N 日 nav 对序号做线性回归斜率，再除以均值转成百分比
```

#### 4.5.2 风险特征

输出：

```text
volatility5d
volatility20d
volatility60d
maxDrawdown20d
maxDrawdown60d
maxDrawdown120d
downsideVolatility20d
lossDayRatio20d
```

计算要求：

```text
日收益序列 = nav.pct_change()
波动率 = std(日收益) * sqrt(252) * 100
最大回撤 = min(nav / rolling_max(nav) - 1) * 100
lossDayRatio20d = 最近20日收益 < 0 的天数 / 可用天数
```

#### 4.5.3 仓位特征

输出：

```text
positionRate
positionToSingleLimit
equityPositionToLimit
profitBuffer
lossPressure
canBuyMore
shouldReduceByPosition
```

计算要求：

```text
positionToSingleLimit = positionRate / maxSingleFundPositionRate
equityPositionToLimit = equityPositionRate / maxEquityPositionRate
profitBuffer = max(holdingProfitRate, 0)
lossPressure = abs(min(holdingProfitRate, 0))
```

#### 4.5.4 市场动能特征

输出：

```text
themeRate
estimateGrowthRate
intradayMomentum
marketTrading
decisionPhase
```

注意：

```text
QDII/海外基金如果不是对应市场可参考窗口，不应把 A 股盘中波动当作当日买卖依据。
非盘中展示窗口下，如果 Java 已经传 relatedThemeRate = 0，则 Python 不要重新制造盘中涨跌。
```

### 4.6 规则评分算法

#### 4.6.1 trendScore

范围 0-100。

参考规则：

```text
初始 50
return20d > 3%      +12
return20d > 8%      +8
return60d > 5%      +10
latestNav > ma20    +10
ma20 > ma60         +8
trendSlope20d > 0   +8
return5d < -3%      -10
latestNav < ma20    -10
ma20 < ma60         -8
consecutiveDownDays >= 4 -8
最后 clamp 到 0-100
```

#### 4.6.2 opportunityScore

衡量是否适合低吸或加仓。

```text
初始 50
maxDrawdown60d <= -5% 且 return20d 开始回升 +15
ma20Deviation 在 -5% 到 -1% 之间 +10
holdingProfitRate < 0 且风险不高 +5
return5d 暴跌超过 -5% 但 ma60 仍向上 +8
maxDrawdown60d < -15% -15
趋势破位 latestNav < ma60 -10
```

#### 4.6.3 riskScore

风险越低分越高。

```text
初始 80
volatility20d > 25%       -15
volatility20d > 40%       -15
maxDrawdown60d < -8%      -10
maxDrawdown60d < -15%     -15
lossDayRatio20d > 0.6     -10
QDII 且当前不是海外市场可参考窗口 -5
历史净值点少于 40 个 -10
最后 clamp 到 0-100
```

#### 4.6.4 positionScore

仓位越健康分越高。

```text
初始 80
positionRate > maxSingleFundPositionRate * 0.9  -25
positionRate > maxSingleFundPositionRate        -35
equityPositionRate > maxEquityPositionRate       -25
holdingProfitRate > 25% 且 positionRate 高       -10
positionRate < maxSingleFundPositionRate * 0.4   +8
```

#### 4.6.5 momentumScore

```text
初始 50
themeRate > 1%                  +10
themeRate > 2%                  +10
estimateGrowthRate > 1%          +8
themeRate < -1%                 -10
themeRate < -2%                 -10
非盘中窗口或无有效估值             0，不加不减
```

#### 4.6.6 totalScore

```text
totalScore =
  trendScore * 0.30
  + opportunityScore * 0.20
  + riskScore * 0.20
  + positionScore * 0.20
  + momentumScore * 0.10
```

### 4.7 动作映射规则

硬约束优先于总分：

```text
如果 positionRate > maxSingleFundPositionRate:
  不允许 BUY
  若 trendScore < 55 或 riskScore < 60，则 REDUCE

如果 equityPositionRate > maxEquityPositionRate:
  不允许 BUY

如果 maxDrawdown60d < -15 且 latestNav < ma60:
  SELL 或 WATCH，视持仓收益率和风险等级而定

如果 holdingProfitRate > 25 且 trendScore 下滑:
  SELL，建议分批止盈

如果 14:57 后:
  不给 BUY，只给 HOLD/WATCH/SELL
```

默认映射：

```text
totalScore >= 80 且 positionScore >= 65 且 riskScore >= 60:
  BUY

totalScore >= 65:
  HOLD

totalScore >= 50:
  WATCH

totalScore >= 35:
  SELL，actionText = 建议小幅减仓

totalScore < 35:
  SELL，actionText = 风险较高，建议减仓或止损
```

建议比例：

```text
BUY:
  base = 5%
  totalScore >= 90 -> 10%
  positionRate 已接近上限 -> 降为 3%

SELL:
  base = 5%
  riskScore < 40 -> 10%
  holdingProfitRate > 25 -> 10%-20% 分批止盈
```

建议金额：

```text
BUY suggestAmount =
  min(account.totalAsset * suggestRatio / 100,
      max(0, account.totalAsset * maxSingleFundPositionRate / 100 - holding.holdingAmount))

SELL suggestAmount =
  holding.holdingAmount * suggestRatio / 100
```

第一版全部四舍五入到 2 位。

## 5. Java 后端接入方案

### 5.1 新增配置

修改：

```text
quant-fund-server/src/main/resources/application.yml
quant-fund-server/src/main/java/com/lk/quantfund/config/QuantFundProperties.java
```

新增配置：

```yaml
quantfund:
  quant-engine:
    enabled: ${QUANT_ENGINE_ENABLED:true}
    base-url: ${QUANT_ENGINE_BASE_URL:http://127.0.0.1:8091}
    timeout-ms: ${QUANT_ENGINE_TIMEOUT_MS:8000}
    batch-timeout-ms: ${QUANT_ENGINE_BATCH_TIMEOUT_MS:60000}
    backtest-timeout-ms: ${QUANT_ENGINE_BACKTEST_TIMEOUT_MS:300000}
    model-version: ${QUANT_ENGINE_MODEL_VERSION:rule-v1.0.0}
    fallback-to-java-rules: ${QUANT_ENGINE_FALLBACK_TO_JAVA_RULES:true}
    decision-deadline: ${QUANT_ENGINE_DECISION_DEADLINE:15:00:00}
    max-batch-holdings: ${QUANT_ENGINE_MAX_BATCH_HOLDINGS:100}
    max-backtest-funds: ${QUANT_ENGINE_MAX_BACKTEST_FUNDS:1000}
    max-param-grid: ${QUANT_ENGINE_MAX_PARAM_GRID:100}
```

`QuantFundProperties` 增加内部类：

```java
private QuantEngine quantEngine = new QuantEngine();

public static class QuantEngine {
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8091";
    private int timeoutMs = 8000;
    private int batchTimeoutMs = 60000;
    private int backtestTimeoutMs = 300000;
    private String modelVersion = "rule-v1.0.0";
    private boolean fallbackToJavaRules = true;
    private String decisionDeadline = "15:00:00";
    private int maxBatchHoldings = 100;
    private int maxBacktestFunds = 1000;
    private int maxParamGrid = 100;
    // getters setters
}
```

超时策略：

```text
实时单持仓分析：timeout-ms = 8 秒。
账户批量建议：batch-timeout-ms = 60 秒。
单次回测：backtest-timeout-ms = 300 秒。
大批量回测不要同步等待 HTTP 完成，应做异步任务。
```

### 5.2 新增 Java DTO

新增包：

```text
quant-fund-server/src/main/java/com/lk/quantfund/dto/quant
```

文件：

```text
QuantAnalyzeRequest.java
QuantAnalyzeBatchRequest.java
QuantAnalyzeResponse.java
QuantScoreDTO.java
QuantMetricsDTO.java
QuantAccountContextDTO.java
QuantRiskProfileDTO.java
QuantHoldingContextDTO.java
QuantNavPointDTO.java
QuantTradeDTO.java
QuantMarketContextDTO.java
```

字段按 Python API 契约实现。

注意：

```text
BigDecimal 不要用 double。
LocalDate/LocalDateTime 用 Jackson 默认 ISO 或现有全局格式。
请求给 Python 时可以转为字符串或让 Jackson 处理。
```

### 5.3 新增 Java VO

新增：

```text
quant-fund-server/src/main/java/com/lk/quantfund/vo/quant
```

文件：

```text
QuantSignalVO.java
QuantScoreVO.java
QuantMetricsVO.java
QuantEngineHealthVO.java
```

`QuantSignalVO` 字段：

```java
Long id;
Long accountId;
Long holdingId;
String fundCode;
String fundName;
String action;
String actionText;
BigDecimal suggestAmount;
BigDecimal suggestRatio;
String riskLevel;
BigDecimal confidence;
BigDecimal totalScore;
BigDecimal trendScore;
BigDecimal opportunityScore;
BigDecimal riskScore;
BigDecimal positionScore;
BigDecimal momentumScore;
List<String> reasons;
List<String> risks;
String metricsJson;
String modelName;
String modelVersion;
String deadline;
LocalDateTime signalTime;
String disclaimer;
```

### 5.4 新增数据库表

新建迁移：

```text
docs/sql/006_quant_engine.sql
```

SQL：

```sql
CREATE TABLE IF NOT EXISTS quant_signal (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'Owner user id',
  account_id BIGINT DEFAULT NULL COMMENT 'Portfolio account id',
  holding_id BIGINT DEFAULT NULL COMMENT 'Fund holding id',
  fund_code VARCHAR(32) NOT NULL COMMENT 'Fund code',
  fund_name VARCHAR(128) NOT NULL COMMENT 'Fund name',
  action VARCHAR(32) NOT NULL COMMENT 'BUY, SELL, HOLD, WATCH, CONVERT',
  action_text VARCHAR(128) NOT NULL COMMENT 'Action display text',
  suggest_amount DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT 'Suggested amount',
  suggest_ratio DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Suggested ratio percentage',
  risk_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM' COMMENT 'Risk level',
  confidence DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Confidence 0-1',
  total_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Total quant score 0-100',
  trend_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Trend score',
  opportunity_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Opportunity score',
  risk_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Risk score',
  position_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Position score',
  momentum_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Momentum score',
  metrics_json JSON NOT NULL COMMENT 'Quant metrics JSON',
  reasons_json JSON NOT NULL COMMENT 'Reasons JSON',
  risks_json JSON NOT NULL COMMENT 'Risks JSON',
  model_name VARCHAR(128) NOT NULL DEFAULT 'QuantRuleEngine' COMMENT 'Model name',
  model_version VARCHAR(64) NOT NULL COMMENT 'Model version',
  trade_date DATE NOT NULL COMMENT 'Trading date of the signal',
  decision_phase VARCHAR(32) NOT NULL COMMENT 'Decision phase: MORNING, PRE_DECISION, FINAL_DECISION, CLOSED',
  request_payload JSON DEFAULT NULL COMMENT 'Request sent to quant engine',
  response_payload JSON DEFAULT NULL COMMENT 'Response from quant engine',
  fallback_used TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether Java fallback was used',
  deadline DATETIME(3) DEFAULT NULL COMMENT 'Signal deadline',
  signal_time DATETIME(3) NOT NULL COMMENT 'Signal time',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Create time',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Update time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (id),
  KEY idx_quant_signal_user_time (user_id, signal_time),
  KEY idx_quant_signal_holding_time (holding_id, signal_time),
  KEY idx_quant_signal_fund_code (fund_code),
  KEY idx_quant_signal_action (action),
  KEY idx_quant_signal_model_version (model_version),
  UNIQUE KEY uk_quant_signal_idempotent (user_id, holding_id, trade_date, decision_phase, model_version, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quant engine signal';

CREATE TABLE IF NOT EXISTS quant_backtest_result (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  strategy_name VARCHAR(128) NOT NULL COMMENT 'Strategy name',
  model_version VARCHAR(64) NOT NULL COMMENT 'Model version',
  fund_code VARCHAR(32) DEFAULT NULL COMMENT 'Fund code',
  fund_name VARCHAR(128) DEFAULT NULL COMMENT 'Fund name',
  fund_type VARCHAR(32) DEFAULT NULL COMMENT 'Fund type',
  start_date DATE NOT NULL COMMENT 'Backtest start date',
  end_date DATE NOT NULL COMMENT 'Backtest end date',
  benchmark_code VARCHAR(32) DEFAULT NULL COMMENT 'Benchmark code',
  total_return_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Total return percentage',
  annual_return_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Annual return percentage',
  max_drawdown_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Max drawdown percentage',
  win_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Win rate percentage',
  sharpe_ratio DECIMAL(10,4) DEFAULT NULL COMMENT 'Sharpe ratio',
  calmar_ratio DECIMAL(10,4) DEFAULT NULL COMMENT 'Calmar ratio',
  trade_count INT NOT NULL DEFAULT 0 COMMENT 'Trade count',
  result_json JSON NOT NULL COMMENT 'Detailed backtest result',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Create time',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Update time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (id),
  KEY idx_quant_backtest_strategy (strategy_name, model_version),
  KEY idx_quant_backtest_fund_code (fund_code),
  KEY idx_quant_backtest_date (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quant backtest result';
```

### 5.5 新增实体和 Mapper

新增：

```text
entity/QuantSignal.java
entity/QuantBacktestResult.java
mapper/QuantSignalMapper.java
mapper/QuantBacktestResultMapper.java
```

实体风格参考：

```text
entity/StrategySignal.java
entity/AiAnalysisReport.java
```

必须包含：

```text
@TableName
@TableId(type = IdType.AUTO)
@TableLogic deleted
getter/setter
```

### 5.6 新增 QuantEngineClient

新增包：

```text
quant-fund-server/src/main/java/com/lk/quantfund/quant
```

文件：

```text
QuantEngineClient.java
QuantEngineClientImpl.java
QuantEngineException.java
```

接口：

```java
public interface QuantEngineClient {
    QuantAnalyzeResponse analyze(QuantAnalyzeRequest request);
    List<QuantAnalyzeResponse> analyzeBatch(List<QuantAnalyzeRequest> requests);
    QuantEngineHealthVO health();
}
```

实现要求：

```text
使用 WebClient。
baseUrl 从 quantfund.quant-engine.base-url 获取。
timeout 使用 quantfund.quant-engine.timeout-ms。
调用失败抛 QuantEngineException。
记录 api_call_log，provider = QUANT_ENGINE，apiName = quant_analyze / quant_analyze_batch。
```

### 5.7 新增 QuantAnalysisService

新增：

```text
service/QuantAnalysisService.java
service/impl/QuantAnalysisServiceImpl.java
```

接口：

```java
QuantSignalVO analyzeHolding(Long holdingId);
List<QuantSignalVO> analyzeAccount(Long accountId);
List<QuantSignalVO> latestSignals(Long accountId, Long holdingId, String fundCode, StrategyAction action);
QuantEngineHealthVO health();
```

核心职责：

```text
1. 加载当前用户持仓、账户、风险配置。
2. 加载最近 120-260 个交易日 fund_nav_daily。
3. 加载最近交易记录 trade_record。
4. 组装 QuantAnalyzeRequest。
5. 调用 Python QuantEngineClient。
6. 如果失败且 fallbackToJavaRules=true，则调用本地 Java 简易规则 fallback。
7. 保存 quant_signal。
8. 同步生成或更新 strategy_signal，便于现有 Dashboard 复用。
9. 可选生成 ai_analysis_report，或只在用户点击 AI 分析时再生成。
```

### 5.8 上下文组装规则

`QuantAnalysisServiceImpl.buildRequest(...)` 必须包含：

```text
account:
  portfolio_account 当前字段

riskProfile:
  risk_profile 当前字段

holding:
  fund_holding 当前字段
  positionRate 可复用 FundHoldingVO 或自己计算
  relatedThemeName / relatedThemeRate 可复用 FundValuationService

navSeries:
  fund_nav_daily 最近 260 条，按 nav_date 升序

tradeRecords:
  trade_record 当前 holdingId 最近 180 天或最近 50 条，按 trade_time 升序

market:
  tradingDay = TradingCalendarService.isTradingDay(LocalDate.now())
  trading = TradingCalendarService.isIntradayEstimateWindow(LocalDateTime.now())
  decisionPhase = 根据当前时间生成
```

decisionPhase：

```java
BEFORE_OPEN       < 09:30
MORNING          09:30-11:30
MIDDAY_BREAK     11:30-13:00
AFTERNOON        13:00-14:30
PRE_DECISION     14:30-14:50
FINAL_DECISION   14:50-14:57
CLOSED           >= 15:00
```

### 5.9 保存 quant_signal

保存策略：

```text
quant_signal 是量化结果主表，必须保存完整评分、指标、原因、风险、模型版本和请求/响应快照。
同一用户、同一 holdingId、同一 modelVersion、同一交易日、同一 decisionPhase，使用唯一键做幂等 upsert，避免反复点击或调度刷屏。
如果需要保留 action 变化历史，后续可以新增 quant_signal_revision；第一版不要绕开幂等约束反复 insert。
```

第一版简化：

```text
手动 analyzeHolding 和调度任务都复用同一套保存逻辑。
Dashboard 查询时按 holdingId 取当天最新，优先 FINAL_DECISION，其次 PRE_DECISION，再其次其他阶段。
```

### 5.10 同步 strategy_signal

为了不大改现有 Dashboard，第一版可以把量化结果同步一条摘要到现有 `strategy_signal`。注意：`strategy_signal` 只是兼容展示摘要，不是量化结果主表，完整信息必须以 `quant_signal` 为准。

```text
signal_type = "QUANT_MODEL"
action = response.action
action_text = response.actionText
suggest_amount = response.suggestAmount
suggest_ratio = response.suggestRatio
risk_level = response.riskLevel
confidence = response.confidence
reason_json = response.reasons
signal_time = now
```

注意：

当前 `SignalType` 枚举没有 `QUANT_MODEL`，如果选择同步到 `strategy_signal`，必须新增该枚举值，并确认现有策略列表、Dashboard 和 AI 分析不会把它误当成原规则策略。

如果不想改枚举，也可以第一版只展示 `quant_signal`，暂不同步 `strategy_signal`。二选一即可，不要同时实现两套互相竞争的展示来源。

文件：

```text
quant-fund-server/src/main/java/com/lk/quantfund/enums/SignalType.java
```

新增：

```java
QUANT_MODEL
```

### 5.11 Java fallback 规则

当 Python 不可用时，Java fallback 不要复杂，只保证系统可用：

```text
如果 holding.positionRate > maxSingleFundPositionRate:
  SELL，建议减仓 5%，confidence 0.55

如果 holdingProfitRate > 25:
  SELL，建议分批止盈 10%，confidence 0.60

如果 currentDrawdownRate > drawdownAlertRate:
  WATCH，confidence 0.55

否则:
  HOLD，confidence 0.50
```

fallback 的 quant_signal：

```text
modelName = JavaFallbackRule
modelVersion = fallback-v1.0.0
fallbackUsed = 1
```

## 6. Java API 设计

### 6.1 新增 Controller

新增：

```text
controller/QuantAnalysisController.java
```

路径：

```text
/api/quant
```

接口：

```http
GET /api/quant/health
POST /api/quant/holdings/{holdingId}/analyze
POST /api/quant/accounts/{accountId}/analyze
GET /api/quant/signals
```

示例：

```java
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/quant")
public class QuantAnalysisController {
    @GetMapping("/health")
    public ApiResponse<QuantEngineHealthVO> health() {}

    @PostMapping("/holdings/{holdingId}/analyze")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "holdingId", operation = DataOperation.READ)
    @RateLimit(key = "quant:holding-analyze", windowSeconds = 60, maxRequests = 30)
    public ApiResponse<QuantSignalVO> analyzeHolding(@PathVariable Long holdingId) {}

    @PostMapping("/accounts/{accountId}/analyze")
    @DataScope(resourceType = ResourceType.PORTFOLIO_ACCOUNT, idParam = "accountId", operation = DataOperation.READ)
    @RateLimit(key = "quant:account-analyze", windowSeconds = 60, maxRequests = 10)
    public ApiResponse<List<QuantSignalVO>> analyzeAccount(@PathVariable Long accountId) {}

    @GetMapping("/signals")
    @RateLimit(key = "quant:signals", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<QuantSignalVO>> signals(...) {}
}
```

### 6.2 接入现有 AI 分析

不要让 AI 直接做买卖判断。修改思路：

```text
AiAnalysisServiceImpl.analyzeHolding
  -> 先查询当天最新 quant_signal
  -> 如果不存在，调用 QuantAnalysisService.analyzeHolding
  -> 把 quant_signal 的 action/score/metrics/reasons 放入 AI prompt
  -> AI 只负责生成 explanation，不覆盖 action
```

prompt 约束：

```text
必须基于量化引擎结果解释，不得自行改变建议动作。
必须保留风险提示。
```

## 7. 调度任务接入

### 7.1 修改 QuantFundScheduler

现有文件：

```text
quant-fund-server/src/main/java/com/lk/quantfund/scheduler/QuantFundScheduler.java
```

新增调度：

```java
@Scheduled(cron = "0 45 9 ? * MON-FRI", zone = ZONE)
@Scheduled(cron = "0 30 10 ? * MON-FRI", zone = ZONE)
@Scheduled(cron = "0 20 11 ? * MON-FRI", zone = ZONE)
@Scheduled(cron = "0 30 13 ? * MON-FRI", zone = ZONE)
@Scheduled(cron = "0 30 14 ? * MON-FRI", zone = ZONE)
@Scheduled(cron = "0 50 14 ? * MON-FRI", zone = ZONE)
@Scheduled(cron = "0 55 14 ? * MON-FRI", zone = ZONE)
public void generateQuantSignals() {
    runTradingTask("GENERATE_QUANT_SIGNALS", scheduledFundTaskService::generateQuantSignals);
}
```

### 7.2 修改 ScheduledFundTaskService

新增方法：

```java
public SchedulerTaskResult generateQuantSignals()
```

逻辑：

```text
1. 查询所有 enabled 用户的持仓。
2. 优先 watch_focus = 1 或 core_holding = 1。
3. 每个用户每次最多分析 N 只，默认 50。
4. 调 QuantAnalysisService.analyzeHoldingForUser(userId, holdingId)。
5. 统计 success/failure/skipped。
6. 写 scheduler_task_log。
```

新增配置：

```yaml
quantfund:
  scheduler:
    quant-focus-holding-limit: ${QUANTFUND_SCHEDULER_QUANT_FOCUS_HOLDING_LIMIT:50}
```

## 8. 前端接入方案

### 8.1 TypeScript 类型

修改：

```text
quant-fund-web/src/types/domain.ts
```

新增：

```ts
export interface QuantScore {
  totalScore: number
  trendScore: number
  opportunityScore: number
  riskScore: number
  positionScore: number
  momentumScore: number
}

export interface QuantSignal {
  id: number
  accountId: number
  holdingId: number
  fundCode: string
  fundName: string
  action: StrategyAction
  actionText: string
  suggestAmount: number
  suggestRatio: number
  riskLevel: RiskLevel
  confidence: number
  totalScore: number
  trendScore: number
  opportunityScore: number
  riskScore: number
  positionScore: number
  momentumScore: number
  metricsJson: string
  reasons: string[]
  risks: string[]
  modelName: string
  modelVersion: string
  deadline: string
  signalTime: string
  disclaimer: string
}
```

### 8.2 API

修改：

```text
quant-fund-web/src/api/quant.ts
```

新增：

```ts
quantHealth(): Promise<QuantEngineHealth>
quantAnalyzeHolding(holdingId: number): Promise<QuantSignal>
quantAnalyzeAccount(accountId: number): Promise<QuantSignal[]>
quantSignals(query?: { accountId?: number; holdingId?: number; fundCode?: string; action?: StrategyAction }): Promise<QuantSignal[]>
```

路径：

```text
GET  /quant/health
POST /quant/holdings/{holdingId}/analyze
POST /quant/accounts/{accountId}/analyze
GET  /quant/signals
```

### 8.3 页面改造

#### 8.3.1 DashboardView

当前 Dashboard 已展示 `latestStrategySignals`。第一版可以继续复用。

增强建议：

```text
1. 在首页增加“今日量化建议”面板。
2. 从 /api/quant/signals 拉取当天最新信号。
3. 如果没有信号，显示空状态和“生成量化建议”按钮。
4. 强调最终建议有效期 15:00 前。
```

卡片字段：

```text
基金名称/代码
动作标签
建议金额/比例
综合评分
置信度
风险等级
生成时间
原因前 2 条
```

#### 8.3.2 AiAnalysisView

改名或新增 tab：

```text
智能分析
  - 量化建议
  - AI 解读
```

量化建议 tab：

```text
左侧：持仓列表
右侧：量化评分雷达/进度条
底部：原因、风险、指标 JSON 展开
```

#### 8.3.3 FundDetailView

新增按钮：

```text
生成量化建议
```

点击：

```text
quantApi.quantAnalyzeHolding(holding.id)
成功后展示 QuantSignal 卡片
```

#### 8.3.4 StrategyConfigView

新增或复用配置：

```text
量化模型开关
尾盘最终建议时间
最大单基金建议加仓比例
最大单次减仓比例
是否启用 Python 引擎
Python 不可用时是否使用 Java fallback
```

第一版可以先不做 UI 配置，只走后端 application.yml。

### 8.4 UI 表达规范

动作颜色：

```text
BUY    红色/上涨色
SELL   绿色/风险/减仓色，注意项目现有红涨绿跌口径
HOLD   蓝色/中性
WATCH  黄色/观察
```

## 9. 回测模块

### 9.1 Python 回测目标

第一版回测在 Python 内实现，可以先通过 API 和脚本使用，前端页面作为第二步。

功能：

```text
输入基金历史净值 + 策略参数
按日模拟生成信号
按信号模拟买卖
输出收益、回撤、胜率、夏普等指标
```

必须支持四种模式：

```text
1. 单基金回测
   用于调试某只基金的策略表现。

2. 批量基金回测
   一键测试几十到几百只基金，判断策略泛化能力。

3. 参数网格回测
   例如 buyThreshold = 75/80/85，sellThreshold = 35/40/45 全组合测试。

4. 滚动窗口回测
   例如 2021-2022、2022-2023、2023-2024、2024-2025、2025-2026 分段测试。
```

本机性能定位：

```text
规则策略回测和模型训练不是一回事。
规则回测通常很快，主要慢在数据读取和参数组合数量。
LightGBM/XGBoost 训练才会明显更慢，应作为离线任务。
```

Y9000P 本机预估：

```text
单基金 5 年日频数据：< 1 秒。
100 只基金 5 年日频数据：几秒到 30 秒。
500 只基金 5 年日频数据：30 秒到 3 分钟。
1000 只基金 * 20 参数组合：几分钟到 30 分钟，取决于指标数量和并发。
```

性能实现要求：

```text
1. 回测期间禁止调用东方财富/天天基金等外部接口。
2. 历史净值必须由 Java 一次性批量传入，或 Python 从本地缓存/数据库批量读取。
3. pandas/numpy 向量化计算收益率、均线、波动率、回撤。
4. 多基金之间可以并发，多参数组合需要限制并发和数量。
5. 回测结果落库或落 parquet/json，前端只轮询进度和读取结果。
```

### 9.2 回测 API

#### 9.2.1 单基金回测

```http
POST /api/v1/backtest/run
```

请求：

```json
{
  "fundCode": "025833",
  "fundName": "天弘电网设备特高压指数C",
  "fundType": "INDEX",
  "startDate": "2024-01-01",
  "endDate": "2026-06-28",
  "initialCash": 10000,
  "navSeries": [],
  "strategyParams": {
    "buyThreshold": 80,
    "sellThreshold": 40,
    "maxSinglePositionRate": 25
  }
}
```

响应：

```json
{
  "strategyName": "rule-v1.0.0",
  "totalReturnRate": 18.2,
  "annualReturnRate": 7.4,
  "maxDrawdownRate": -9.8,
  "winRate": 56.2,
  "sharpeRatio": 0.82,
  "calmarRatio": 0.75,
  "tradeCount": 18,
  "equityCurve": [],
  "trades": []
}
```

#### 9.2.2 批量基金回测

```http
POST /api/v1/backtest/run-batch
```

请求：

```json
{
  "taskName": "rule-v1.0.0-index-funds-2024-2026",
  "strategyName": "rule-v1.0.0",
  "startDate": "2024-01-01",
  "endDate": "2026-06-28",
  "initialCash": 10000,
  "feeRate": 0.0015,
  "funds": [
    {
      "fundCode": "025833",
      "fundName": "天弘电网设备特高压指数C",
      "fundType": "INDEX",
      "navSeries": []
    }
  ],
  "strategyParams": {
    "buyThreshold": 80,
    "sellThreshold": 40,
    "maxSinglePositionRate": 25
  },
  "options": {
    "workers": 6,
    "saveEquityCurve": false,
    "saveTrades": true
  }
}
```

响应：

```json
{
  "taskId": "bt-20260628-001",
  "status": "COMPLETED",
  "fundCount": 100,
  "successCount": 98,
  "failedCount": 2,
  "summary": {
    "avgAnnualReturnRate": 7.8,
    "medianAnnualReturnRate": 6.4,
    "avgMaxDrawdownRate": -11.2,
    "medianMaxDrawdownRate": -9.7,
    "winFundRate": 61.0,
    "outperformBuyHoldRate": 55.0,
    "avgTradeCount": 16.8,
    "avgSharpeRatio": 0.71
  },
  "results": []
}
```

#### 9.2.3 参数网格回测

```http
POST /api/v1/backtest/run-grid
```

请求：

```json
{
  "taskName": "rule-grid-v1",
  "startDate": "2022-01-01",
  "endDate": "2026-06-28",
  "initialCash": 10000,
  "feeRate": 0.0015,
  "funds": [],
  "paramGrid": {
    "buyThreshold": [75, 80, 85],
    "sellThreshold": [35, 40, 45],
    "maxSinglePositionRate": [20, 25, 30],
    "takeProfitRate": [15, 20, 25]
  },
  "options": {
    "workers": 6,
    "maxCombinations": 100,
    "rankBy": "calmarRatio"
  }
}
```

响应：

```json
{
  "taskId": "bt-grid-20260628-001",
  "status": "COMPLETED",
  "combinationCount": 81,
  "bestParams": {
    "buyThreshold": 80,
    "sellThreshold": 40,
    "maxSinglePositionRate": 25,
    "takeProfitRate": 20
  },
  "topResults": [
    {
      "rank": 1,
      "annualReturnRate": 8.1,
      "maxDrawdownRate": -9.5,
      "calmarRatio": 0.85,
      "outperformBuyHoldRate": 57.0,
      "params": {}
    }
  ]
}
```

参数网格限制：

```text
第一版 maxCombinations <= 100。
如果组合数超过上限，直接返回 400，提示缩小参数范围。
不要为了追求历史最优参数无限扩大网格，这会导致过拟合。
```

#### 9.2.4 异步回测任务

大批量回测建议异步，不要让 HTTP 长时间阻塞。

Python API：

```http
POST /api/v1/backtest/tasks
GET  /api/v1/backtest/tasks/{taskId}
GET  /api/v1/backtest/tasks/{taskId}/results
POST /api/v1/backtest/tasks/{taskId}/cancel
```

任务状态：

```text
PENDING
RUNNING
COMPLETED
FAILED
CANCELLED
```

任务进度：

```json
{
  "taskId": "bt-20260628-001",
  "status": "RUNNING",
  "progress": 42.5,
  "totalUnits": 2000,
  "finishedUnits": 850,
  "successCount": 840,
  "failedCount": 10,
  "startedAt": "2026-06-28 14:00:00",
  "finishedAt": null,
  "message": "Running backtest 850/2000"
}
```

Java 第一版可先同步调用单基金/小批量；大批量阶段必须增加：

```text
quant_backtest_task
quant_backtest_result
前端轮询 task status
```

### 9.3 回测验收标准

必须避免：

```text
未来函数：当天信号只能使用当天 15:00 前可获得的数据。
幸存者偏差：第一版单基金回测可暂不处理，文档说明限制。
过度交易：交易次数过高要扣分。
不计费用：第一版至少支持 feeRate 参数，默认 0.15%。
```

指标：

```text
totalReturnRate
annualReturnRate
maxDrawdownRate
winRate
sharpeRatio
calmarRatio
tradeCount
turnoverRate
```

### 9.4 批量回测结果怎么看

不要只看最高收益。优先看稳定性：

```text
1. 中位数年化收益率
2. 平均最大回撤
3. 最差 10% 样本表现
4. 跑赢买入持有比例
5. 正收益基金比例
6. 平均交易次数
7. 交易过多惩罚
8. 不同年份/市场阶段表现
```

推荐汇总表：

```text
strategyName
modelVersion
fundCount
dateRange
avgAnnualReturnRate
medianAnnualReturnRate
p10AnnualReturnRate
avgMaxDrawdownRate
medianMaxDrawdownRate
worstMaxDrawdownRate
winFundRate
outperformBuyHoldRate
avgTradeCount
avgSharpeRatio
avgCalmarRatio
```

策略通过标准，第一版建议：

```text
跑赢买入持有比例 >= 52%
平均最大回撤低于买入持有
平均交易次数每年 <= 12
最差 10% 样本不能显著劣于买入持有
滚动年份中不能只在单一年份有效
```

### 9.5 回测缓存与数据格式

为了适配本机批量测试，Python 侧实现缓存：

```text
quant-engine/.cache/parquet/nav_daily.parquet
quant-engine/.cache/features/{fundCode}_{endDate}_{window}.parquet
quant-engine/.cache/backtest/{taskId}/summary.json
quant-engine/.cache/backtest/{taskId}/results.parquet
```

缓存键：

```text
fundCode
startDate
endDate
strategyName
modelVersion
paramsHash
dataVersion
```

Java 侧如果从 MySQL 传数据：

```text
一次查出所有 fund_code 的 fund_nav_daily。
按 fundCode 分组后构造 batch 请求。
不要每只基金查询一次。
```

### 9.6 本机并发建议

Y9000P Ultra 9 / 32G 建议默认：

```text
backtest_workers = 6
单批基金数 = 200-500
单次参数组合 <= 100
保存 equityCurve = false
保存 trades = true
```

如果运行时电脑仍然流畅，可以提升：

```text
backtest_workers = 8
单批基金数 = 500-1000
```

如果出现风扇狂转、内存接近 28 GB、Java/MySQL 响应变慢：

```text
backtest_workers 降到 4
单批基金数降到 200
关闭详细 equityCurve 保存
```

### 9.7 回测与训练的区别

```text
规则回测：
  不训练参数，主要是历史循环和指标计算。
  适合频繁运行、快速迭代。

模型训练：
  需要构造标签、训练 LightGBM/XGBoost、交叉验证。
  应离线运行，不要和盘中建议抢资源。
```

第一版任务优先级：

```text
先把规则回测做快、做准、做可复现。
再考虑模型训练。
```

## 10. 后续机器学习模型

第一版不启用，但要预留接口。

### 10.1 推荐模型

```text
LightGBM
XGBoost
RandomForest
```

### 10.2 预测目标

不要预测明天净值具体值。推荐：

```text
未来 5 个交易日收益是否 > 0
未来 20 个交易日是否跑赢基准指数
未来 20 个交易日最大回撤是否超过 5%
未来 10 个交易日风险收益评分
```

### 10.3 模型输出

```text
upProbability
outperformProbability
drawdownProbability
riskProbability
```

由规则层融合：

```text
if ml enabled:
  totalScore = ruleScore * 0.70 + mlOpportunityScore * 0.30
```

### 10.4 模型注册

Python 侧：

```text
models/registry.py
models/artifacts/
  lgbm-v1.0.0.pkl
  feature_columns-v1.0.0.json
```

Java 侧：

```text
quant_signal.model_version
quant_backtest_result.model_version
```

## 11. 个人使用风控要求

### 11.1 建议上限

即使是个人使用，也建议把风控写进代码里，避免临近 15:00 时冲动操作。

第一版硬编码或配置：

```text
单次 BUY 最大 10%
单次 SELL 最大 20%
单基金仓位超过上限不得 BUY
总权益仓位超过上限不得 BUY
14:57 后不得 BUY
QDII 在 A 股盘中不得因为 A 股估值给 BUY
```

建议默认值：

```text
maxSingleBuyRatio = 5%
maxAggressiveBuyRatio = 10%
maxSingleSellRatio = 10%
maxTakeProfitSellRatio = 20%
minSuggestAmount = 100 元
```

如果资金规模较小，可以把 `minSuggestAmount` 改为 50 元，避免生成过碎的操作建议。

### 11.2 置信度

置信度不是准确率。前端文案不要写“准确率”。

推荐展示：

```text
模型置信度
```

不要展示：

```text
预测准确率
稳赚概率
收益保证
```

### 11.3 个人操作闭环

建议系统展示时区分：

```text
量化建议：模型根据规则输出。
人工确认：你实际是否操作。
实际操作记录：你在平台操作后手动登记 trade_record。
复盘结果：次日或一段时间后验证建议是否有效。
```

后续可增加字段：

```text
quant_signal.user_confirmed
quant_signal.user_action
quant_signal.user_note
quant_signal.review_status
quant_signal.review_profit
```

第一版可以不加字段，但文档保留演进方向。

## 12. 测试计划

### 12.1 Python 单元测试

必须覆盖：

```text
nav_features:
  收益率计算
  MA 计算
  最大回撤计算
  样本不足处理

rule_model:
  趋势强 -> 高 trendScore
  高波动 -> 低 riskScore
  仓位超限 -> 不允许 BUY
  14:57 后 -> 不允许 BUY

action_mapper:
  totalScore >= 80 -> BUY
  仓位超限 -> SELL/WATCH
  totalScore < 35 -> SELL

schemas:
  请求响应字段校验
```

命令：

```bash
cd quant-engine
pytest
```

### 12.2 Java 单元测试

新增测试：

```text
QuantEngineClientImplTest
QuantAnalysisServiceImplTest
QuantAnalysisControllerTest
```

重点：

```text
Python 返回 BUY 时保存 quant_signal。
Python 失败时 fallback 生效。
生成 strategy_signal 的字段正确。
holdingId 不属于用户时拒绝访问。
navSeries 按日期升序。
QDII/非盘中窗口不会传入误导性 themeRate。
```

命令：

```bash
cd quant-fund-server
mvn.cmd test
```

### 12.3 前端类型检查

```bash
cd quant-fund-web
npm.cmd run typecheck
```

### 12.4 联调 smoke

新增：

```text
tools/quant-engine-smoke.mjs
```

流程：

```text
1. 登录或注册测试用户。
2. 查询持仓。
3. 调 POST /api/quant/holdings/{id}/analyze。
4. 断言返回 action、score、reasons。
5. 调 GET /api/quant/signals。
6. 断言刚生成的信号存在。
```

## 13. 分阶段实施任务

执行门禁：

```text
第一轮只做 Python 侧 quant-engine，不修改 Java 后端和 Vue 前端。
Python 侧完成后必须先汇报完成情况、测试结果、启动方式和接口样例，再进入 Java 对接。
进入 Phase 2 前，必须明确告知用户“准备开始 Java 对接”，并等待用户确认或新的指示。
```

### Phase 1: Python 规则量化引擎

目标：

```text
建立 quant-engine FastAPI 服务。
实现单持仓 /api/v1/quant/analyze。
实现批量持仓 /api/v1/quant/analyze-batch。
实现规则多因子评分。
实现 pytest。
不修改 quant-fund-server。
不修改 quant-fund-web。
```

交付：

```text
quant-engine 目录
requirements.txt
README.md
规则模型单测
可直接复制执行的 curl/httpx 请求样例
```

验收：

```text
GET /api/v1/health 返回 UP。
POST /api/v1/quant/analyze 返回完整 QuantSignal JSON。
POST /api/v1/quant/analyze-batch 返回批量结果。
pytest 通过。
给出本地启动命令和一条成功响应示例。
```

### Phase 2: Java 调用 Python

开始条件：

```text
Phase 1 已完成。
pytest 已通过。
已向用户汇报 Python 部分完成，并明确告知准备开始 Java 对接。
用户确认继续，或用户明确要求继续实施 Java 对接。
```

目标：

```text
新增配置、DTO、Client、Service、Controller、SQL、Entity、Mapper。
Java 能调用 Python 并保存 quant_signal。
可选同步 strategy_signal 摘要；quant_signal 必须作为主表。
```

交付：

```text
docs/sql/006_quant_engine.sql
QuantEngineClient
QuantAnalysisService
QuantAnalysisController
QuantSignal 实体
QuantSignalVO
```

验收：

```text
mvn.cmd test 通过。
POST /api/quant/holdings/{holdingId}/analyze 返回信号。
数据库 quant_signal 有记录。
GET /api/quant/signals 能看到量化信号。
如果实现 strategy_signal 兼容摘要，则 GET /api/strategies/signals 也能看到 QUANT_MODEL 摘要。
```

### Phase 3: 前端展示

目标：

```text
前端展示今日量化建议。
基金详情可手动生成量化建议。
AI 分析页增加量化建议 tab。
```

交付：

```text
domain.ts 类型
quant.ts API
DashboardView 今日量化建议
FundDetailView 量化建议卡片
AiAnalysisView tab
```

验收：

```text
npm.cmd run typecheck 通过。
页面能生成并展示量化建议。
按钮 loading/错误态正常。
```

### Phase 4: 调度生成 15:00 前建议

目标：

```text
在 14:30、14:50、14:55 自动生成建议。
首页展示最新 FINAL_DECISION 建议。
```

交付：

```text
QuantFundScheduler 新任务
ScheduledFundTaskService.generateQuantSignals
scheduler_task_log 记录
```

验收：

```text
交易日调度可执行。
非交易日跳过。
Python 不可用时 fallback。
```

### Phase 5: 回测

目标：

```text
Python 支持 /api/v1/backtest/run。
第一步先完成 Python 回测 API 和 pytest。
Java 保存 quant_backtest_result 放到 Python 回测验收通过之后。
```

交付：

```text
backtest engine
metrics
QuantBacktestResult
```

验收：

```text
给定历史净值可输出收益、回撤、胜率、夏普。
不得使用未来数据。
```

### Phase 6: LightGBM/XGBoost

目标：

```text
训练简单概率模型。
模型输出作为规则评分辅助。
```

交付：

```text
train_lgbm.py
predict.py
model registry
```

验收：

```text
可训练、可加载、可推理。
回测结果不明显劣于纯规则模型。
```

## 14. 验收清单

最终验收：

```text
[ ] quant-engine 可以启动。
[ ] /api/v1/health 返回 UP。
[ ] /api/v1/quant/analyze 返回 BUY/HOLD/WATCH/SELL 中的一种。
[ ] Java /api/quant/holdings/{id}/analyze 可调用成功。
[ ] quant_signal 表有记录。
[ ] strategy_signal 可选同步生成 QUANT_MODEL 摘要；如果未同步，前端必须直接读取 quant_signal。
[ ] 首页能看到今日量化建议。
[ ] 基金详情能手动生成量化建议。
[ ] Python 不可用时 Java fallback 可用。
[ ] 14:57 后不会生成 BUY。
[ ] 仓位超过上限不会生成 BUY。
[ ] QDII/海外基金不会被 A 股盘中估值误导。
[ ] mvn.cmd test 通过。
[ ] npm.cmd run typecheck 通过。
[ ] pytest 通过。
```

## 15. 关键设计结论

这套方案的核心原则：

```text
先规则量化
再回测验证
再机器学习增强
最后大模型解释
```

不要反过来。

最终系统定位：

```text
QuantFund = 基金数据驾驶舱 + 量化决策引擎 + AI 解读层
```

其中：

```text
Python Quant Engine 负责算。
Java Spring Boot 负责管。
Vue 前端负责看。
大模型负责说清楚。
用户自己负责最终操作。
```
