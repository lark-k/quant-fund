package com.lk.quantfund.vo.quant;

import java.math.BigDecimal;

public record QuantScoreVO(
        BigDecimal totalScore,
        BigDecimal trendScore,
        BigDecimal opportunityScore,
        BigDecimal riskScore,
        BigDecimal positionScore,
        BigDecimal momentumScore
) {
}
