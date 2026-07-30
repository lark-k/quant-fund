ALTER TABLE portfolio_account
  ADD COLUMN cash_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000 COMMENT 'Available cash balance'
  AFTER daily_profit;
