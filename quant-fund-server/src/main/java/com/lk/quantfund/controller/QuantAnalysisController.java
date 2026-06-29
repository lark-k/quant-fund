package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.DataScope;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.service.QuantAnalysisService;
import com.lk.quantfund.vo.quant.QuantEngineHealthVO;
import com.lk.quantfund.vo.quant.QuantSignalVO;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/quant")
public class QuantAnalysisController {

    private final QuantAnalysisService quantAnalysisService;

    public QuantAnalysisController(QuantAnalysisService quantAnalysisService) {
        this.quantAnalysisService = quantAnalysisService;
    }

    @GetMapping("/health")
    @RateLimit(key = "quant:health", windowSeconds = 60, maxRequests = 60)
    public ApiResponse<QuantEngineHealthVO> health() {
        return ApiResponse.success(quantAnalysisService.health());
    }

    @PostMapping("/holdings/{holdingId}/analyze")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "holdingId", operation = DataOperation.READ)
    @RateLimit(key = "quant:holding-analyze", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "quant", action = "analyze_holding", bizType = "QUANT_SIGNAL")
    public ApiResponse<QuantSignalVO> analyzeHolding(@PathVariable Long holdingId) {
        return ApiResponse.success(quantAnalysisService.analyzeHolding(holdingId));
    }

    @PostMapping("/accounts/{accountId}/analyze")
    @DataScope(resourceType = ResourceType.PORTFOLIO_ACCOUNT, idParam = "accountId", operation = DataOperation.READ)
    @RateLimit(key = "quant:account-analyze", windowSeconds = 60, maxRequests = 10)
    @RepeatSubmit(intervalSeconds = 10)
    @OperationLog(module = "quant", action = "analyze_account", bizType = "QUANT_SIGNAL")
    public ApiResponse<List<QuantSignalVO>> analyzeAccount(@PathVariable Long accountId) {
        return ApiResponse.success(quantAnalysisService.analyzeAccount(accountId));
    }

    @GetMapping("/signals")
    @RateLimit(key = "quant:signals", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<QuantSignalVO>> signals(@RequestParam(required = false) Long accountId,
                                                    @RequestParam(required = false) Long holdingId,
                                                    @RequestParam(required = false) String fundCode,
                                                    @RequestParam(required = false) StrategyAction action) {
        return ApiResponse.success(quantAnalysisService.latestSignals(accountId, holdingId, fundCode, action));
    }
}
