package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QuantNavPointDTO(
        LocalDate date,
        BigDecimal nav,
        BigDecimal accumulatedNav,
        BigDecimal dailyGrowthRate
) {
}
