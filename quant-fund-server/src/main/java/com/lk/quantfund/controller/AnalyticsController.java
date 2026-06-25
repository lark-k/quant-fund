package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.service.AnalyticsService;
import com.lk.quantfund.vo.analytics.ProfitAnalysisVO;
import com.lk.quantfund.vo.analytics.ProfitCalendarVO;
import com.lk.quantfund.vo.analytics.ProfitIntradayTrendPointVO;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/profit")
    @RateLimit(key = "analytics:profit", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<ProfitAnalysisVO> profitAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String indexCode) {
        return ApiResponse.success(analyticsService.profitAnalysis(startDate, endDate, indexCode));
    }

    @GetMapping("/profit-intraday")
    @RateLimit(key = "analytics:profit-intraday", windowSeconds = 60, maxRequests = 180)
    public ApiResponse<List<ProfitIntradayTrendPointVO>> profitIntraday(
            @RequestParam(required = false) String indexCode) {
        return ApiResponse.success(analyticsService.intradayTrend(indexCode));
    }

    @GetMapping("/profit-calendar")
    @RateLimit(key = "analytics:profit-calendar", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<ProfitCalendarVO> profitCalendar(@RequestParam(required = false) String month) {
        YearMonth parsedMonth = parseMonth(month);
        return ApiResponse.success(analyticsService.profitCalendar(parsedMonth));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month must use yyyy-MM format");
        }
    }
}
