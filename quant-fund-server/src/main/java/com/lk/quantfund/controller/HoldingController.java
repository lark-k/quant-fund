package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.DataScope;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.holding.ClearHoldingRequest;
import com.lk.quantfund.dto.holding.CreateHoldingRequest;
import com.lk.quantfund.dto.holding.UpdateHoldingRequest;
import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.service.FundHoldingService;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping(SystemConstants.API_PREFIX + "/holdings")
public class HoldingController {

    private final FundHoldingService fundHoldingService;

    public HoldingController(FundHoldingService fundHoldingService) {
        this.fundHoldingService = fundHoldingService;
    }

    @PostMapping
    @RateLimit(key = "holding:create", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "holding", action = "create_holding", bizType = "FUND_HOLDING")
    public ApiResponse<FundHoldingVO> create(@Valid @RequestBody CreateHoldingRequest request) {
        return ApiResponse.success(fundHoldingService.create(request));
    }

    @GetMapping
    @RateLimit(key = "holding:list", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<FundHoldingVO>> list(@RequestParam(required = false) Long accountId,
                                                 @RequestParam(required = false) String fundCode) {
        return ApiResponse.success(fundHoldingService.list(accountId, fundCode));
    }

    @PostMapping("/sync-official-nav")
    @RateLimit(key = "holding:sync-official-nav", windowSeconds = 60, maxRequests = 20)
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "holding", action = "sync_official_nav", bizType = "FUND_HOLDING")
    public ApiResponse<List<FundHoldingVO>> syncOfficialNav() {
        return ApiResponse.success(fundHoldingService.syncOfficialNav());
    }

    @GetMapping("/{id}")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "id", operation = DataOperation.READ)
    @RateLimit(key = "holding:detail", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<FundHoldingVO> detail(@PathVariable Long id) {
        return ApiResponse.success(fundHoldingService.detail(id));
    }

    @PutMapping("/{id}")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "id", operation = DataOperation.UPDATE)
    @RateLimit(key = "holding:update", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "holding", action = "update_holding", bizType = "FUND_HOLDING")
    public ApiResponse<FundHoldingVO> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateHoldingRequest request) {
        return ApiResponse.success(fundHoldingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "id", operation = DataOperation.DELETE)
    @RateLimit(key = "holding:delete", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "holding", action = "delete_holding", bizType = "FUND_HOLDING")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fundHoldingService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/clear")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "id", operation = DataOperation.UPDATE)
    @RateLimit(key = "holding:clear", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "holding", action = "clear_holding", bizType = "FUND_HOLDING")
    public ApiResponse<FundHoldingVO> clear(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) ClearHoldingRequest request) {
        return ApiResponse.success(fundHoldingService.clear(id, request));
    }

    @PostMapping("/{id}/recalculate")
    @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "id", operation = DataOperation.UPDATE)
    @RateLimit(key = "holding:recalculate", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "holding", action = "recalculate_holding", bizType = "FUND_HOLDING")
    public ApiResponse<FundHoldingVO> recalculate(@PathVariable Long id) {
        return ApiResponse.success(fundHoldingService.recalculate(id));
    }
}
