package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.lk.quantfund.entity.ApiCallLogEntity;
import com.lk.quantfund.mapper.ApiCallLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiCallLogServiceImplTest {

    @Test
    void shouldRecordApiCallWithoutSaTokenContext() {
        ApiCallLogMapper mapper = mock(ApiCallLogMapper.class);
        ApiCallLogServiceImpl service = new ApiCallLogServiceImpl(mapper);

        service.record("EAST_MONEY", "historical_nav", "https://example.test?token=secret", "GET",
                true, 200, null, 123L, false);

        ArgumentCaptor<ApiCallLogEntity> captor = ArgumentCaptor.forClass(ApiCallLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getProvider()).isEqualTo("EAST_MONEY");
        assertThat(captor.getValue().getSuccess()).isEqualTo(1);
        assertThat(captor.getValue().getCostTimeMs()).isEqualTo(123L);
    }
}
