USE quant_fund;

-- Keep one snapshot for each holding and snapshot date before adding the unique key.
-- The latest updated row wins; if update_time ties, the larger id wins.
DELETE hs
FROM holding_snapshot hs
JOIN (
  SELECT id
  FROM (
    SELECT
      id,
      ROW_NUMBER() OVER (
        PARTITION BY holding_id, snapshot_date
        ORDER BY update_time DESC, id DESC
      ) AS duplicate_rank
    FROM holding_snapshot
    WHERE holding_id IS NOT NULL
      AND deleted = 0
  ) ranked
  WHERE duplicate_rank > 1
) duplicate_rows ON duplicate_rows.id = hs.id;

ALTER TABLE holding_snapshot
  ADD UNIQUE KEY uk_holding_snapshot_holding_date (holding_id, snapshot_date);
