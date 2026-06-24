package com.lk.quantfund.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.vo.market.MarketIndexVO;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class MarketDataServiceImpl implements MarketDataService {

    private static final String SOURCE_NAME = "EAST_MONEY";
    private static final String DEFAULT_INDEX_SECIDS = "1.000001,0.399001,0.399006,1.000905";

    private final WebClient.Builder webClientBuilder;
    private final QuantFundProperties properties;
    private final ObjectMapper objectMapper;

    public MarketDataServiceImpl(WebClient.Builder webClientBuilder,
                                 QuantFundProperties properties,
                                 ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MarketIndexVO> marketReadings() {
        try {
            String response = webClientBuilder.build()
                    .get()
                    .uri(builder -> builder
                            .scheme("https")
                            .host("push2.eastmoney.com")
                            .path("/api/qt/ulist.np/get")
                            .queryParam("fltt", "2")
                            .queryParam("secids", DEFAULT_INDEX_SECIDS)
                            .queryParam("fields", "f12,f14,f2,f3,f4,f6")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(properties.getFundDataSource().getTimeoutMs()))
                    .block();
            return parse(response);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<MarketIndexVO> parse(String response) {
        try {
            JsonNode diff = objectMapper.readTree(response).path("data").path("diff");
            if (!diff.isArray()) {
                return List.of();
            }
            List<MarketIndexVO> result = new ArrayList<>();
            for (JsonNode row : diff) {
                result.add(new MarketIndexVO(
                        row.path("f12").asText(),
                        row.path("f14").asText(),
                        decimal(row.path("f2")),
                        decimal(row.path("f4")),
                        decimal(row.path("f3")),
                        decimal(row.path("f6")),
                        LocalDateTime.now(),
                        SOURCE_NAME
                ));
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || "-".equals(node.asText())) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(node.asText()).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
