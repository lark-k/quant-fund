package com.lk.quantfund.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfitTrendPointVO(
        LocalDate date,
        BigDecimal totalAsset,
        BigDecimal dailyProfit,
        BigDecimal cumulativeProfit,
        BigDecimal dailyProfitRate,
        BigDecimal indexReturnRate,
        String profitStatus,
        String profitStatusText
) {
}
