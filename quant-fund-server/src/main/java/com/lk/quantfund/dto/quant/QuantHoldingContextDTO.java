package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;

public record QuantHoldingContextDTO(
        Long holdingId,
        String fundCode,
        String fundName,
        String fundType,
        Boolean activeFund,
        BigDecimal holdingAmount,
        BigDecimal holdingShare,
        BigDecimal holdingCost,
        BigDecimal holdingProfit,
        BigDecimal holdingProfitRate,
        BigDecimal dailyProfit,
        BigDecimal positionRate,
        BigDecimal currentEstimateNav,
        BigDecimal latestOfficialNav,
        BigDecimal currentEstimateGrowthRate,
        String relatedThemeName,
        BigDecimal relatedThemeRate,
        String marketStatus,
        Integer holdingDays,
        Boolean coreHolding,
        Boolean watchFocus
) {
}
