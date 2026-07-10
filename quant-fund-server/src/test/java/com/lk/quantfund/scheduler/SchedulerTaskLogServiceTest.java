package com.lk.quantfund.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.lk.quantfund.entity.SchedulerTaskLog;
import com.lk.quantfund.mapper.SchedulerTaskLogMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SchedulerTaskLogServiceTest {

    @Test
    void shouldPersistCachedSkipCountInTaskLogSummary() {
        SchedulerTaskLogMapper mapper = mock(SchedulerTaskLogMapper.class);
        SchedulerTaskLogService service = new SchedulerTaskLogService(mapper);
        SchedulerTaskResult result = new SchedulerTaskResult();
        for (int index = 0; index < 4; index++) {
            result.success();
        }
        for (int index = 0; index < 11; index++) {
            result.skipped();
        }

        service.save("SCREENER_INCREMENTAL_BACKTEST", "CRON", LocalDateTime.now(), result, false);

        ArgumentCaptor<SchedulerTaskLog> captor = ArgumentCaptor.forClass(SchedulerTaskLog.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getSuccessCount()).isEqualTo(4);
        assertThat(captor.getValue().getErrorMessage()).contains("skipped=11");
    }
}
