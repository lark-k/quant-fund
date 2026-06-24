package com.lk.quantfund.service.valuation;

import java.math.BigDecimal;

public record FundValuationResult(
        String themeName,
        BigDecimal themeRate,
        String sourceName,
        String basis,
        String marketStatus
) {
}
