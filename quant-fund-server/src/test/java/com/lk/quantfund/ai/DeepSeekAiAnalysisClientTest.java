package com.lk.quantfund.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.ai.model.AiAnalysisContext;
import com.lk.quantfund.config.QuantFundProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class DeepSeekAiAnalysisClientTest {

    @Test
    void analyzeShouldFallbackWhenMockEnabled() {
        QuantFundProperties properties = new QuantFundProperties();
        properties.getAi().setMockEnabled(true);
        properties.getAi().setApiKey("secret-key");
        DeepSeekAiAnalysisClient client = new DeepSeekAiAnalysisClient(
                WebClient.builder(),
                properties,
                mock(AiPromptBuilder.class),
                new AiResponseValidator(new ObjectMapper()),
                new ObjectMapper()
        );

        var result = client.analyze(context());

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.reasons()).anySatisfy(reason ->
                assertThat(reason).contains("DEEPSEEK_MOCK_ENABLED=true"));
    }

    private AiAnalysisContext context() {
        return new AiAnalysisContext(
                10L,
                100L,
                "000001",
                "华夏成长混合",
                "MIXED",
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "MEDIUM",
                null,
                List.of()
        );
    }
}
