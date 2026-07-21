package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
        BigDecimal marketZz500ReturnRate,
        Boolean estimated,
        LocalDateTime observedAt,
        String navSource
) {
    public QuantNavPointDTO(LocalDate date,
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
                            BigDecimal marketZz500ReturnRate) {
        this(date, nav, accumulatedNav, dailyGrowthRate, indexReturnRate, indexCode, indexName,
                marketSh000001ReturnRate, marketSz399001ReturnRate, marketCyb399006ReturnRate,
                marketHs300ReturnRate, marketZz500ReturnRate, false, null, "OFFICIAL_NAV");
    }

    public QuantNavPointDTO(LocalDate date,
                            BigDecimal nav,
                            BigDecimal accumulatedNav,
                            BigDecimal dailyGrowthRate) {
        this(date, nav, accumulatedNav, dailyGrowthRate, null, null, null, null, null, null, null, null);
    }
}
