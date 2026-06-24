package com.lk.quantfund.datasource.model;

import java.math.BigDecimal;

public record FundPeerRankDTO(
        String fundCode,
        String rankText,
        String category,
        Integer rank,
        Integer total,
        BigDecimal percentile,
        String period,
        String sourceName
) {
}
