# QuantFund Quant Engine

Python FastAPI service for QuantFund intraday rule-based quant decisions.

Phase 1 only implements a deterministic multi-factor rule model. It does not
connect to real trading APIs, does not place orders, and does not use deep
learning. Java remains responsible for business permissions, persistence, and
workflow closure; Python only calculates quant signals.

## Local Setup

```powershell
cd quant-engine
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8091 --reload
```

Recommended local defaults for the target Y9000P machine:

```text
QUANT_ENGINE_WORKERS=1
QUANT_ENGINE_BACKTEST_WORKERS=6
QUANT_ENGINE_MAX_BATCH_FUNDS=500
```

## Tests

```powershell
cd quant-engine
pytest
```

## APIs

### Health

```http
GET /api/v1/health
```

Example:

```powershell
curl.exe http://127.0.0.1:8091/api/v1/health
```

Response:

```json
{
  "status": "UP",
  "service": "quant-engine",
  "modelVersion": "rule-v1.36.0"
}
```

### Single Holding Analysis

```http
POST /api/v1/quant/analyze
```

Example:

```powershell
curl.exe -X POST http://127.0.0.1:8091/api/v1/quant/analyze `
  -H "Content-Type: application/json" `
  -d @examples/analyze-request.json
```

Minimal request body shape:

