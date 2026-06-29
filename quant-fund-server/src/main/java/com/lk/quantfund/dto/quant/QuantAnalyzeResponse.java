package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record QuantAnalyzeResponse(
        String requestId,
        String fundCode,
        Long holdingId,
        String action,
        String actionText,
        BigDecimal suggestAmount,
        BigDecimal suggestRatio,
        BigDecimal confidence,
        String riskLevel,
        QuantScoreDTO score,
        Map<String, Object> metrics,
        List<String> reasons,
        List<String> risks,
        String modelName,
        String modelVersion,
        String deadline,
        String disclaimer
) {
}
