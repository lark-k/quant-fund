DELETE older
FROM screener_backtest_result older
INNER JOIN screener_backtest_result newer
  ON newer.score_date = older.score_date
 AND newer.horizon_days = older.horizon_days
 AND newer.bucket_name = older.bucket_name
 AND newer.model_version = older.model_version
 AND newer.id > older.id;

ALTER TABLE screener_backtest_result
  ADD UNIQUE KEY uk_screener_backtest_unit (score_date, horizon_days, bucket_name, model_version),
  ADD KEY idx_screener_backtest_validation (model_version, horizon_days, bucket_name, score_date);
