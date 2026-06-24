package com.lk.quantfund.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.ai.model.AiAnalysisResult;
import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.StrategyAction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiResponseValidator {

    private final ObjectMapper objectMapper;

    public AiResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiAnalysisResult parseOrFallback(String rawResponse, String fallbackReason) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            StrategyAction action = StrategyAction.valueOf(requiredText(root, "action"));
            RiskLevel riskLevel = RiskLevel.valueOf(requiredText(root, "riskLevel"));
            String actionText = requiredText(root, "actionText");
            BigDecimal suggestAmount = decimal(root, "suggestAmount");
            BigDecimal suggestRatio = decimal(root, "suggestRatio");
            BigDecimal confidence = decimal(root, "confidence");
            if ((action == StrategyAction.BUY || action == StrategyAction.SELL) 
                    && suggestAmount.compareTo(BigDecimal.ZERO) <= 0 
                    && suggestRatio.compareTo(BigDecimal.ZERO) <= 0) {
                return fallback(rawResponse, "AI buy/sell result missing amount or ratio");
            }
            return new AiAnalysisResult(
                    action,
                    actionText,
                    suggestAmount,
                    suggestRatio,
                    confidence,
                    riskLevel,
                    textOrDefault(root, "deadline", "15:00前"),
                    textOrDefault(root, "strategy", "AI 综合分析"),
                    textList(root, "reasons"),
                    textList(root, "risks"),
                    textOrDefault(root, "dataSummary", ""),
                    textOrDefault(root, "finalConclusion", ""),
                    false,
                    rawResponse
            );
        } catch (Exception exception) {
            return fallback(rawResponse, fallbackReason + ": " + exception.getMessage());
        }
    }

    public AiAnalysisResult fallback(String rawResponse, String reason) {
        return new AiAnalysisResult(
                StrategyAction.WATCH,
                "建议观察",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.3000"),
                RiskLevel.MEDIUM,
                "15:00前",
                "AI 降级观察",
                List.of("AI 返回格式不合法或调用失败，系统已降级为 WATCH", reason),
                List.of("当日估值只作为参考，晚间正式净值前不是最终净值", "仅供参考，不构成投资建议，不承诺收益"),
                "AI 分析不可用，使用保守兜底结果。",
                "建议观察，等待更多可靠数据或稍后重试。",
                true,
                rawResponse == null ? "" : rawResponse
        );
    }

    private String requiredText(JsonNode root, String field) {
        String value = textOrDefault(root, field, null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("missing field: " + field);
        }
        return value;
    }

    private String textOrDefault(JsonNode root, String field, String fallback) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? fallback : node.asText();
    }

    private BigDecimal decimal(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(node.asText("0"));
    }

    private List<String> textList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(item.asText()));
        }
        return result;
    }
}

