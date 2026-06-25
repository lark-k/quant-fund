CREATE TABLE IF NOT EXISTS market_index_daily (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  index_code VARCHAR(32) NOT NULL COMMENT 'Index code',
  index_name VARCHAR(64) NOT NULL COMMENT 'Index name',
  trade_date DATE NOT NULL COMMENT 'Trade date',
  close_price DECIMAL(20,6) NOT NULL COMMENT 'Close price',
  daily_change_rate DECIMAL(10,4) DEFAULT NULL COMMENT 'Daily change rate percentage',
  source_name VARCHAR(64) NOT NULL COMMENT 'Data source name',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Create time',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Update time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (id),
  UNIQUE KEY uk_market_index_daily_code_date (index_code, trade_date),
  KEY idx_market_index_daily_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Market index daily quote';
