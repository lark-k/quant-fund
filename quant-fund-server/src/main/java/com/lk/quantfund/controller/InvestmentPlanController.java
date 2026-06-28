package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.trade.InvestmentPlanRequest;
import com.lk.quantfund.service.InvestmentPlanService;
import com.lk.quantfund.vo.trade.InvestmentPlanVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping(SystemConstants.API_PREFIX + "/investment-plans")
public class InvestmentPlanController {

    private final InvestmentPlanService investmentPlanService;

    public InvestmentPlanController(InvestmentPlanService investmentPlanService) {
        this.investmentPlanService = investmentPlanService;
    }

    @PostMapping
    @RateLimit(key = "investment-plan:create", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "create_investment_plan", bizType = "INVESTMENT_PLAN")
    public ApiResponse<InvestmentPlanVO> create(@Valid @RequestBody InvestmentPlanRequest request) {
        return ApiResponse.success(investmentPlanService.create(request));
    }

    @PutMapping("/{id}")
    @RateLimit(key = "investment-plan:update", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "update_investment_plan", bizType = "INVESTMENT_PLAN")
    public ApiResponse<InvestmentPlanVO> update(@PathVariable Long id,
                                                @Valid @RequestBody InvestmentPlanRequest request) {
        return ApiResponse.success(investmentPlanService.update(id, request));
    }

    @GetMapping
    @RateLimit(key = "investment-plan:list", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<InvestmentPlanVO>> list(@RequestParam(required = false) Long accountId) {
        return ApiResponse.success(investmentPlanService.list(accountId));
    }

    @PutMapping("/{id}/status")
    @RateLimit(key = "investment-plan:status", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "update_investment_plan_status", bizType = "INVESTMENT_PLAN")
    public ApiResponse<InvestmentPlanVO> updateStatus(@PathVariable Long id,
                                                      @RequestParam String status) {
        return ApiResponse.success(investmentPlanService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @RateLimit(key = "investment-plan:delete", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "delete_investment_plan", bizType = "INVESTMENT_PLAN")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        investmentPlanService.delete(id);
        return ApiResponse.success(null);
    }
}
