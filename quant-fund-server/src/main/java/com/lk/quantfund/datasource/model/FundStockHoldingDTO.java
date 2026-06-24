package com.lk.quantfund.datasource.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundStockHoldingDTO(
        String fundCode,
        String stockCode,
        String stockName,
        BigDecimal positionRate,
        String industry,
        BigDecimal latestPrice,
        BigDecimal changeRate,
        String marketSecId,
        LocalDate reportDate,
        String sourceName
) {
}
