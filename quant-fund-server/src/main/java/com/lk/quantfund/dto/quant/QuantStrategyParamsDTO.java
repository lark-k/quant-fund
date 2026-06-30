package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;

public record QuantStrategyParamsDTO(
        BigDecimal buyThreshold,
        BigDecimal sellThreshold,
        BigDecimal maxSinglePositionRate,
        BigDecimal buyStepRatio,
        BigDecimal sellStepRatio,
        BigDecimal takeProfitRate,
        BigDecimal stopLossRate,
        Integer minNavSamples,
        Integer warmupDays,
        BigDecimal trendHoldReturn20d,
        BigDecimal trendHoldMa20Deviation
) {
}
