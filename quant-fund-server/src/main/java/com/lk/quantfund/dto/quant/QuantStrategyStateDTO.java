package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;

public record QuantStrategyStateDTO(
        Integer weakTrendCandidateDays,
        Boolean weakTrendDefenseHandled,
        Integer weakTrendCooldownDays,
        Integer positionRebalanceCooldownDays,
        Boolean weakRecoveryRequired,
        Integer extremeRiskStage,
        String lastExtremeRiskDate,
        BigDecimal lastExtremeDrawdown,
        String lastActionDate,
        Integer extremeRiskSellCount,
        String lastDefenseDate
) {

    public static QuantStrategyStateDTO initial() {
        return new QuantStrategyStateDTO(0, false, 0, 0, false, 0, null, null, null, 0, null);
    }
}
