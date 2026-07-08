package com.lk.quantfund.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.service.FundFactorService;
import com.lk.quantfund.service.FundQualityScoreService;
import com.lk.quantfund.service.FundScreenerNavService;
import com.lk.quantfund.service.FundUniverseService;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduledFundScreenerTaskServiceTest {

    private final FundUniverseService universeService = mock(FundUniverseService.class);
    private final FundScreenerNavService navService = mock(FundScreenerNavService.class);
    private final FundFactorService factorService = mock(FundFactorService.class);
    private final FundQualityScoreService qualityScoreService = mock(FundQualityScoreService.class);
    private final ScheduledFundScreenerTaskService service = new ScheduledFundScreenerTaskService(
            universeService,
            navService,
            factorService,
            qualityScoreService
    );

    @Test
    void refreshQualityScoreDelegatesAndAdaptsCounts() {
        when(qualityScoreService.refreshScore()).thenReturn(new FundScreenerTaskResultVO(
                "REFRESH_SCORE",
                "PARTIAL_SUCCESS",
                8,
                2,
                0,
                1200,
                List.of("000001: nav missing", "000002: score failed"),
                "partial",
                LocalDateTime.now()
        ));

        SchedulerTaskResult result = service.refreshQualityScore();

        assertThat(result.getSuccessCount()).isEqualTo(8);
        assertThat(result.getFailureCount()).isEqualTo(2);
        assertThat(result.errorSummary()).contains("000001: nav missing");
        verify(qualityScoreService).refreshScore();
    }

    @Test
    void skippedTaskDoesNotCountAsSuccessOrFailure() {
        when(factorService.refreshFactors()).thenReturn(FundScreenerTaskResultVO.skipped("REFRESH_FACTORS", "disabled"));

        SchedulerTaskResult result = service.refreshFactors();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailureCount()).isZero();
        assertThat(result.errorSummary()).isEmpty();
        verify(factorService).refreshFactors();
    }
}
