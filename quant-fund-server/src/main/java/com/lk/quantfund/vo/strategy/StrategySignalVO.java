package com.lk.quantfund.vo.strategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StrategySignalVO(
        Long id,
        Long accountId,
        Long holdingId,
        String fundCode,
        String signalType,
        String action,
        String actionText,
        BigDecimal suggestAmount,
        BigDecimal suggestRatio,
        String riskLevel,
        BigDecimal confidence,
        List<String> reasons,
        LocalDateTime signalTime,
        String disclaimer
) {
}

