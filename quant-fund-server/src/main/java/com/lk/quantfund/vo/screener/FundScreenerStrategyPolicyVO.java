package com.lk.quantfund.vo.screener;

import java.math.BigDecimal;

public record FundScreenerStrategyPolicyVO(
        BigDecimal strongMinScore,
        int strongTopPercent,
        BigDecimal watchMinScore,
        int watchTopPercent,
        BigDecimal neutralMinScore,
        int minValidationSamples,
        int minValidationScoreDates
) {
}
