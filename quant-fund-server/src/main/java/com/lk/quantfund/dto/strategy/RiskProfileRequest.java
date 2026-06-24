package com.lk.quantfund.dto.strategy;

import com.lk.quantfund.enums.RiskLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RiskProfileRequest(
        @NotNull RiskLevel riskLevel,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal maxEquityPositionRate,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal maxSingleFundPositionRate,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal drawdownAlertRate,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal dailyRiseAlertRate,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal dailyFallAlertRate,
        String configJson
) {
}

