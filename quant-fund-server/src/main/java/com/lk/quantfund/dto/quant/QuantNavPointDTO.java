package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QuantNavPointDTO(
        LocalDate date,
        BigDecimal nav,
        BigDecimal accumulatedNav,
        BigDecimal dailyGrowthRate,
        BigDecimal indexReturnRate,
        String indexCode,
        String indexName,
        BigDecimal marketSh000001ReturnRate,
        BigDecimal marketSz399001ReturnRate,
        BigDecimal marketCyb399006ReturnRate,
        BigDecimal marketHs300ReturnRate,
        BigDecimal marketZz500ReturnRate
) {
    public QuantNavPointDTO(LocalDate date,
                            BigDecimal nav,
                            BigDecimal accumulatedNav,
                            BigDecimal dailyGrowthRate) {
        this(date, nav, accumulatedNav, dailyGrowthRate, null, null, null, null, null, null, null, null);
    }
}
