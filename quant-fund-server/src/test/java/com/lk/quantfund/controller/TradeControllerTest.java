package com.lk.quantfund.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.scheduler.SchedulerTaskResult;
import com.lk.quantfund.service.TradeRecordService;
import org.junit.jupiter.api.Test;

class TradeControllerTest {

    @Test
    void compensateDueRegularInvestShouldReturnSchedulerResult() {
        TradeRecordService tradeRecordService = mock(TradeRecordService.class);
        SchedulerTaskResult result = new SchedulerTaskResult();
        result.success();
        when(tradeRecordService.compensateDueRegularInvestTrades()).thenReturn(result);
        TradeController controller = new TradeController(tradeRecordService);

        ApiResponse<SchedulerTaskResult> response = controller.compensateDueRegularInvest();

        verify(tradeRecordService).compensateDueRegularInvestTrades();
        assertThat(response.data().getSuccessCount()).isEqualTo(1);
        assertThat(response.data().getFailureCount()).isZero();
    }
}
