package com.lk.quantfund.vo.ai;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AiAnalysisReportVO(
        Long id,
        Long accountId,
        Long holdingId,
        String fundCode,
        String modelName,
        String action,
        String actionText,
        BigDecimal suggestAmount,
        BigDecimal suggestRatio,
        BigDecimal confidence,
        String riskLevel,
        String deadline,
        String strategy,
        List<String> reasons,
        List<String> risks,
        String dataSummary,
        String finalConclusion,
        Boolean fallbackUsed,
        LocalDateTime analysisTime,
        String disclaimer
) {
}

