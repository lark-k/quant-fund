package com.lk.quantfund.datasource.model;

import java.math.BigDecimal;

public record FundThemeDTO(
        String fundCode,
        String themeName,
        String themeType,
        BigDecimal weight,
        BigDecimal estimatedRate,
        String sourceName
) {
}
