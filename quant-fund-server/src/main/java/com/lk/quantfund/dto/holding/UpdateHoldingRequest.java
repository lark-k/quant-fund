package com.lk.quantfund.dto.holding;

import com.lk.quantfund.enums.FundType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateHoldingRequest(
        @NotNull Long accountId,
        @NotBlank @Size(max = 32) String fundCode,
        @NotBlank @Size(max = 128) String fundName,
        @NotNull FundType fundType,
        @NotNull Boolean activeFund,
        @DecimalMin("0.0000") BigDecimal holdingAmount,
        @DecimalMin("0.0000") BigDecimal holdingShare,
        @DecimalMin("0.0000") BigDecimal holdingCost,
        BigDecimal holdingProfit,
        @DecimalMin("0.0000") BigDecimal currentEstimateNav,
        @DecimalMin("0.0000") BigDecimal latestOfficialNav,
        @Size(max = 64) String sourcePlatform,
        Boolean regularInvestment,
        Boolean coreHolding,
        Boolean watchFocus
) {
}
