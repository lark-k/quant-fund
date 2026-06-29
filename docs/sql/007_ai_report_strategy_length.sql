USE quant_fund;

ALTER TABLE ai_analysis_report
  MODIFY COLUMN strategy VARCHAR(512) DEFAULT NULL COMMENT 'Triggered strategy';
