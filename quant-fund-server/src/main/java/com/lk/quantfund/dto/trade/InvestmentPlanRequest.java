package com.lk.quantfund.dto.trade;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentPlanRequest(
        @NotNull Long accountId,
        @NotBlank @Size(max = 32) String fundCode,
        @NotBlank @Size(max = 128) String fundName,
        @Size(max = 128) String planName,
        @NotNull @DecimalMin("0.0100") BigDecimal amount,
        @NotBlank @Size(max = 32) String frequency,
        @NotNull LocalDate nextExecuteDate,
        @Size(max = 32) String status
) {
}
