package com.lk.quantfund.vo.analytics;

import java.math.BigDecimal;

public record FundProfitRankVO(
        Long holdingId,
        String fundCode,
        String fundName,
        BigDecimal holdingAmount,
        BigDecimal holdingProfit,
        BigDecimal holdingProfitRate
) {
}
