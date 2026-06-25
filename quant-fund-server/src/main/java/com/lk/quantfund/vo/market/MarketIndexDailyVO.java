package com.lk.quantfund.vo.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketIndexDailyVO(
        String indexCode,
        String indexName,
        LocalDate tradeDate,
        BigDecimal closePrice,
        BigDecimal dailyChangeRate,
        String sourceName
) {
}
