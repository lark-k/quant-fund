package com.lk.quantfund.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lk.quantfund.annotation.*;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.backtest.NavBacktestRequest;
import com.lk.quantfund.service.NavTechnicalService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/holdings/{holdingId}/nav-technical")
public class NavTechnicalController {
    private final NavTechnicalService service;
    public NavTechnicalController(NavTechnicalService service) { this.service = service; }

    @PostMapping("/analyze")
    @RateLimit(key="nav-technical:analyze", windowSeconds=60, maxRequests=20)
    public ApiResponse<JsonNode> analyze(@PathVariable long holdingId) { return ApiResponse.success(service.analyze(holdingId)); }

    @PostMapping("/backtests")
    @RateLimit(key="nav-technical:backtest", windowSeconds=60, maxRequests=4)
    @RepeatSubmit(intervalSeconds=5)
    public ApiResponse<JsonNode> run(@PathVariable long holdingId, @Valid @RequestBody NavBacktestRequest request) {
        return ApiResponse.success(service.run(holdingId, request));
    }

    @GetMapping("/backtests")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable long holdingId) { return ApiResponse.success(service.history(holdingId)); }

    @GetMapping("/backtests/{id}")
    public ApiResponse<JsonNode> result(@PathVariable long holdingId, @PathVariable String id) { return ApiResponse.success(service.result(holdingId, id)); }
}
