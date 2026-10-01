package com.lk.quantfund.dto.backtest;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record NavBacktestRequest(
    @NotNull LocalDate startDate, @NotNull LocalDate endDate,
    @NotNull @DecimalMin("100") @DecimalMax("100000000") BigDecimal initialCash,
    @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("100") BigDecimal buyPercent,
    @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("100") BigDecimal sellPercent,
    @NotNull @DecimalMin("0") @DecimalMax("5") BigDecimal buyFee,
    @NotNull @DecimalMin("0") @DecimalMax("5") BigDecimal shortSellFee,
    @NotNull @DecimalMin("0") @DecimalMax("5") BigDecimal mediumSellFee,
    @NotNull @DecimalMin("0") @DecimalMax("5") BigDecimal sellFee,
    @NotNull @Min(1) @Max(5) Integer disclosureDelay,
    @NotNull @Min(1) @Max(10) Integer confirmDelay,
    @NotNull @Min(1) @Max(20) Integer settlementDelay,
    @DecimalMin("0") @DecimalMax("100") BigDecimal initialPositionPercent,
    @Pattern(regexp="NAV-TA v1|NAV-TA v2-balanced|NAV-TA v3[.]1-trend") String ruleVersion
) {
    public NavBacktestRequest {
        if (initialPositionPercent == null) initialPositionPercent = BigDecimal.ZERO;
        if (ruleVersion == null) ruleVersion = "NAV-TA v1";
    }
}
