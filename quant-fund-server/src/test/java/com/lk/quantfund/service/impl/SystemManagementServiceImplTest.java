package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.dto.system.DataSourceConfigRequest;
import com.lk.quantfund.entity.DataSourceConfig;
import com.lk.quantfund.mapper.ApiCallLogMapper;
import com.lk.quantfund.mapper.DataSourceConfigMapper;
import com.lk.quantfund.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SystemManagementServiceImplTest {

    private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
    private final OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
    private final ApiCallLogMapper apiCallLogMapper = mock(ApiCallLogMapper.class);
    private final SystemManagementServiceImpl service = new SystemManagementServiceImpl(
            dataSourceConfigMapper,
            operationLogMapper,
            apiCallLogMapper
    );

    @Test
    void updateGlobalDataSourceShouldCreateUserOverride() {
        DataSourceConfig global = new DataSourceConfig();
        global.setId(10L);
        global.setUserId(null);
        global.setSourceName("EAST_MONEY");
        global.setBaseUrl("https://global.example.com");
        when(dataSourceConfigMapper.selectById(10L)).thenReturn(global);
        when(dataSourceConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        DataSourceConfigRequest request = new DataSourceConfigRequest(
                "EAST_MONEY",
                "https://user.example.com",
                5000,
                120,
                60,
                true,
                100,
                "{}"
        );

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(99L);
            service.updateDataSourceConfig(10L, request);
        }

        ArgumentCaptor<DataSourceConfig> captor = ArgumentCaptor.forClass(DataSourceConfig.class);
        verify(dataSourceConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(99L);
        assertThat(captor.getValue().getSourceName()).isEqualTo("EAST_MONEY");
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://user.example.com");
    }
}
