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
  "modelVersion": "rule-v1.0.0"
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

## Rule Constraints

- The service only generates suggestions. It never places trades.
- `BUY` is blocked at and after 14:57.
- `BUY` is blocked when single-fund or total equity position reaches the risk
  profile limit.
- QDII/overseas funds are blocked from generating same-day `BUY` suggestions
  from A-share intraday movement.
- Large language models may explain returned results later, but must not
  override the deterministic action returned by this engine.

## Known Phase 1 Limits

- Backtest APIs are intentionally not implemented in this first round.
- LightGBM/XGBoost and deep learning are intentionally not enabled.
- Historical data must be supplied by Java or local cache in later phases; this
  service does not call East Money, Tiantian Fund, or any external market data
  source.
- Confidence is a rule-model confidence score, not an accuracy guarantee.
