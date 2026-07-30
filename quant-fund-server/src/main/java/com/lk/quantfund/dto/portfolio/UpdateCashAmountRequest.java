package com.lk.quantfund.dto.portfolio;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateCashAmountRequest(
        @NotNull
        @DecimalMin("0.0000")
        @Digits(integer = 16, fraction = 4)
        BigDecimal cashAmount
) {
}
