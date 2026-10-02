package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.service.FundCashService;
import com.lk.quantfund.service.PortfolioAccountService;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequireLogin
@RequestMapping("/api/portfolios/{id}/fund-cash")
public class FundCashController {
    private final FundCashService cash;
    private final PortfolioAccountService portfolios;
    public FundCashController(FundCashService cash,PortfolioAccountService portfolios) { this.cash=cash;this.portfolios=portfolios; }
    @GetMapping
    public ApiResponse<FundCashService.Snapshot> get(@PathVariable Long id) {
        return ApiResponse.success(cash.get(UserContext.getUserId(),id));
    }
    @PutMapping
    @Transactional(rollbackFor=Exception.class)
    @OperationLog(module="portfolio",action="allocate_fund_cash",bizType="PORTFOLIO_ACCOUNT")
    public ApiResponse<FundCashService.Snapshot> save(@PathVariable Long id,@Valid @RequestBody FundCashService.Update request) {
        Long user=UserContext.getUserId();
        var result=cash.save(user,id,request);
        portfolios.recalculateOwnedAccount(user,id);
        return ApiResponse.success(result);
    }
}
