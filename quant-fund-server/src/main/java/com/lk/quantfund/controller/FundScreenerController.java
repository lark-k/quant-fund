package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.common.PageResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.screener.FundScreenerQueryRequest;
import com.lk.quantfund.service.FundFactorService;
import com.lk.quantfund.service.FundQualityScoreService;
import com.lk.quantfund.service.FundScreenerBacktestService;
import com.lk.quantfund.service.FundScreenerNavService;
import com.lk.quantfund.service.FundUniverseService;
import com.lk.quantfund.vo.screener.FundScreenerExplainVO;
import com.lk.quantfund.vo.screener.FundScreenerRankItemVO;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/fund-screener")
public class FundScreenerController {

    private final FundUniverseService fundUniverseService;
    private final FundScreenerNavService fundScreenerNavService;
    private final FundFactorService fundFactorService;
    private final FundQualityScoreService fundQualityScoreService;
    private final FundScreenerBacktestService fundScreenerBacktestService;

    public FundScreenerController(FundUniverseService fundUniverseService,
                                  FundScreenerNavService fundScreenerNavService,
                                  FundFactorService fundFactorService,
                                  FundQualityScoreService fundQualityScoreService,
                                  FundScreenerBacktestService fundScreenerBacktestService) {
        this.fundUniverseService = fundUniverseService;
        this.fundScreenerNavService = fundScreenerNavService;
        this.fundFactorService = fundFactorService;
        this.fundQualityScoreService = fundQualityScoreService;
        this.fundScreenerBacktestService = fundScreenerBacktestService;
    }

    @GetMapping("/rank")
    @RateLimit(key = "fund-screener:rank", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<PageResponse<FundScreenerRankItemVO>> rank(
            @RequestParam(required = false) String fundType,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String recommendLevel,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) BigDecimal minFundSize,
            @RequestParam(required = false, defaultValue = "true") Boolean excludeShareClassC,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyActiveFund,
            @RequestParam(required = false, defaultValue = "1") long pageNo,
            @RequestParam(required = false, defaultValue = "20") long pageSize,
            @RequestParam(required = false) String sortBy) {
        BigDecimal scoreFloor = minScore == null ? null : BigDecimal.valueOf(minScore.longValue());
        FundScreenerQueryRequest request = new FundScreenerQueryRequest(
                fundType,
                period,
                null,
                scoreFloor,
                minFundSize,
                excludeShareClassC,
                onlyActiveFund,
                recommendLevel,
                pageNo,
                pageSize,
                sortBy
        );
        return ApiResponse.success(fundQualityScoreService.rank(request));
    }

    @GetMapping("/{fundCode}/explain")
    @RateLimit(key = "fund-screener:explain", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<FundScreenerExplainVO> explain(@PathVariable String fundCode) {
        return ApiResponse.success(fundQualityScoreService.explain(fundCode));
    }

    @PostMapping("/sync-universe")
    @RateLimit(key = "fund-screener:sync-universe", windowSeconds = 60, maxRequests = 10)
    public ApiResponse<FundScreenerTaskResultVO> syncUniverse() {
        return ApiResponse.success(fundUniverseService.syncUniverse());
    }

    @PostMapping("/sync-nav")
    @RateLimit(key = "fund-screener:sync-nav", windowSeconds = 60, maxRequests = 10)
    public ApiResponse<FundScreenerTaskResultVO> syncNav() {
        return ApiResponse.success(fundScreenerNavService.syncNav());
    }

    @PostMapping("/rebuild-universe")
    @RateLimit(key = "fund-screener:rebuild-universe", windowSeconds = 60, maxRequests = 10)
    public ApiResponse<FundScreenerTaskResultVO> rebuildUniverse() {
        return ApiResponse.success(fundUniverseService.rebuildUniverse());
    }

    @PostMapping("/refresh-factors")
    @RateLimit(key = "fund-screener:refresh-factors", windowSeconds = 60, maxRequests = 10)
    public ApiResponse<FundScreenerTaskResultVO> refreshFactors() {
        return ApiResponse.success(fundFactorService.refreshFactors());
    }

    @PostMapping("/refresh-score")
    @RateLimit(key = "fund-screener:refresh-score", windowSeconds = 60, maxRequests = 10)
    public ApiResponse<FundScreenerTaskResultVO> refreshScore() {
        long started = System.currentTimeMillis();
        List<FundScreenerTaskResultVO> steps = List.of(
                fundQualityScoreService.refreshScore()
        );
        return ApiResponse.success(combine("REFRESH_SCORE", started, steps));
    }

    @PostMapping("/refresh-full")
    @RateLimit(key = "fund-screener:refresh-full", windowSeconds = 60, maxRequests = 3)
    public ApiResponse<FundScreenerTaskResultVO> refreshFull() {
        long started = System.currentTimeMillis();
        List<FundScreenerTaskResultVO> steps = List.of(
                fundUniverseService.syncUniverse(),
                fundScreenerNavService.syncNav(),
                fundUniverseService.rebuildUniverse(),
                fundFactorService.refreshFactors(),
                fundQualityScoreService.refreshScore()
        );
        return ApiResponse.success(combine("REFRESH_FULL", started, steps));
    }

    @GetMapping("/backtest")
    @RateLimit(key = "fund-screener:backtest", windowSeconds = 60, maxRequests = 5)
    public ApiResponse<FundScreenerTaskResultVO> backtest() {
        return ApiResponse.success(fundScreenerBacktestService.backtest());
    }

    private FundScreenerTaskResultVO combine(String taskName, long started, List<FundScreenerTaskResultVO> steps) {
        int success = steps.stream().mapToInt(FundScreenerTaskResultVO::successCount).sum();
        int failure = steps.stream().mapToInt(FundScreenerTaskResultVO::failureCount).sum();
        int skipped = steps.stream().mapToInt(FundScreenerTaskResultVO::skippedCount).sum();
        List<String> errors = new ArrayList<>();
        for (FundScreenerTaskResultVO step : steps) {
            errors.addAll(step.errorSummaries());
        }
        String status = failure == 0 ? "SUCCESS" : success > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        String message = steps.stream()
                .map(step -> step.taskName() + "=" + step.status() + "(" + step.successCount() + "/" + step.failureCount() + ")")
                .reduce((left, right) -> left + "; " + right)
                .orElse("no steps");
        return new FundScreenerTaskResultVO(
                taskName,
                status,
                success,
                failure,
                skipped,
                System.currentTimeMillis() - started,
                errors,
                message,
                LocalDateTime.now()
        );
    }
}
