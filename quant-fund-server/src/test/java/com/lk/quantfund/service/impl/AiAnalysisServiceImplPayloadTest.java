package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.ai.AiAnalysisClient;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.mapper.AiAnalysisReportMapper;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.RiskProfileMapper;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.service.QuantAnalysisService;
import com.lk.quantfund.service.StrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiAnalysisServiceImplPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiAnalysisServiceImpl service = new AiAnalysisServiceImpl(
            mock(AiAnalysisClient.class),
            mock(StrategyService.class),
            mock(QuantAnalysisService.class),
            new QuantFundProperties(),
            objectMapper,
            mock(AiAnalysisReportMapper.class),
            mock(FundHoldingMapper.class),
            mock(PortfolioAccountMapper.class),
            mock(RiskProfileMapper.class),
            mock(StrategySignalMapper.class)
    );

    @Test
    void blankRawResponseIsStoredAsSqlNull() {
        String payload = ReflectionTestUtils.invokeMethod(service, "normalizeResponsePayload", "");

        assertThat(payload).isNull();
    }

    @Test
    void validJsonResponseRemainsValidJson() throws Exception {
        String payload = ReflectionTestUtils.invokeMethod(
                service,
                "normalizeResponsePayload",
                "{\"action\":\"WATCH\",\"reason\":\"ok\"}"
        );

        JsonNode parsed = objectMapper.readTree(payload);
        assertThat(parsed.path("action").asText()).isEqualTo("WATCH");
        assertThat(parsed.path("reason").asText()).isEqualTo("ok");
    }

    @Test
    void invalidJsonResponseIsWrappedAsValidJson() throws Exception {
        String payload = ReflectionTestUtils.invokeMethod(service, "normalizeResponsePayload", "upstream timeout");

        JsonNode parsed = objectMapper.readTree(payload);
        assertThat(parsed.path("rawResponse").asText()).isEqualTo("upstream timeout");
    }
}
