package com.lk.quantfund.strategy.context;

import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.SignalType;
import com.lk.quantfund.enums.StrategyAction;
import java.math.BigDecimal;
import java.util.List;

public record StrategySignalDraft(
        SignalType signalType,
        StrategyAction action,
        String actionText,
        BigDecimal suggestAmount,
        BigDecimal suggestRatio,
        RiskLevel riskLevel,
        BigDecimal confidence,
        List<String> reasons
) {
}

