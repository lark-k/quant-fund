-- Additive ledger. Existing holdings, cash, trades and daily snapshots are not altered.
CREATE TABLE IF NOT EXISTS cumulative_profit_user (
    user_id BIGINT PRIMARY KEY,
    opening_history DECIMAL(20,8) NOT NULL DEFAULT 0,
    seed_version VARCHAR(80) NULL
);

CREATE TABLE IF NOT EXISTS cumulative_profit_fund (
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    fund_code VARCHAR(20) NOT NULL,
    profit DECIMAL(20,8) NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, account_id, fund_code)
);

CREATE TABLE IF NOT EXISTS cumulative_profit_position (
    holding_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    fund_code VARCHAR(20) NOT NULL,
    shares DECIMAL(20,8) NOT NULL DEFAULT 0,
    book_value DECIMAL(24,8) NOT NULL DEFAULT 0,
    nav_date DATE NULL,
    unit_nav DECIMAL(20,8) NULL,
    minimum_nav_date DATE NULL,
    tracking INT NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS cumulative_profit_event (
    event_key VARCHAR(100) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    fund_code VARCHAR(20) NOT NULL,
    holding_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    nav_date DATE NULL,
    profit_delta DECIMAL(20,8) NOT NULL,
    shares_after DECIMAL(20,8) NOT NULL,
    book_value_after DECIMAL(24,8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
