package com.lk.quantfund.datasource.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundNavPointDTO(
        String fundCode,
        LocalDate navDate,
        BigDecimal unitNav,
        BigDecimal accumulatedNav,
        BigDecimal dailyGrowthRate,
        String sourceName
) {
}

