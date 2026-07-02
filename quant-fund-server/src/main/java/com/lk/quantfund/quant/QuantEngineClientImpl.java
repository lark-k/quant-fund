package com.lk.quantfund.quant;

import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.BatchResponse;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.EngineBatchRequest;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.TrainingSampleExportRequest;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.TrainingSampleExportResponse;
import com.lk.quantfund.dto.quant.QuantAnalyzeBatchRequest;
import com.lk.quantfund.dto.quant.QuantAnalyzeBatchResponse;
import com.lk.quantfund.dto.quant.QuantAnalyzeRequest;
import com.lk.quantfund.dto.quant.QuantAnalyzeResponse;
import com.lk.quantfund.service.ApiCallLogService;
import com.lk.quantfund.vo.quant.QuantEngineHealthVO;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class QuantEngineClientImpl implements QuantEngineClient {

    private static final String PROVIDER = "QUANT_ENGINE";

    private final WebClient.Builder webClientBuilder;
    private final QuantFundProperties properties;
    private final ApiCallLogService apiCallLogService;

    public QuantEngineClientImpl(WebClient.Builder webClientBuilder,
                                 QuantFundProperties properties,
                                 ApiCallLogService apiCallLogService) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.apiCallLogService = apiCallLogService;
    }

    @Override
    public QuantAnalyzeResponse analyze(QuantAnalyzeRequest request) {
        return post(
                "quant_analyze",
                "/api/v1/quant/analyze",
                request,
                QuantAnalyzeResponse.class,
                Duration.ofMillis(properties.getQuantEngine().getTimeoutMs())
        );
    }

    @Override
    public List<QuantAnalyzeResponse> analyzeBatch(List<QuantAnalyzeRequest> requests) {
        QuantAnalyzeBatchRequest batchRequest = new QuantAnalyzeBatchRequest(
                "qf-batch-" + UUID.randomUUID(),
                requests
        );
        QuantAnalyzeBatchResponse response = post(
                "quant_analyze_batch",
                "/api/v1/quant/analyze-batch",
                batchRequest,
                QuantAnalyzeBatchResponse.class,
                Duration.ofMillis(properties.getQuantEngine().getBatchTimeoutMs())
        );
        return response == null || response.results() == null ? List.of() : response.results();
    }

    @Override
    public BatchResponse runBacktestBatch(EngineBatchRequest request) {
        return post(
                "quant_backtest_batch",
                "/api/v1/backtest/run-batch",
                request,
                BatchResponse.class,
                Duration.ofMillis(properties.getQuantEngine().getBacktestTimeoutMs())
        );
    }

    @Override
    public TrainingSampleExportResponse exportTrainingSamples(TrainingSampleExportRequest request) {
        return post(
                "ml_training_samples_export",
                "/api/v1/ml/training-samples/export",
                request,
                TrainingSampleExportResponse.class,
                Duration.ofMillis(properties.getQuantEngine().getBacktestTimeoutMs())
        );
    }

    @Override
    public QuantEngineHealthVO health() {
        long start = System.currentTimeMillis();
        String url = properties.getQuantEngine().getBaseUrl() + "/api/v1/health";
        try {
            QuantEngineHealthVO response = client()
                    .get()
                    .uri("/api/v1/health")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new QuantEngineException("Quant engine health failed: " + body))))
                    .bodyToMono(QuantEngineHealthVO.class)
                    .timeout(Duration.ofMillis(properties.getQuantEngine().getTimeoutMs()))
                    .block();
            apiCallLogService.record(PROVIDER, "quant_health", url, "GET", true, 200, null, elapsed(start), false);
            if (response == null) {
                return new QuantEngineHealthVO("DOWN", "quant-engine", properties.getQuantEngine().getModelVersion(), properties.getQuantEngine().isEnabled());
            }
            return new QuantEngineHealthVO(response.status(), response.service(), response.modelVersion(), properties.getQuantEngine().isEnabled());
        } catch (RuntimeException exception) {
            apiCallLogService.record(PROVIDER, "quant_health", url, "GET", false, statusCode(exception), exception.getMessage(), elapsed(start), false);
            throw new QuantEngineException("Quant engine health request failed", exception);
        }
    }

    private <T> T post(String apiName, String path, Object request, Class<T> responseType, Duration timeout) {
        long start = System.currentTimeMillis();
        String url = properties.getQuantEngine().getBaseUrl() + path;
        try {
            T response = client()
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new QuantEngineException("Quant engine request failed: " + body))))
                    .bodyToMono(responseType)
                    .timeout(timeout)
                    .block();
            apiCallLogService.record(PROVIDER, apiName, url, "POST", true, 200, null, elapsed(start), false);
            return response;
        } catch (RuntimeException exception) {
            apiCallLogService.record(PROVIDER, apiName, url, "POST", false, statusCode(exception), exception.getMessage(), elapsed(start), false);
            throw new QuantEngineException("Quant engine request failed: " + apiName, exception);
        }
    }

    private WebClient client() {
        int responseMaxBytes = properties.getQuantEngine().getResponseMaxInMemoryMb() * 1024 * 1024;
        return webClientBuilder.clone()
                .baseUrl(properties.getQuantEngine().getBaseUrl())
                .defaultHeader("User-Agent", "QuantFund/0.1.0")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(responseMaxBytes))
                .build();
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private Integer statusCode(RuntimeException exception) {
        if (exception instanceof WebClientResponseException webClientResponseException) {
            return webClientResponseException.getStatusCode().value();
        }
        Throwable cause = exception.getCause();
        if (cause instanceof WebClientResponseException webClientResponseException) {
            return webClientResponseException.getStatusCode().value();
        }
        return null;
    }
}
