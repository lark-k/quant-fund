package com.lk.quantfund.vo.screener;

import java.util.List;

public record FundScreenerValidationVO(
        String latestRunDate,
        String earliestScoreDate,
        String latestScoreDate,
        String status,
        String conclusion,
        List<String> calibrationAdvice,
        FundScreenerStrategyPolicyVO policy,
        List<FundScreenerBacktestMetricVO> lookbackMetrics,
        List<FundScreenerBacktestMetricVO> metrics
) {
}
