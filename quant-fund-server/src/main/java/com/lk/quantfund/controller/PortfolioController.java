package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.DataScope;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.portfolio.CreatePortfolioAccountRequest;
import com.lk.quantfund.dto.portfolio.UpdatePortfolioAccountRequest;
import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import com.lk.quantfund.vo.portfolio.PortfolioAccountVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/portfolios")
public class PortfolioController {

    private final PortfolioAccountService portfolioAccountService;

    public PortfolioController(PortfolioAccountService portfolioAccountService) {
        this.portfolioAccountService = portfolioAccountService;
    }

    @PostMapping
    @RateLimit(key = "portfolio:create", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "portfolio", action = "create_account", bizType = "PORTFOLIO_ACCOUNT")
    public ApiResponse<PortfolioAccountVO> create(@Valid @RequestBody CreatePortfolioAccountRequest request) {
        return ApiResponse.success(portfolioAccountService.create(request));
    }

    @GetMapping
    @RateLimit(key = "portfolio:list", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<PortfolioAccountVO>> list() {
        return ApiResponse.success(portfolioAccountService.list());
    }

    @GetMapping("/summary")
    @RateLimit(key = "portfolio:summary", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<PortfolioSummaryVO> summary() {
        return ApiResponse.success(portfolioAccountService.summary());
    }

    @GetMapping("/{id}")
    @DataScope(resourceType = ResourceType.PORTFOLIO_ACCOUNT, idParam = "id", operation = DataOperation.READ)
    @RateLimit(key = "portfolio:detail", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<PortfolioAccountVO> detail(@PathVariable Long id) {
        return ApiResponse.success(portfolioAccountService.detail(id));
    }

    @PutMapping("/{id}")
    @DataScope(resourceType = ResourceType.PORTFOLIO_ACCOUNT, idParam = "id", operation = DataOperation.UPDATE)
    @RateLimit(key = "portfolio:update", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "portfolio", action = "update_account", bizType = "PORTFOLIO_ACCOUNT")
    public ApiResponse<PortfolioAccountVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdatePortfolioAccountRequest request) {
        return ApiResponse.success(portfolioAccountService.update(id, request));
    }

    @GetMapping("/{id}/holdings")
    @DataScope(resourceType = ResourceType.PORTFOLIO_ACCOUNT, idParam = "id", operation = DataOperation.READ)
    @RateLimit(key = "portfolio:holdings", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<FundHoldingVO>> holdings(@PathVariable Long id) {
        return ApiResponse.success(portfolioAccountService.holdings(id));
    }

    @PostMapping("/{id}/recalculate")
    @DataScope(resourceType = ResourceType.PORTFOLIO_ACCOUNT, idParam = "id", operation = DataOperation.UPDATE)
    @RateLimit(key = "portfolio:recalculate", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "portfolio", action = "recalculate_account", bizType = "PORTFOLIO_ACCOUNT")
    public ApiResponse<PortfolioAccountVO> recalculate(@PathVariable Long id) {
        return ApiResponse.success(portfolioAccountService.recalculate(id));
    }
}

