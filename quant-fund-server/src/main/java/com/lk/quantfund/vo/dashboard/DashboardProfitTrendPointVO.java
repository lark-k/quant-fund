package com.lk.quantfund.vo.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardProfitTrendPointVO(
        LocalDate date,
        BigDecimal totalAsset,
        BigDecimal holdingProfit,
        BigDecimal dailyProfit,
        BigDecimal indexReturnRate,
        String profitStatus,
        String profitStatusText
) {
}
