package com.lk.quantfund.vo.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketIndexIntradayPointVO(
        String indexCode,
        String indexName,
        LocalDateTime time,
        BigDecimal price,
        BigDecimal returnRate,
        String sourceName
) {
}
