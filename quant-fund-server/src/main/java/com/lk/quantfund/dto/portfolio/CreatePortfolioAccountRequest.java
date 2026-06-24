package com.lk.quantfund.dto.portfolio;

import com.lk.quantfund.enums.PlatformType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePortfolioAccountRequest(
        @NotBlank @Size(max = 128) String accountName,
        @NotNull PlatformType platformType,
        @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal maxSingleFundPositionRate
) {
}

