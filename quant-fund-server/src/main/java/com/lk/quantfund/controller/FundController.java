package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundPeerRankDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
import com.lk.quantfund.service.FundQueryService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping(SystemConstants.API_PREFIX + "/funds")
public class FundController {

    private final FundQueryService fundQueryService;

    public FundController(FundQueryService fundQueryService) {
        this.fundQueryService = fundQueryService;
    }

    @GetMapping("/search")
    @RateLimit(key = "fund:search", windowSeconds = 60, maxRequests = 60)
    public ApiResponse<List<FundSearchResultDTO>> search(@RequestParam String keyword,
                                                         @RequestParam(defaultValue = "FUZZY") String mode) {
        return ApiResponse.success(fundQueryService.search(keyword, mode));
    }

    @GetMapping("/{fundCode}")
    @RateLimit(key = "fund:basic", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<FundBasicInfoDTO> basicInfo(@PathVariable String fundCode) {
        return ApiResponse.success(fundQueryService.getBasicInfo(fundCode));
    }

    @GetMapping("/{fundCode}/nav")
    @RateLimit(key = "fund:nav", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<FundNavPointDTO>> historicalNav(
            @PathVariable String fundCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String indexCode) {
        return ApiResponse.success(fundQueryService.getHistoricalNav(fundCode, startDate, endDate, indexCode));
    }

    @GetMapping("/{fundCode}/estimate")
    @RateLimit(key = "fund:estimate", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<FundEstimateDTO> estimate(@PathVariable String fundCode) {
        return ApiResponse.success(fundQueryService.getIntradayEstimate(fundCode, false));
    }

    @PostMapping("/{fundCode}/refresh-estimate")
    @RateLimit(key = "fund:manual-refresh", windowSeconds = 60, maxRequests = 30)
    public ApiResponse<FundEstimateDTO> refreshEstimate(@PathVariable String fundCode) {
        return ApiResponse.success(fundQueryService.getIntradayEstimate(fundCode, true));
    }

    @GetMapping("/{fundCode}/heavy-stocks")
    @RateLimit(key = "fund:heavy-stocks", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<FundStockHoldingDTO>> heavyStocks(@PathVariable String fundCode) {
        return ApiResponse.success(fundQueryService.getHeavyStocks(fundCode));
    }

    @GetMapping("/{fundCode}/themes")
    @RateLimit(key = "fund:themes", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<FundThemeDTO>> themes(@PathVariable String fundCode) {
        return ApiResponse.success(fundQueryService.getRelatedThemes(fundCode));
    }

    @GetMapping("/{fundCode}/peer-rank")
    @RateLimit(key = "fund:peer-rank", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<FundPeerRankDTO> peerRank(@PathVariable String fundCode) {
        return ApiResponse.success(fundQueryService.getPeerRank(fundCode));
    }
}
