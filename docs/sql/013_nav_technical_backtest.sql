CREATE TABLE IF NOT EXISTS nav_technical_backtest (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    holding_id BIGINT NOT NULL,
    fund_code VARCHAR(16) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    created_at DATETIME NOT NULL,
    result_json LONGTEXT NOT NULL,
    INDEX idx_nav_backtest_owner (user_id, holding_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
