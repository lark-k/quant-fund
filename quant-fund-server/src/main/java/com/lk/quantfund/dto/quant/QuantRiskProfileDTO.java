package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;

public record QuantRiskProfileDTO(
        String riskLevel,
        BigDecimal maxEquityPositionRate,
        BigDecimal maxSingleFundPositionRate,
        BigDecimal drawdownAlertRate,
        BigDecimal dailyRiseAlertRate,
        BigDecimal dailyFallAlertRate
) {
}
