package com.lk.quantfund.vo.quant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record QuantSignalVO(
        Long id,
        Long accountId,
        Long holdingId,
        String fundCode,
        String fundName,
        String action,
        String actionText,
        BigDecimal suggestAmount,
        BigDecimal suggestRatio,
        String riskLevel,
        BigDecimal confidence,
        BigDecimal totalScore,
        BigDecimal trendScore,
        BigDecimal opportunityScore,
        BigDecimal riskScore,
        BigDecimal positionScore,
        BigDecimal momentumScore,
        List<String> reasons,
        List<String> risks,
        String metricsJson,
        String modelName,
        String modelVersion,
        String deadline,
        LocalDateTime signalTime,
        boolean fallbackUsed,
        String disclaimer
) {
}
