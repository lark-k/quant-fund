package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;

public record QuantMetricsDTO(
        BigDecimal return20d,
        BigDecimal maxDrawdown60d,
        BigDecimal volatility20d,
        BigDecimal positionRate,
        BigDecimal themeRate,
        Integer navSampleSize
) {
}
