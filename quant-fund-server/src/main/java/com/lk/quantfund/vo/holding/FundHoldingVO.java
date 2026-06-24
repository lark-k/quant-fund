package com.lk.quantfund.vo.holding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FundHoldingVO(
        Long id,
        Long accountId,
        String fundCode,
        String fundName,
        String fundType,
        Boolean activeFund,
        BigDecimal holdingAmount,
        BigDecimal holdingShare,
        BigDecimal holdingCost,
        BigDecimal currentEstimateNav,
        BigDecimal latestOfficialNav,
        BigDecimal holdingProfit,
        BigDecimal holdingProfitRate,
        BigDecimal dailyProfit,
        BigDecimal yesterdayProfit,
        BigDecimal positionRate,
        BigDecimal currentEstimateGrowthRate,
        Boolean officialNavUpdated,
        LocalDate officialNavDate,
        String relatedThemeName,
        BigDecimal relatedThemeRate,
        String valuationSource,
        String estimateBasis,
        String marketStatus,
        Integer holdingDays,
        String sourcePlatform,
        Boolean regularInvestment,
        Boolean coreHolding,
        Boolean watchFocus,
        LocalDateTime updateTime,
        String disclaimer
) {
}
