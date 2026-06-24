package com.lk.quantfund.vo.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketIndexVO(
        String code,
        String name,
        BigDecimal latestPrice,
        BigDecimal changeValue,
        BigDecimal changeRate,
        BigDecimal turnover,
        LocalDateTime updateTime,
        String sourceName
) {
}

