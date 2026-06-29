package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;

public record QuantAccountContextDTO(
        Long accountId,
        BigDecimal totalAsset,
        BigDecimal totalInvestAmount,
        BigDecimal currentProfit,
        BigDecimal currentProfitRate,
        BigDecimal dailyProfit,
        BigDecimal equityPositionRate,
        BigDecimal maxSingleFundPositionRate
) {
}
