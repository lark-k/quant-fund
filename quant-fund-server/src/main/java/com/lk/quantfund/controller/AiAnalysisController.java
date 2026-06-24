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
import com.lk.quantfund.service.AiAnalysisService;
import com.lk.quantfund.vo.ai.AiAnalysisReportVO;
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
@RequestMapping(SystemConstants.API_PREFIX + "/ai-analysis")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    public AiAnalysisController(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @PostMapping("/holdings/{holdingId}")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "holdingId", operation = DataOperation.READ)
    @RateLimit(key = "ai:holding-analysis", windowSeconds = 60, maxRequests = 20)
    @RepeatSubmit(intervalSeconds = 10)
    @OperationLog(module = "ai", action = "analyze_holding", bizType = "AI_ANALYSIS_REPORT")
    public ApiResponse<AiAnalysisReportVO> analyzeHolding(@PathVariable Long holdingId) {
        return ApiResponse.success(aiAnalysisService.analyzeHolding(holdingId));
    }

    @PostMapping("/accounts/{accountId}")
    @DataScope(resourceType = ResourceType.PORTFOLIO_ACCOUNT, idParam = "accountId", operation = DataOperation.READ)
    @RateLimit(key = "ai:account-analysis", windowSeconds = 60, maxRequests = 10)
    @RepeatSubmit(intervalSeconds = 20)
    @OperationLog(module = "ai", action = "analyze_account", bizType = "AI_ANALYSIS_REPORT")
    public ApiResponse<List<AiAnalysisReportVO>> analyzeAccount(@PathVariable Long accountId) {
        return ApiResponse.success(aiAnalysisService.analyzeAccount(accountId));
    }

    @GetMapping("/history")
    @RateLimit(key = "ai:history", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<AiAnalysisReportVO>> history(@RequestParam(required = false) Long accountId,
                                                         @RequestParam(required = false) Long holdingId,
                                                         @RequestParam(required = false) String fundCode) {
        return ApiResponse.success(aiAnalysisService.history(accountId, holdingId, fundCode));
    }

    @PostMapping("/reports/{reportId}/regenerate")
    @DataScope(resourceType = ResourceType.AI_ANALYSIS_REPORT, idParam = "reportId", operation = DataOperation.READ)
    @RateLimit(key = "ai:regenerate", windowSeconds = 60, maxRequests = 20)
    @RepeatSubmit(intervalSeconds = 10)
    @OperationLog(module = "ai", action = "regenerate_report", bizType = "AI_ANALYSIS_REPORT")
    public ApiResponse<AiAnalysisReportVO> regenerate(@PathVariable Long reportId) {
        return ApiResponse.success(aiAnalysisService.regenerate(reportId));
    }
}

