package com.lk.quantfund.dto.strategy;

import com.lk.quantfund.enums.FundType;
import com.lk.quantfund.enums.StrategyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StrategyConfigRequest(
        @NotBlank @Size(max = 128) String configName,
        @NotNull StrategyType strategyType,
        FundType fundType,
        @NotBlank String paramsJson,
        @NotNull Boolean enabled
) {
}

