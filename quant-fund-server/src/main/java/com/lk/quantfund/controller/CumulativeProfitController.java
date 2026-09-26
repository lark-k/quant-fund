package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.service.CumulativeProfitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/dashboard/cumulative-profit")
public class CumulativeProfitController {
    private final CumulativeProfitService service;
    public CumulativeProfitController(CumulativeProfitService service) { this.service = service; }

    @GetMapping
    @RateLimit(key = "dashboard:cumulative-profit", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<CumulativeProfitService.Overview> overview() {
        return ApiResponse.success(service.overview(UserContext.getUserId()));
    }
}
