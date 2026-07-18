package com.lk.quantfund.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.common.PageResponse;
import com.lk.quantfund.dto.screener.FundScreenerQueryRequest;
import com.lk.quantfund.service.FundFactorService;
import com.lk.quantfund.service.FundQualityScoreService;
import com.lk.quantfund.service.FundScreenerBacktestService;
import com.lk.quantfund.service.FundScreenerNavService;
import com.lk.quantfund.service.FundUniverseService;
import com.lk.quantfund.vo.screener.FundScreenerExplainVO;
import com.lk.quantfund.vo.screener.FundScreenerRankItemVO;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import com.lk.quantfund.vo.screener.FundScreenerStrategyPolicyVO;
import com.lk.quantfund.vo.screener.FundScreenerValidationVO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundScreenerControllerTest {

    @Test
    void shouldReturnPagedRankFromQualityScoreService() {
        FundQualityScoreService service = mock(FundQualityScoreService.class);
        PageResponse<FundScreenerRankItemVO> page = PageResponse.of(1, 20, 1, List.of(rankItem()));
        when(service.rank(any(FundScreenerQueryRequest.class))).thenReturn(page);
        FundScreenerController controller = controller(service);

        ApiResponse<PageResponse<FundScreenerRankItemVO>> response = controller.rank(
                "MIXED", "120d", "WATCH", 75, null, true, true, 1, 20, "qualityScore"
        );

        assertThat(response.data()).isEqualTo(page);
        assertThat(response.data().records()).hasSize(1);
        assertThat(response.data().records().get(0).fundCode()).isEqualTo("000001");
        verify(service).rank(any(FundScreenerQueryRequest.class));
    }

    @Test
    void shouldRefreshNavFactorsAndScoresInOrder() {
        FundUniverseService universeService = mock(FundUniverseService.class);
        FundScreenerNavService navService = mock(FundScreenerNavService.class);
        FundFactorService factorService = mock(FundFactorService.class);
        FundQualityScoreService service = mock(FundQualityScoreService.class);
        when(navService.syncNav()).thenReturn(taskResult("SYNC_NAV", "SUCCESS", 10, 0));
        when(universeService.rebuildUniverse()).thenReturn(taskResult("REBUILD_UNIVERSE", "SUCCESS", 8, 0));
        when(factorService.refreshFactors()).thenReturn(taskResult("REFRESH_FACTORS", "SUCCESS", 8, 0));
        FundScreenerTaskResultVO taskResult = taskResult("REFRESH_SCORE", "SUCCESS", 8, 0);
        when(service.refreshScore()).thenReturn(taskResult);
        FundScreenerController controller = new FundScreenerController(
                universeService,
                navService,
                factorService,
                service,
                mock(FundScreenerBacktestService.class)
        );

        ApiResponse<FundScreenerTaskResultVO> response = controller.refreshScore();

        assertThat(response.data().taskName()).isEqualTo("REFRESH_SCORE");
        assertThat(response.data().status()).isEqualTo("SUCCESS");
        assertThat(response.data().successCount()).isEqualTo(26);
        verify(service).refreshScore();
        verify(navService).syncNav();
        verify(universeService, never()).rebuildUniverse();
        verify(factorService).refreshFactors();
    }

    @Test
    void shouldStopManualScoreRefreshWhenNavSyncFails() {
        FundScreenerNavService navService = mock(FundScreenerNavService.class);
        FundFactorService factorService = mock(FundFactorService.class);
        FundQualityScoreService scoreService = mock(FundQualityScoreService.class);
        when(navService.syncNav()).thenReturn(taskResult("SYNC_NAV", "FAILED", 0, 1));
        FundScreenerController controller = new FundScreenerController(
                mock(FundUniverseService.class), navService, factorService, scoreService,
                mock(FundScreenerBacktestService.class)
        );

        var response = controller.refreshScore();

        assertThat(response.data().status()).isEqualTo("FAILED");
        verify(factorService, never()).refreshFactors();
        verify(scoreService, never()).refreshScore();
    }

    @Test
    void shouldRunFullManualRefreshPath() {
        FundUniverseService universeService = mock(FundUniverseService.class);
        FundScreenerNavService navService = mock(FundScreenerNavService.class);
        FundFactorService factorService = mock(FundFactorService.class);
        FundQualityScoreService service = mock(FundQualityScoreService.class);
        when(universeService.syncUniverse()).thenReturn(taskResult("SYNC_UNIVERSE", "SUCCESS", 10, 0));
        when(navService.syncNav()).thenReturn(taskResult("SYNC_NAV", "SUCCESS", 11, 0));
        when(universeService.rebuildUniverse()).thenReturn(taskResult("REBUILD_UNIVERSE", "SUCCESS", 8, 0));
        when(factorService.refreshFactors()).thenReturn(taskResult("REFRESH_FACTORS", "SUCCESS", 7, 0));
        when(service.refreshScore()).thenReturn(taskResult("REFRESH_SCORE", "SUCCESS", 6, 0));
        FundScreenerController controller = new FundScreenerController(
                universeService,
                navService,
                factorService,
                service,
                mock(FundScreenerBacktestService.class)
        );

        ApiResponse<FundScreenerTaskResultVO> response = controller.refreshFull();

        assertThat(response.data().taskName()).isEqualTo("REFRESH_FULL");
        assertThat(response.data().status()).isEqualTo("SUCCESS");
        assertThat(response.data().successCount()).isEqualTo(42);
        verify(universeService).syncUniverse();
        verify(navService).syncNav();
        verify(universeService).rebuildUniverse();
        verify(factorService).refreshFactors();
        verify(service).refreshScore();
    }

    @Test
    void shouldReturnExplainForFundCode() {
        FundQualityScoreService service = mock(FundQualityScoreService.class);
        FundScreenerExplainVO explain = FundScreenerExplainVO.empty("000001");
        when(service.explain("000001")).thenReturn(explain);
        FundScreenerController controller = controller(service);

        ApiResponse<FundScreenerExplainVO> response = controller.explain("000001");

        assertThat(response.data().fundCode()).isEqualTo("000001");
        assertThat(response.data().disclaimer()).contains("不构成投资建议");
        verify(service).explain("000001");
    }

    @Test
    void shouldExposeTaskSkeletonEndpoints() {
        FundUniverseService universeService = mock(FundUniverseService.class);
        FundScreenerNavService navService = mock(FundScreenerNavService.class);
        FundFactorService factorService = mock(FundFactorService.class);
        FundQualityScoreService scoreService = mock(FundQualityScoreService.class);
        FundScreenerBacktestService backtestService = mock(FundScreenerBacktestService.class);
        when(universeService.syncUniverse()).thenReturn(FundScreenerTaskResultVO.skipped("SYNC_UNIVERSE", "batch 1 skeleton"));
        when(universeService.rebuildUniverse()).thenReturn(FundScreenerTaskResultVO.skipped("REBUILD_UNIVERSE", "batch 1 skeleton"));
        when(navService.syncNav()).thenReturn(FundScreenerTaskResultVO.skipped("SYNC_NAV", "batch 1 skeleton"));
        when(factorService.refreshFactors()).thenReturn(FundScreenerTaskResultVO.skipped("REFRESH_FACTORS", "batch 1 skeleton"));
        when(backtestService.backtest()).thenReturn(FundScreenerTaskResultVO.skipped("SCREENER_BACKTEST", "batch 6 skeleton"));
        FundScreenerController controller = new FundScreenerController(universeService, navService, factorService, scoreService, backtestService);

        assertThat(controller.syncUniverse().data().taskName()).isEqualTo("SYNC_UNIVERSE");
        assertThat(controller.rebuildUniverse().data().taskName()).isEqualTo("REBUILD_UNIVERSE");
        assertThat(controller.syncNav().data().taskName()).isEqualTo("SYNC_NAV");
        assertThat(controller.refreshFactors().data().taskName()).isEqualTo("REFRESH_FACTORS");
        assertThat(controller.backtest().data().taskName()).isEqualTo("SCREENER_BACKTEST");
    }

    @Test
    void shouldExposeManualIncrementalRunAndCachedValidation() {
        FundScreenerBacktestService backtestService = mock(FundScreenerBacktestService.class);
        FundScreenerTaskResultVO task = taskResult("SCREENER_BACKTEST", "SUCCESS", 12, 0);
        FundScreenerValidationVO validation = new FundScreenerValidationVO(
                "2026-07-10", "2026-01-02", "2026-04-02", "EFFECTIVE", "策略当前有效",
                List.of("保持当前阈值"),
                new FundScreenerStrategyPolicyVO(new BigDecimal("82"), 8, new BigDecimal("72"),
                        20, new BigDecimal("58"), 30, 3),
                List.of(),
                List.of()
        );
        when(backtestService.runIncremental()).thenReturn(task);
        when(backtestService.getValidation()).thenReturn(validation);
        FundScreenerController controller = new FundScreenerController(
                mock(FundUniverseService.class), mock(FundScreenerNavService.class),
                mock(FundFactorService.class), mock(FundQualityScoreService.class), backtestService
        );

        assertThat(controller.runBacktest().data().successCount()).isEqualTo(12);
        assertThat(controller.validation().data().status()).isEqualTo("EFFECTIVE");
        verify(backtestService).runIncremental();
        verify(backtestService).getValidation();
    }

    private FundScreenerController controller(FundQualityScoreService service) {
        return new FundScreenerController(
                mock(FundUniverseService.class),
                mock(FundScreenerNavService.class),
                mock(FundFactorService.class),
                service,
                mock(FundScreenerBacktestService.class)
        );
    }

    private FundScreenerRankItemVO rankItem() {
        return new FundScreenerRankItemVO(
                "000001",
                "示例基金A",
                "MIXED",
                "示例基金公司",
                "基金经理",
                86.5,
                88.0,
                78.0,
                82.0,
                81.0,
                75.0,
                0.0,
                95.0,
                84.0,
                80.0,
                82.0,
                88.0,
                1,
                1.2,
                "STRONG",
                8.2,
                16.4,
                28.0,
                -9.8,
                18.6,
                12.5,
                1.7,
                82.0,
                "000300",
                "2026-07-03",
                List.of("近120日收益强于同类"),
                List.of("基金优选结果仅供参考，不构成投资建议"),
                "仅供参考，不构成投资建议，不承诺收益"
        );
    }

    private FundScreenerTaskResultVO taskResult(String taskName, String status, int successCount, int failureCount) {
        return new FundScreenerTaskResultVO(
                taskName,
                status,
                successCount,
                failureCount,
                0,
                10,
                List.of(),
                taskName + " done",
                java.time.LocalDateTime.now()
        );
    }
}
