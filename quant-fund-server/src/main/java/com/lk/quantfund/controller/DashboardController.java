package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.service.DashboardService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.vo.dashboard.DashboardOverviewVO;
import com.lk.quantfund.vo.market.MarketIndexVO;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final MarketDataService marketDataService;

    public DashboardController(DashboardService dashboardService, MarketDataService marketDataService) {
        this.dashboardService = dashboardService;
        this.marketDataService = marketDataService;
    }

    @GetMapping("/overview")
    @RateLimit(key = "dashboard:overview", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<DashboardOverviewVO> overview() {
        return ApiResponse.success(dashboardService.overview());
    }

    @GetMapping("/market-readings")
    @RateLimit(key = "dashboard:market-readings", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<MarketIndexVO>> marketReadings() {
        return ApiResponse.success(marketDataService.marketReadings());
    }
}
