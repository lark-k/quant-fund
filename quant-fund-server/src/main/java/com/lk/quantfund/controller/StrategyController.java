package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.DataScope;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.strategy.RiskProfileRequest;
import com.lk.quantfund.dto.strategy.StrategyConfigRequest;
import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.service.StrategyService;
import com.lk.quantfund.vo.strategy.RiskProfileVO;
import com.lk.quantfund.vo.strategy.StrategyAnalysisVO;
import com.lk.quantfund.vo.strategy.StrategyConfigVO;
import com.lk.quantfund.vo.strategy.StrategySignalVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/strategies")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @PostMapping("/holdings/{holdingId}/analyze")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "holdingId", operation = DataOperation.READ)
    @RateLimit(key = "strategy:holding-analyze", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "strategy", action = "analyze_holding", bizType = "STRATEGY_SIGNAL")
    public ApiResponse<StrategyAnalysisVO> analyzeHolding(@PathVariable Long holdingId) {
        return ApiResponse.success(strategyService.analyzeHolding(holdingId));
    }

    @PostMapping("/accounts/{accountId}/analyze")
    @DataScope(resourceType = ResourceType.PORTFOLIO_ACCOUNT, idParam = "accountId", operation = DataOperation.READ)
    @RateLimit(key = "strategy:account-analyze", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 10)
    @OperationLog(module = "strategy", action = "analyze_account", bizType = "STRATEGY_SIGNAL")
    public ApiResponse<List<StrategyAnalysisVO>> analyzeAccount(@PathVariable Long accountId) {
        return ApiResponse.success(strategyService.analyzeAccount(accountId));
    }

    @GetMapping("/signals")
    @RateLimit(key = "strategy:signals", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<StrategySignalVO>> signals(@RequestParam(required = false) Long accountId,
                                                       @RequestParam(required = false) Long holdingId,
                                                       @RequestParam(required = false) String fundCode,
                                                       @RequestParam(required = false) StrategyAction action) {
        return ApiResponse.success(strategyService.listSignals(accountId, holdingId, fundCode, action));
    }

    @GetMapping("/configs")
    @RateLimit(key = "strategy:configs", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<StrategyConfigVO>> configs() {
        return ApiResponse.success(strategyService.listConfigs());
    }

    @PutMapping("/configs")
    @RateLimit(key = "strategy:save-config", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "strategy", action = "save_config", bizType = "STRATEGY_CONFIG")
    public ApiResponse<StrategyConfigVO> saveConfig(@Valid @RequestBody StrategyConfigRequest request) {
        return ApiResponse.success(strategyService.saveConfig(request));
    }

    @GetMapping("/risk-profile")
    @RateLimit(key = "strategy:risk-profile", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<RiskProfileVO> riskProfile() {
        return ApiResponse.success(strategyService.getRiskProfile());
    }

    @PutMapping("/risk-profile")
    @RateLimit(key = "strategy:update-risk-profile", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "strategy", action = "update_risk_profile", bizType = "RISK_PROFILE")
    public ApiResponse<RiskProfileVO> updateRiskProfile(@Valid @RequestBody RiskProfileRequest request) {
        return ApiResponse.success(strategyService.updateRiskProfile(request));
    }
}

