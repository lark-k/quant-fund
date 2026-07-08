# Fund Screener V2 Design

## Goal

Upgrade fund screener from a recent-performance shortlist into a more reliable, explainable fund selection module that can identify market-quality candidates for further human review.

## Scope

- Rank only each fund's latest score snapshot.
- Add v2 factors for return quality, risk control, consistency, investability, and benchmark-relative performance.
- Rework scoring into a transparent rule model with fund-type-aware weights.
- Add backtest validation summaries so the model can be judged by forward performance.
- Surface the new score dimensions in the screener page and explanation drawer.

## Non-Goals

- No direct buy/sell instruction.
- No machine-learning model in this phase.
- No reliance on opaque external ratings.

## Data Model

Extend `screener_factor_snapshot` with:

- `benchmark_code`
- `return_drawdown_ratio_120d`
- `return_consistency_score`
- `fund_age_years`

Extend `screener_quality_score` with:

- `return_quality_score`
- `drawdown_control_score`
- `consistency_score`
- `investability_score`

Create `screener_backtest_result` for validation runs:

- `id`
- `run_date`
- `score_date`
- `horizon_days`
- `bucket_name`
- `sample_count`
- `avg_forward_return`
- `win_rate`
- `avg_excess_return`
- `max_drawdown`
- `model_version`
- timestamps and logical delete

## Algorithm

The v2 model keeps a transparent rules-based score. It still uses NAV-derived factors, but avoids ranking by short-term return alone.

Return quality:

- 60/120/250-day return with a cap to reduce momentum chasing.
- Annual return and return-drawdown ratio.
- Multi-window consistency.

Risk control:

- 120-day drawdown.
- 120-day annualized volatility.
- Return-drawdown ratio.

Relative performance:

- Benchmark-relative 60/120-day excess return.
- Peer percentile within the same universe type.

Investability:

- Data completeness.
- Fund size when available.
- Fund age and fund status.
- Share class filtering remains outside score.

Recommendation level:

- `STRONG`: score at least 82 and rank percentile at or above top 8%.
- `WATCH`: score at least 72 or rank percentile at or above top 20%.
- `NEUTRAL`: score at least 58.
- `AVOID`: below 58.

## Benchmark Mapping

Initial benchmark mapping is internal and deterministic:

- `ACTIVE_EQUITY`: `000985`, all-share style broad equity proxy.
- `MIXED`: `000300`, broad equity proxy until better mixed-fund benchmark data is added.
- `INDEX`: use tracking index when available, otherwise `000300`.

If benchmark NAV data is unavailable in local screener NAV storage, excess return falls back to 0 and the score explains that relative performance is not yet fully available.

## Backtest Validation

The backtest groups historical score rows by score bucket and computes future performance over 20, 60, and 120 NAV observations when available.

Buckets:

- `TOP_5`
- `TOP_10`
- `WATCH`
- `NEUTRAL`
- `AVOID`

Metrics:

- sample count
- average forward return
- win rate
- average excess return versus all available samples on the same score date and horizon
- max drawdown during the forward window

## UI

The screener table adds the new score dimensions so users can tell why a fund ranks highly:

- return quality
- risk control
- consistency
- investability

The explanation drawer shows those same dimensions plus benchmark code and return-drawdown ratio where available.

## Testing

- Unit tests for latest-score ranking.
- Unit tests for factor calculation of drawdown ratio, consistency, age, and benchmark code.
- Unit tests for v2 score dimensions and recommendation thresholds.
- Unit tests for backtest bucket summaries.
- Frontend build verification.
- Full backend test suite.
