USE quant_fund;

INSERT INTO user_account (
  username, password_hash, nickname, role, status, risk_level, register_time, create_time, update_time, deleted
) VALUES (
  'quantdemo',
  '$2a$12$Anws.89GYzr2NKVPDeJUJe5P5HhjLwX4ntjd7CXVWnQLkORSrXrui',
  'QuantFund 演示用户',
  'USER',
  'ENABLED',
  'MEDIUM',
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3),
  0
) ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  nickname = VALUES(nickname),
  role = VALUES(role),
  status = VALUES(status),
  risk_level = VALUES(risk_level),
  update_time = CURRENT_TIMESTAMP(3),
  deleted = 0;

SET @demo_user_id := (SELECT id FROM user_account WHERE username = 'quantdemo' LIMIT 1);

DELETE FROM ai_analysis_report WHERE user_id = @demo_user_id;
DELETE FROM strategy_signal WHERE user_id = @demo_user_id;
DELETE FROM strategy_config WHERE user_id = @demo_user_id;
DELETE FROM trade_record WHERE user_id = @demo_user_id;
DELETE FROM holding_snapshot WHERE user_id = @demo_user_id;
DELETE FROM fund_holding WHERE user_id = @demo_user_id;
DELETE FROM investment_plan WHERE user_id = @demo_user_id;
DELETE FROM risk_profile WHERE user_id = @demo_user_id;
DELETE FROM data_source_config WHERE user_id = @demo_user_id;
DELETE FROM portfolio_account WHERE user_id = @demo_user_id;

INSERT INTO portfolio_account (
  user_id, account_name, platform_type, total_asset, total_invest_amount, current_profit,
  current_profit_rate, daily_profit, cash_position_rate, equity_position_rate, bond_position_rate,
  max_single_fund_position_rate, status, create_time, update_time, deleted
) VALUES (
  @demo_user_id, 'QuantFund 演示组合', 'MANUAL', 501921.0000, 475000.0000, 26921.0000,
  5.6676, 3764.2000, 0.0000, 100.0000, 0.0000, 40.0561,
  'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
);

SET @demo_account_id := LAST_INSERT_ID();

