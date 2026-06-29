package com.lk.quantfund.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.ai.model.AiAnalysisContext;
import com.lk.quantfund.ai.model.AiAnalysisResult;
import com.lk.quantfund.config.QuantFundProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DeepSeekAiAnalysisClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiAnalysisClient.class);

    private final WebClient.Builder webClientBuilder;
    private final QuantFundProperties properties;
    private final AiPromptBuilder promptBuilder;
    private final AiResponseValidator responseValidator;
    private final ObjectMapper objectMapper;

    public DeepSeekAiAnalysisClient(WebClient.Builder webClientBuilder,
                                    QuantFundProperties properties,
                                    AiPromptBuilder promptBuilder,
                                    AiResponseValidator responseValidator,
                                    ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.responseValidator = responseValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAnalysisResult analyze(AiAnalysisContext context) {
        QuantFundProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled()) {
            return responseValidator.fallback("", "AI is disabled");
        }
        if (ai.isMockEnabled()) {
            return responseValidator.fallback("", "DEEPSEEK_MOCK_ENABLED=true");
        }
        if (!StringUtils.hasText(ai.getApiKey())) {
            return responseValidator.fallback("", "DeepSeek API key is not configured");
        }
        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(ai.getBaseUrl()).build();
            Map<String, Object> request = buildRequest(ai, context);
            String response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(ai.getApiKey()))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(ai.getTimeoutMs()))
                    .block();
            String content = extractContent(response);
            return responseValidator.parseOrFallback(content, "AI response invalid");
        } catch (RuntimeException exception) {
            String reason = "DeepSeek call failed: " + readableMessage(exception);
            log.warn("DeepSeek analysis failed for fund {}: {}", context.fundCode(), reason);
            return responseValidator.fallback("", reason);
        }
    }

    private Map<String, Object> buildRequest(QuantFundProperties.Ai ai, AiAnalysisContext context) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", promptBuilder.systemPrompt()));
        messages.add(Map.of("role", "user", "content", promptBuilder.userPrompt(context)));
        Map<String, Object> request = new HashMap<>();
        request.put("model", ai.getModel());
        request.put("messages", messages);
        request.put("stream", false);
        request.put("max_tokens", ai.getMaxTokens());
        request.put("response_format", Map.of("type", "json_object"));
        if (ai.isReasoningEnabled()) {
            request.put("thinking", Map.of("type", "enabled"));
            request.put("reasoning_effort", "high");
        }
        return request;
    }

    private String extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return response;
            }
            JsonNode content = choices.get(0).path("message").path("content");
            return content.isMissingNode() ? response : content.asText();
        } catch (Exception exception) {
            return response;
        }
    }

    private String readableMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message) && exception.getCause() != null) {
            message = exception.getCause().getMessage();
        }
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
    }
}
