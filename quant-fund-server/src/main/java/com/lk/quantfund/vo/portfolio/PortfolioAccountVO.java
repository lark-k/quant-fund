package com.lk.quantfund.vo.portfolio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioAccountVO(
        Long id,
        String accountName,
        String platformType,
        BigDecimal totalAsset,
        BigDecimal totalInvestAmount,
        BigDecimal currentProfit,
        BigDecimal currentProfitRate,
        BigDecimal dailyProfit,
        BigDecimal cashPositionRate,
        BigDecimal equityPositionRate,
        BigDecimal bondPositionRate,
        BigDecimal maxSingleFundPositionRate,
        String status,
        LocalDateTime updateTime
) {
}

