package com.lk.quantfund.datasource.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundNavPointDTO(
        String fundCode,
        LocalDate navDate,
        BigDecimal unitNav,
        BigDecimal accumulatedNav,
        BigDecimal dailyGrowthRate,
        String sourceName,
        BigDecimal indexReturnRate,
        String indexCode,
        String indexName
) {
    public FundNavPointDTO(String fundCode,
                           LocalDate navDate,
                           BigDecimal unitNav,
                           BigDecimal accumulatedNav,
                           BigDecimal dailyGrowthRate,
                           String sourceName) {
        this(fundCode, navDate, unitNav, accumulatedNav, dailyGrowthRate, sourceName, null, null, null);
    }
}
