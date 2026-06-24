package com.lk.quantfund.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.enums.StrategyAction;
import org.junit.jupiter.api.Test;

class AiResponseValidatorTest {

    private final AiResponseValidator validator = new AiResponseValidator(new ObjectMapper());

    @Test
    void parseShouldFallbackWhenBuyHasNoAmountOrRatio() {
        String json = """
                {
                  "action": "BUY",
                  "actionText": "建议加仓",
                  "suggestAmount": 0,
                  "suggestRatio": 0,
                  "confidence": 0.8,
                  "riskLevel": "MEDIUM",
                  "deadline": "15:00前",
                  "strategy": "AI 综合分析",
                  "reasons": ["测试"],
                  "risks": [],
                  "dataSummary": "",
                  "finalConclusion": ""
                }
                """;

        var result = validator.parseOrFallback(json, "invalid");

        assertThat(result.action()).isEqualTo(StrategyAction.WATCH);
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void parseShouldAcceptValidWatchJson() {
        String json = """
                {
                  "action": "WATCH",
                  "actionText": "建议观察",
                  "suggestAmount": 0,
                  "suggestRatio": 0,
                  "confidence": 0.6,
                  "riskLevel": "LOW",
                  "deadline": "15:00前",
                  "strategy": "观察",
                  "reasons": ["未触发强信号"],
                  "risks": ["估值仅供参考"],
                  "dataSummary": "summary",
                  "finalConclusion": "watch"
                }
                """;

        var result = validator.parseOrFallback(json, "invalid");

        assertThat(result.action()).isEqualTo(StrategyAction.WATCH);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.reasons()).contains("未触发强信号");
    }
}

