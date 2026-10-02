CREATE TABLE IF NOT EXISTS fund_cash_account (
  account_id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  unallocated DECIMAL(20,4) NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS fund_cash_balance (
  account_id BIGINT NOT NULL,
  fund_code VARCHAR(20) NOT NULL,
  fund_name VARCHAR(200) NOT NULL,
  balance DECIMAL(20,4) NOT NULL DEFAULT 0,
  PRIMARY KEY(account_id, fund_code)
);
CREATE TABLE IF NOT EXISTS fund_cash_event (
  event_key VARCHAR(100) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  fund_code VARCHAR(20) NOT NULL,
  delta DECIMAL(20,4) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
