package com.lk.quantfund.vo.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryVO(
        BigDecimal totalAsset,
        BigDecimal holdingMarketValue,
        BigDecimal cashAmount,
        BigDecimal totalInvestAmount,
        BigDecimal currentProfit,
        BigDecimal currentProfitRate,
        BigDecimal dailyProfit,
        BigDecimal equityPositionRate,
        BigDecimal bondPositionRate,
        BigDecimal cashPositionRate,
        Integer holdingCount,
        List<PortfolioAccountVO> accounts
) {
}
