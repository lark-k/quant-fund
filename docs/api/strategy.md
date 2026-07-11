# QuantFund Strategy API

This document describes the Java business-rule strategy engine used by `QuantFund`.

Important safety boundary:

- Strategy signals are reference signals only.
- The system does not place real orders.
- Every signal response includes `disclaimer`: `仅供参考，不构成投资建议，不承诺收益`.
- Buy and sell actions must still be manually handled by the user on the original fund platform.

## Strategy Analysis

- `POST /api/strategies/holdings/{holdingId}/analyze`: run strategy analysis for one owned holding.
- `POST /api/strategies/accounts/{accountId}/analyze`: run strategy analysis for all holdings under one owned account.
- `GET /api/strategies/signals`: query saved strategy signals.

Signal query filters:

- `accountId`
- `holdingId`
- `fundCode`
- `action`: `BUY`, `SELL`, `HOLD`, `CONVERT`, `WATCH`

## Strategy Config

- `GET /api/strategies/configs`: list current user's strategy configs.
- `PUT /api/strategies/configs`: create or update a strategy config by `strategyType` and optional `fundType`.

Supported `strategyType` values:

- `FUND_CLASSIFICATION`
- `DRAWDOWN_STOP_PROFIT`
- `DYNAMIC_LADDER_STOP_PROFIT`
- `FIXED_BATCH_STOP_PROFIT`
- `POSITION_MONITOR`
- `BUY_DIP`
- `RISK_ALERT`

The service validates and saves `paramsJson`, and uses `enabled` to decide whether a rule participates in analysis. Rule-specific thresholds can be stored on top of this config model.

## Risk Profile

- `GET /api/strategies/risk-profile`: get or initialize current user's risk profile.
- `PUT /api/strategies/risk-profile`: update risk level and threshold parameters.

Risk profile fields:

- `riskLevel`: `LOW`, `MEDIUM`, `HIGH`
- `maxEquityPositionRate`
- `maxSingleFundPositionRate`
- `drawdownAlertRate`
- `dailyRiseAlertRate`
- `dailyFallAlertRate`
- `configJson`

## Implemented Rules

### Fund Classification

Classifies fund holdings from fund name, fund type, active/passive flag, and optional tracking-index data.

Recognized categories include:

- `ACTIVE_EQUITY`
- `INDEX`
- `ETF`
- `ETF_LINK`
- `INDEX_ENHANCED`
- `BOND`
- `FIXED_INCOME_PLUS`
- `MONEY_MARKET`
- `QDII`
- `MIXED`
- `UNKNOWN`

### Drawdown Stop Profit

For active equity and mixed funds:

- Profit rate over 15% and 90-day NAV drawdown over 3%: light sell reference.
- Drawdown over 5%: medium sell reference.
- Drawdown over 8%: heavier sell reference.

The rule uses 90-day NAV drawdown as an approximation for profit drawdown. It is saved as an explainable signal and remains a manual reference.

### Dynamic Ladder Stop Profit

For active equity and mixed funds:

- Profit rate over 10%: sell 10% reference.
- Profit rate over 20%: sell 20% reference.
- Profit rate over 30%: sell 30% reference.
- Profit rate over 50%: sell 40% reference.

If account equity position is over 70%, the suggested sell ratio is raised. If the holding is marked as core holding, the ratio is reduced.

### Fixed Batch Stop Profit

For index and ETF-like funds:

- Profit rate over 8%: sell 10% reference.
- Profit rate over 15%: sell 20% reference.
- Profit rate over 25%: sell 30% reference.
- Profit rate over 35%: sell 40% reference.

### Position Monitor

Checks:

- Equity fund position above risk-profile threshold.
- Single-fund position above risk-profile threshold.

### Buy Dip

Uses 90-day NAV high drawdown:

- Drawdown over 5%: small buy reference.
- Drawdown over 10%: medium attention reference.
- Drawdown over 15%: high-risk attention reference.

This rule is skipped when account equity position is already over the configured maximum.

### Risk Alert

Checks:

- Daily estimated rise over threshold: avoid chasing.
- Daily estimated fall over threshold: caution.
- NAV drawdown over threshold: risk warning.
