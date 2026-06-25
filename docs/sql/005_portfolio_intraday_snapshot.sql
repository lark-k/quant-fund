CREATE TABLE IF NOT EXISTS portfolio_intraday_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'User id',
  snapshot_date DATE NOT NULL COMMENT 'Snapshot date',
  snapshot_time DATETIME(3) NOT NULL COMMENT 'Snapshot time',
  total_asset DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT 'Total asset at snapshot',
  daily_profit DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT 'Portfolio daily profit at snapshot',
  daily_profit_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Portfolio daily profit rate percentage',
  source_name VARCHAR(64) NOT NULL DEFAULT 'PORTFOLIO_RECALCULATE' COMMENT 'Snapshot source',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Create time',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Update time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (id),
  UNIQUE KEY uk_portfolio_intraday_user_time (user_id, snapshot_time),
  KEY idx_portfolio_intraday_user_date (user_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Portfolio intraday profit snapshot';
