package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.BatchResponse;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.NavRefreshResponse;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.RunRequest;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.TrainingSampleExportResponse;
import com.lk.quantfund.service.QuantBacktestService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/backtests")
public class QuantBacktestController {

    private final QuantBacktestService quantBacktestService;

    public QuantBacktestController(QuantBacktestService quantBacktestService) {
        this.quantBacktestService = quantBacktestService;
    }

    @PostMapping("/run")
    @RateLimit(key = "backtest:run", windowSeconds = 60, maxRequests = 10)
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "backtest", action = "run_quant_backtest", bizType = "QUANT_BACKTEST")
    public ApiResponse<BatchResponse> run(@Valid @RequestBody RunRequest request) {
        return ApiResponse.success(quantBacktestService.run(request));
    }

    @PostMapping("/nav-cache/refresh")
    @RateLimit(key = "backtest:nav-refresh", windowSeconds = 60, maxRequests = 5)
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "backtest", action = "refresh_backtest_nav_cache", bizType = "QUANT_BACKTEST_NAV")
    public ApiResponse<NavRefreshResponse> refreshNavCache(@Valid @RequestBody RunRequest request) {
        return ApiResponse.success(quantBacktestService.refreshNavCache(request));
    }

    @PostMapping(value = "/ml-training-samples/export", produces = "text/csv")
    @RateLimit(key = "backtest:ml-samples-export", windowSeconds = 60, maxRequests = 5)
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "backtest", action = "export_ml_training_samples", bizType = "ML_TRAINING_SAMPLE")
    public ResponseEntity<byte[]> exportMlTrainingSamples(@Valid @RequestBody RunRequest request) {
        TrainingSampleExportResponse response = quantBacktestService.exportTrainingSamples(request);
        String fileName = response.fileName() == null || response.fileName().isBlank()
                ? "quantfund_ml_training_samples.csv"
                : response.fileName();
        byte[] body = ("\uFEFF" + response.csvContent()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(body);
    }
}
