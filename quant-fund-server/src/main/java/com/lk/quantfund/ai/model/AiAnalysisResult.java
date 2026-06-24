package com.lk.quantfund.ai.model;

import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.StrategyAction;
import java.math.BigDecimal;
import java.util.List;

public record AiAnalysisResult(
        StrategyAction action,
        String actionText,
        BigDecimal suggestAmount,
        BigDecimal suggestRatio,
        BigDecimal confidence,
        RiskLevel riskLevel,
        String deadline,
        String strategy,
        List<String> reasons,
        List<String> risks,
        String dataSummary,
        String finalConclusion,
        boolean fallbackUsed,
        String rawResponse
) {
}