```json
{
  "requestId": "qf-20260628-145000-1001",
  "userId": 1,
  "account": {
    "accountId": 1,
    "totalAsset": 10000,
    "equityPositionRate": 45
  },
  "riskProfile": {
    "riskLevel": "MEDIUM",
    "maxEquityPositionRate": 70,
    "maxSingleFundPositionRate": 25
  },
  "holding": {
    "holdingId": 1001,
    "fundCode": "025833",
    "fundName": "Example Index Fund",
    "fundType": "INDEX",
    "holdingAmount": 1200,
    "holdingProfitRate": 6.2,
    "positionRate": 12,
    "currentEstimateGrowthRate": 0.6,
    "relatedThemeRate": 0.8,
    "holdingDays": 35
  },
  "navSeries": [
    {"date": "2026-06-20", "nav": 1.0000, "dailyGrowthRate": 0.1},
    {"date": "2026-06-21", "nav": 1.0060, "dailyGrowthRate": 0.6},
    {"date": "2026-06-22", "nav": 1.0100, "dailyGrowthRate": 0.4}
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

Response action is one of `BUY`, `SELL`, `HOLD`, or `WATCH` in Phase 1. The
response includes score breakdown, metrics, reasons, risks, model version, and
the standard investment disclaimer.

### Batch Holding Analysis

```http
POST /api/v1/quant/analyze-batch
```

Example:

```json
{
  "requestId": "qf-batch-20260628-145000-user1",
  "items": [
    {
      "requestId": "qf-20260628-145000-1001",
      "account": {"totalAsset": 10000, "equityPositionRate": 45},
      "riskProfile": {"riskLevel": "MEDIUM", "maxEquityPositionRate": 70, "maxSingleFundPositionRate": 25},
      "holding": {"fundCode": "025833", "fundName": "Example Index Fund", "fundType": "INDEX", "positionRate": 12},
      "navSeries": [],
      "market": {"tradingDay": true, "trading": true, "now": "2026-06-28 14:50:00"}
    }
  ]
}
```

Batch mode is the required integration path for account-level analysis and
backtest-style bulk evaluation. Java should not call the single-analysis API in
a per-fund loop for large batches.

### Rule Backtest

```http
POST /api/v1/backtest/run
POST /api/v1/backtest/run-batch
POST /api/v1/backtest/run-grid
```

Batch request shape:

```json
{
  "taskName": "rule-v1-current-holdings",
  "strategyName": "QuantRuleEngine",
  "startDate": "2025-06-30",
  "endDate": "2026-06-30",
  "initialCash": 10000,
  "feeRate": 0.0015,
  "funds": [
    {
      "fundCode": "025833",
      "fundName": "Example Index Fund",
      "fundType": "INDEX",
      "navSeries": [
        {"date": "2025-06-30", "nav": 1.0, "dailyGrowthRate": 0.0}
      ]
    }
  ],
  "strategyParams": {
    "buyThreshold": 52,
    "sellThreshold": 6,
    "maxSinglePositionRate": 45,
    "buyStepRatio": 20,
    "sellStepRatio": 8,
    "takeProfitRate": 300,
    "stopLossRate": -18,
    "minNavSamples": 40,
    "warmupDays": 180,
    "trendHoldReturn20d": 1.5,
    "trendHoldMa20Deviation": -7
  },
  "options": {
    "workers": 6,
    "saveEquityCurve": true,
    "saveTrades": true
  }
}
```

Backtest uses only supplied historical NAV data. It does not request East
Money, Tiantian Fund, or any external market data source. Use
`QUANT_ENGINE_BACKTEST_WORKERS=6` as the default local setting for the target
Y9000P machine.

The response includes two benchmark views:

- `benchmarkReturnRate`: full-position buy-and-hold from the first backtest day.
- `positionBenchmarkReturnRate`: buy-and-hold using the same position cap as
  `maxSinglePositionRate`, with remaining cash left idle. This is the fairer
  view for account-level risk-control parameters.

`warmupDays` controls how many cached NAV calendar days are supplied before
the requested start date for indicator preheating. Return, benchmark, drawdown,
equity curve, and pass/fail metrics still start from the requested start date.
When `totalScore` falls below `sellThreshold`, the engine keeps holding if the
20-day return is above `trendHoldReturn20d` and the 20-day moving-average
deviation is not below `trendHoldMa20Deviation`. The same two parameters also
participate in the strong-trend lock, together with 60-day return, 60-day
drawdown, and extreme-risk checks. The current recommended defaults use the
3-year window as the primary benchmark. Funds with less than 3 years of NAV
history still participate when their usable sample count is sufficient; the
4-year window is a stability check for older funds, and the 1-year window is
mainly for recent fit.

Fund strategy profiles are maintained in `app/strategies/fund_profile.py`.
Use that registry when a fund's trading vehicle and management style differ,
for example an active QDII fund that should keep overseas risk controls but use
active-fund trend and re-entry rules.

## Phase 6 Optional LightGBM Helper

The rule engine remains the primary decision engine. Phase 6 adds an optional
LightGBM helper with two outputs:

- a binary probability model for capped rule-score adjustment;
- a regression model that predicts the forward return over the training
  horizon, currently the next 20 NAV samples by default.

- By default `QUANT_ENGINE_ML_ENABLED=false`, so production behavior stays pure
  `rule-v1.36.0`.
- When enabled, the active model in `models/registry.json` is loaded once. Its
  probability is converted into a capped score adjustment, and its
  `expectedReturn` is exposed for AI explanation and backtest diagnostics.
- The ML helper does not place trades, does not call external fund data
  sources, and does not bypass rule blockers such as position limits.

Training expects a local CSV that was built from cached/exported historical
features. It must contain the stable feature columns in `app/ml/features.py`,
a binary label column defaulting to `label`, and a forward-return column
defaulting to `forwardReturn`.

```powershell
cd quant-engine
python train_lgbm.py --input .\data\training_samples.csv --model-dir .\models --model-version lgbm-v1.1.0 --n-jobs 8
```

Predict with the active model:

```powershell
cd quant-engine
python predict.py --features .\data\sample_features.json --model-dir .\models
```

Enable the helper for API inference after a model is trained and registered:

```powershell
$env:QUANT_ENGINE_ML_ENABLED="true"
$env:QUANT_ENGINE_ML_MODEL_DIR="models"
$env:QUANT_ENGINE_ML_SCORE_ADJUSTMENT_CAP="5"
uvicorn app.main:app --host 127.0.0.1 --port 8091
```

VSCode one-click flow:

1. In the web backtest page, click `拉取净值` for the selected range first.
2. Click `导出样本` to download a CSV generated from local cached NAV data.
3. Save or move the CSV under `quant-engine/data/training_samples.csv`.
4. Run task `ml: train lightgbm`, enter the training CSV path when prompted.
   The default is `data/training_samples.csv` under `quant-engine`.
5. Choose debug configuration `Quant Engine: FastAPI + ML (8091)`.
6. Start debugging. This sets `QUANT_ENGINE_ML_ENABLED=true` and loads the
   active model from `quant-engine/models`.
7. Use task `ml: inspect models` or call `GET /api/v1/ml/models` to confirm
   the active model.

Useful endpoints:

- `GET /api/v1/ml/models`: inspect registered models.
- `POST /api/v1/ml/predict`: run a feature-dict prediction smoke test.

Training CSV minimum columns:

- All feature columns listed in `app/ml/features.py`, such as `return20d`,
  `return60d`, `ma20Deviation`, `maxDrawdown60d`, `positionToSingleLimit`,
  `navSampleSize`, `holdingProfitRate`, and `holdingDays`.
- A binary `label` column: `1` means the sample later performed well enough to
  favor holding/buying, `0` means it did not.
- A numeric `forwardReturn` column: the realized future return percentage used
  to train the LightGBM return regressor.
- Optional `horizonDays` column. If omitted, the trainer records the default
  prediction horizon as 20 NAV samples.
- Optional `date` column. If present, training sorts by date before splitting
  train/validation data, which reduces look-ahead leakage.

The backtest page export uses the default label rule:

- Horizon: future 20 NAV samples.
- Positive label: future return is at least `2%`.
- Risk filter: future max drawdown is no worse than `-8%`.
- The final horizon rows are skipped, so labels do not look beyond available
  local data.

## Rule Constraints

- The service only generates suggestions. It never places trades.
- `BUY` is blocked when single-fund or total equity position reaches the risk
  profile limit.
- QDII/overseas funds are blocked from generating same-day `BUY` suggestions
  from A-share intraday movement.
- Large language models may explain returned results later, but must not
  override the deterministic action returned by this engine.

## Known Limits

- LightGBM is optional and disabled by default; XGBoost and deep learning are
  not enabled.
- Historical data must be supplied by Java or local cache; this service does
  not call East Money, Tiantian Fund, or any external market data source.
- Confidence is a rule-model confidence score, not an accuracy guarantee.
