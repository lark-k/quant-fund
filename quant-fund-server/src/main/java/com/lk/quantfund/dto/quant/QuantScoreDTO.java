package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;

public record QuantScoreDTO(
        BigDecimal totalScore,
        BigDecimal trendScore,
        BigDecimal opportunityScore,
        BigDecimal riskScore,
        BigDecimal positionScore,
        BigDecimal momentumScore
) {
}
