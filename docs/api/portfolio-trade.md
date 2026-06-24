# QuantFund Portfolio, Holding and Simulated Trade API

This batch implements account, holding and simulated trade APIs for `QuantFund`.

Important safety boundary:

- The system does not place real orders.
- Trade records are only simulated bookkeeping records.
- All trade responses include `simulatedTradeNotice`: `仅为模拟操作，并非真实交易`.
- All holding and trade responses include `disclaimer`: `仅供参考，不构成投资建议，不承诺收益`.

## Portfolio Accounts

- `POST /api/portfolios`: create a portfolio account.
- `GET /api/portfolios`: list current user's portfolio accounts.
- `GET /api/portfolios/summary`: aggregate total assets, invested amount, profit and position rates.
- `GET /api/portfolios/{id}`: get one owned account.
- `PUT /api/portfolios/{id}`: update account name, platform, status and max single fund position rate.
- `GET /api/portfolios/{id}/holdings`: list holdings under one owned account.
- `POST /api/portfolios/{id}/recalculate`: recalculate account totals from holdings.

Supported `platformType` values: `ALIPAY`, `EASTMONEY`, `BROKER`, `MANUAL`.

## Holdings

- `POST /api/holdings`: create a holding.
- `GET /api/holdings`: list holdings, optionally filtered by `accountId` and `fundCode`.
- `GET /api/holdings/{id}`: get one owned holding.
- `PUT /api/holdings/{id}`: update a holding.
- `DELETE /api/holdings/{id}`: logical delete a holding.
- `POST /api/holdings/{id}/recalculate`: recalculate profit and profit rate.

Holding calculation rules:

- All money and share values use `BigDecimal`.
- If `currentEstimateNav` exists, holding amount is calculated from `holdingShare * currentEstimateNav`.
- Otherwise, if `latestOfficialNav` exists, holding amount is calculated from `holdingShare * latestOfficialNav`.
- Holding profit is `holdingAmount - holdingCost`.
- Holding profit rate is `holdingProfit / holdingCost * 100`.
- Daily profit uses `(currentEstimateNav - latestOfficialNav) * holdingShare` when both NAV values exist.

## Simulated Trades

- `POST /api/trades`: create a trade using request `tradeType`.
- `POST /api/trades/buy`: create a simulated buy record.
- `POST /api/trades/sell`: create a simulated sell record.
- `POST /api/trades/regular-invest`: create a simulated regular investment record.
- `POST /api/trades/convert-in`: create a simulated conversion-in record.
- `POST /api/trades/convert-out`: create a simulated conversion-out record.
- `GET /api/trades`: list current user's trades, optionally filtered by `accountId`, `holdingId`, `tradeType`, and `tradeStatus`.
- `GET /api/trades/processing`: list processing trades.
- `GET /api/trades/{id}`: get one owned trade.

Supported `tradeType` values: `BUY`, `SELL`, `REGULAR_INVEST`, `CONVERT_IN`, `CONVERT_OUT`.

Supported `tradeStatus` values: `PROCESSING`, `COMPLETED`, `CANCELLED`, `FAILED`.

Completed trade application rules:

- `BUY`, `REGULAR_INVEST`, and `CONVERT_IN` increase holding shares and holding cost.
- `SELL` and `CONVERT_OUT` reduce holding shares and cost by share ratio.
- Processing, cancelled, and failed records do not modify holdings.
- Sell and convert-out require an existing holding.
- Sell and convert-out require either `tradeShare` or a positive `tradeNav` so the system can calculate reduced shares.

## Data Isolation

Services always read the current user from `UserContext`. Request bodies never decide `user_id`.

The following resources are wired into `@DataScope` owner lookup:

- `PORTFOLIO_ACCOUNT`
- `FUND_HOLDING`
- `TRADE_RECORD`

