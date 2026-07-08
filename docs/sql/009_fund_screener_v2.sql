ALTER TABLE screener_factor_snapshot
  ADD COLUMN benchmark_code VARCHAR(32) DEFAULT NULL COMMENT 'Benchmark code used for relative return' AFTER excess_return_120d,
  ADD COLUMN return_drawdown_ratio_120d DECIMAL(10,4) DEFAULT NULL COMMENT '120d return divided by absolute 120d drawdown' AFTER benchmark_code,
  ADD COLUMN return_consistency_score DECIMAL(10,4) DEFAULT NULL COMMENT 'Multi-window return consistency score' AFTER return_drawdown_ratio_120d,
  ADD COLUMN fund_age_years DECIMAL(10,4) DEFAULT NULL COMMENT 'Fund age in years at factor date' AFTER return_consistency_score;

ALTER TABLE screener_quality_score
  ADD COLUMN return_quality_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'V2 return quality score' AFTER data_score,
  ADD COLUMN drawdown_control_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'V2 drawdown control score' AFTER return_quality_score,
  ADD COLUMN consistency_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'V2 multi-period consistency score' AFTER drawdown_control_score,
  ADD COLUMN investability_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'V2 investability score' AFTER consistency_score;

CREATE TABLE IF NOT EXISTS screener_backtest_result (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  run_date DATE NOT NULL COMMENT 'Backtest run date',
  score_date DATE NOT NULL COMMENT 'Historical score date',
  horizon_days INT NOT NULL COMMENT 'Forward horizon in NAV observations',
  bucket_name VARCHAR(32) NOT NULL COMMENT 'TOP_5/TOP_10/WATCH/NEUTRAL/AVOID',
  sample_count INT NOT NULL DEFAULT 0 COMMENT 'Sample count',
  avg_forward_return DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Average forward return percentage',
  win_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Positive-return sample ratio percentage',
  avg_excess_return DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Average return minus all-sample average',
  max_drawdown DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Worst forward max drawdown percentage',
  model_version VARCHAR(64) NOT NULL DEFAULT 'screener-rule-v2',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_screener_backtest_run_date (run_date),
  KEY idx_screener_backtest_score_bucket (score_date, bucket_name, horizon_days)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fund screener backtest validation result';
