package com.lk.quantfund.datasource.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FundEstimateDTO(
        String fundCode,
        String fundName,
        BigDecimal estimateNav,
        BigDecimal estimateGrowthRate,
        LocalDate estimateDate,
        LocalDateTime estimateTime,
        String sourceName,
        boolean delayed,
        String rawPayload
) {
}