INSERT INTO fund_info (
  fund_code, fund_name, fund_type, active_fund, tracking_index, manager_name, company_name,
  risk_level, theme_tags, manual_category, source_name, source_update_time, create_time, update_time, deleted
) VALUES
  ('161725', '招商中证白酒指数A', 'INDEX', 0, '中证白酒指数', '侯昊', '招商基金', 'HIGH', '["白酒","消费"]', 'INDEX', 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('007689', '国投瑞银新能源混合A', 'ACTIVE_EQUITY', 1, NULL, '施成', '国投瑞银基金', 'HIGH', '["新能源车","储能","电力设备"]', 'ACTIVE_EQUITY', 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('510300', '华泰柏瑞沪深300ETF', 'ETF', 0, '沪深300', '柳军', '华泰柏瑞基金', 'MEDIUM', '["沪深300","宽基ETF"]', 'ETF', 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
  fund_name = VALUES(fund_name),
  fund_type = VALUES(fund_type),
  active_fund = VALUES(active_fund),
  tracking_index = VALUES(tracking_index),
  manager_name = VALUES(manager_name),
  company_name = VALUES(company_name),
  risk_level = VALUES(risk_level),
  theme_tags = VALUES(theme_tags),
  manual_category = VALUES(manual_category),
  source_name = VALUES(source_name),
  source_update_time = VALUES(source_update_time),
  update_time = CURRENT_TIMESTAMP(3),
  deleted = 0;

INSERT INTO fund_holding (
  user_id, account_id, fund_code, fund_name, fund_type, active_fund, holding_amount,
  holding_share, holding_cost, current_estimate_nav, latest_official_nav, holding_profit,
  holding_profit_rate, daily_profit, holding_days, source_platform, regular_investment,
  core_holding, watch_focus, create_time, update_time, deleted
) VALUES
  (@demo_user_id, @demo_account_id, '161725', '招商中证白酒指数A', 'INDEX', 0, 142416.0000, 120000.0000, 132000.0000, 1.186800, 1.174200, 10416.0000, 7.8909, 1512.0000, 365, '支付宝', 1, 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, '007689', '国投瑞银新能源混合A', 'ACTIVE_EQUITY', 1, 158455.0000, 86000.0000, 145000.0000, 1.842500, 1.819800, 13455.0000, 9.2793, 1952.2000, 221, '天天基金', 0, 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, '510300', '华泰柏瑞沪深300ETF', 'ETF', 0, 201050.0000, 50000.0000, 198000.0000, 4.021000, 4.015000, 3050.0000, 1.5404, 300.0000, 412, '券商账户', 0, 1, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0);

SET @holding_161725 := (SELECT id FROM fund_holding WHERE user_id = @demo_user_id AND fund_code = '161725' LIMIT 1);
SET @holding_007689 := (SELECT id FROM fund_holding WHERE user_id = @demo_user_id AND fund_code = '007689' LIMIT 1);
SET @holding_510300 := (SELECT id FROM fund_holding WHERE user_id = @demo_user_id AND fund_code = '510300' LIMIT 1);

INSERT INTO fund_estimate_intraday (
  fund_code, estimate_date, estimate_nav, estimate_growth_rate, estimate_time, source_name,
  `delayed`, raw_payload, create_time, update_time, deleted
) VALUES
  ('161725', CURRENT_DATE, 1.186800, 1.0731, TIMESTAMP(CURRENT_DATE, '14:42:00'), 'MOCK_FALLBACK', 0, JSON_OBJECT('seed', true), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('007689', CURRENT_DATE, 1.842500, 1.2474, TIMESTAMP(CURRENT_DATE, '14:42:00'), 'MOCK_FALLBACK', 0, JSON_OBJECT('seed', true), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('510300', CURRENT_DATE, 4.021000, 0.1494, TIMESTAMP(CURRENT_DATE, '14:42:00'), 'MOCK_FALLBACK', 0, JSON_OBJECT('seed', true), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0);

INSERT INTO fund_nav_daily (
  fund_code, nav_date, unit_nav, accumulated_nav, daily_growth_rate, source_name, create_time, update_time, deleted
) VALUES
  ('161725', CURRENT_DATE - INTERVAL 4 DAY, 1.152000, 1.612000, -0.2300, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('161725', CURRENT_DATE - INTERVAL 3 DAY, 1.166000, 1.626000, 1.2153, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('161725', CURRENT_DATE - INTERVAL 2 DAY, 1.171000, 1.631000, 0.4288, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('161725', CURRENT_DATE - INTERVAL 1 DAY, 1.174200, 1.634200, 0.2733, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('007689', CURRENT_DATE - INTERVAL 4 DAY, 1.781000, 1.981000, -0.5600, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('007689', CURRENT_DATE - INTERVAL 3 DAY, 1.798000, 1.998000, 0.9545, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('007689', CURRENT_DATE - INTERVAL 2 DAY, 1.807000, 2.007000, 0.5006, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('007689', CURRENT_DATE - INTERVAL 1 DAY, 1.819800, 2.019800, 0.7084, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('510300', CURRENT_DATE - INTERVAL 4 DAY, 3.968000, 4.268000, -0.1800, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('510300', CURRENT_DATE - INTERVAL 3 DAY, 3.996000, 4.296000, 0.7056, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('510300', CURRENT_DATE - INTERVAL 2 DAY, 4.006000, 4.306000, 0.2503, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  ('510300', CURRENT_DATE - INTERVAL 1 DAY, 4.015000, 4.315000, 0.2247, 'MOCK_FALLBACK', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
  unit_nav = VALUES(unit_nav),
  accumulated_nav = VALUES(accumulated_nav),
  daily_growth_rate = VALUES(daily_growth_rate),
  update_time = CURRENT_TIMESTAMP(3),
  deleted = 0;

INSERT INTO holding_snapshot (
  user_id, account_id, holding_id, snapshot_date, total_asset, holding_amount,
  holding_profit, daily_profit, position_rate, create_time, update_time, deleted
) VALUES
  (@demo_user_id, @demo_account_id, NULL, CURRENT_DATE - INTERVAL 4 DAY, 486200.0000, 486200.0000, 11200.0000, -1200.0000, 100.0000, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, NULL, CURRENT_DATE - INTERVAL 3 DAY, 492300.0000, 492300.0000, 17300.0000, 6100.0000, 100.0000, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, NULL, CURRENT_DATE - INTERVAL 2 DAY, 497600.0000, 497600.0000, 22600.0000, 5300.0000, 100.0000, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, NULL, CURRENT_DATE - INTERVAL 1 DAY, 498156.8000, 498156.8000, 23156.8000, 556.8000, 100.0000, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, NULL, CURRENT_DATE, 501921.0000, 501921.0000, 26921.0000, 3764.2000, 100.0000, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0);

INSERT INTO trade_record (
  user_id, account_id, holding_id, fund_code, fund_name, trade_type, trade_status,
  trade_amount, trade_share, trade_nav, trade_fee, trade_time, related_trade_id, remark,
  create_time, update_time, deleted
) VALUES
  (@demo_user_id, @demo_account_id, @holding_007689, '007689', '国投瑞银新能源混合A', 'BUY', 'COMPLETED', 15000.0000, 8141.1126, 1.842500, 1.5000, TIMESTAMP(CURRENT_DATE, '09:45:00'), NULL, '仅为模拟操作，并非真实交易', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, @holding_161725, '161725', '招商中证白酒指数A', 'REGULAR_INVEST', 'COMPLETED', 3000.0000, 2527.8059, 1.186800, 0.0000, TIMESTAMP(CURRENT_DATE, '10:30:00'), NULL, '定投扣款同步记录，仅为模拟操作', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, @holding_510300, '510300', '华泰柏瑞沪深300ETF', 'SELL', 'PROCESSING', 12000.0000, 2984.3323, 4.021000, 1.2000, TIMESTAMP(CURRENT_DATE, '14:35:00'), NULL, '减仓待确认，仅为模拟操作', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0);

INSERT INTO strategy_config (
  user_id, config_name, strategy_type, fund_type, params_json, enabled, create_time, update_time, deleted
) VALUES
  (@demo_user_id, '主动基金回撤止盈', 'DRAWDOWN_STOP_PROFIT', 'ACTIVE_EQUITY', JSON_OBJECT('profitActivationPct', 15, 'lightDrawdownPct', 3, 'mediumDrawdownPct', 5, 'heavyDrawdownPct', 8), 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, '权益仓位监控', 'POSITION_MONITOR', NULL, JSON_OBJECT('equityLimitPct', 70, 'singleFundLimitPct', 25, 'largeRisePct', 2), 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0);

INSERT INTO strategy_signal (
  user_id, account_id, holding_id, fund_code, signal_type, action, action_text,
  suggest_amount, suggest_ratio, risk_level, confidence, reason_json, signal_time,
  create_time, update_time, deleted
) VALUES
  (@demo_user_id, @demo_account_id, @holding_007689, '007689', 'POSITION_MONITOR', 'HOLD', '建议持有', 0.0000, 0.0000, 'MEDIUM', 0.7200, JSON_ARRAY('估值修复但单日波动仍偏高', '等待 15:00 前估值进一步确认'), TIMESTAMP(CURRENT_DATE, '14:30:00'), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, @demo_account_id, @holding_510300, '510300', 'POSITION_MONITOR', 'SELL', '建议轻度减仓', 12000.0000, 5.0000, 'HIGH', 0.6800, JSON_ARRAY('单基金仓位占比较高', '宽基 ETF 连续上涨后追高性价比下降'), TIMESTAMP(CURRENT_DATE, '14:40:00'), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0);

INSERT INTO ai_analysis_report (
  user_id, account_id, holding_id, fund_code, model_name, action, action_text,
  suggest_amount, suggest_ratio, confidence, risk_level, deadline, strategy,
  reasons_json, risks_json, data_summary, final_conclusion, request_payload,
  response_payload, fallback_used, analysis_time, create_time, update_time, deleted
) VALUES (
  @demo_user_id, @demo_account_id, @holding_510300, '510300', 'deepseek-v4-flash',
  'SELL', '建议轻度减仓', 12000.0000, 5.0000, 0.6800, 'HIGH', '15:00前',
  '仓位监控 / 固定分批止盈',
  JSON_ARRAY('组合权益仓位偏高', '华泰柏瑞沪深300ETF 单基金占比超过 25%'),
  JSON_ARRAY('当天估值不是最终净值', '减仓后若市场继续上涨，可能降低后续收益弹性'),
  '当前组合总资产 501921.00 元，单基金最大占比约 40.06%。',
  '可在原平台自行确认是否做小比例减仓，本系统仅记录模拟建议。',
  JSON_OBJECT('seed', true),
  JSON_OBJECT('action', 'SELL', 'fallback', true),
  1,
  TIMESTAMP(CURRENT_DATE, '14:45:00'),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3),
  0
);

INSERT INTO risk_profile (
  user_id, risk_level, max_equity_position_rate, max_single_fund_position_rate,
  drawdown_alert_rate, daily_rise_alert_rate, daily_fall_alert_rate, config_json,
  create_time, update_time, deleted
) VALUES (
  @demo_user_id, 'MEDIUM', 70.0000, 25.0000, 8.0000, 2.0000, 2.0000,
  JSON_OBJECT('demo', true, 'notice', '仅供参考，不构成投资建议，不承诺收益'),
  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
) ON DUPLICATE KEY UPDATE
  risk_level = VALUES(risk_level),
  max_equity_position_rate = VALUES(max_equity_position_rate),
  max_single_fund_position_rate = VALUES(max_single_fund_position_rate),
  update_time = CURRENT_TIMESTAMP(3),
  deleted = 0;

INSERT INTO data_source_config (
  user_id, source_name, base_url, timeout_ms, refresh_interval_seconds,
  rate_limit_per_minute, enabled, priority, config_json, create_time, update_time, deleted
) VALUES
  (@demo_user_id, 'MOCK_FALLBACK', 'local://mock', 3000, 120, 600, 1, 99, JSON_OBJECT('fallback', true), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (@demo_user_id, 'EAST_MONEY', 'https://api.fund.eastmoney.com', 5000, 120, 60, 1, 10, JSON_OBJECT('publicApi', true), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0);
