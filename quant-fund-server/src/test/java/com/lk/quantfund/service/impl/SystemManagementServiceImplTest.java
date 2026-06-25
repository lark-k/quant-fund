package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.dto.system.DataSourceConfigRequest;
import com.lk.quantfund.entity.ApiCallLogEntity;
import com.lk.quantfund.entity.DataSourceConfig;
import com.lk.quantfund.mapper.ApiCallLogMapper;
import com.lk.quantfund.mapper.DataSourceConfigMapper;
import com.lk.quantfund.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.time.LocalDateTime;
import java.util.List;

class SystemManagementServiceImplTest {

    private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
    private final OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
    private final ApiCallLogMapper apiCallLogMapper = mock(ApiCallLogMapper.class);
    private final QuantFundProperties properties = new QuantFundProperties();
    private final SystemManagementServiceImpl service = new SystemManagementServiceImpl(
            dataSourceConfigMapper,
            operationLogMapper,
            apiCallLogMapper,
            properties
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

    @Test
    void aiRuntimeConfigShouldExposeSafeDiagnosisWithoutApiKey() {
        properties.getAi().setEnabled(true);
        properties.getAi().setMockEnabled(false);
        properties.getAi().setApiKey("secret-key");
        properties.getAi().setModel("deepseek-chat");

        var config = service.aiRuntimeConfig();

        assertThat(config.ready()).isTrue();
        assertThat(config.keyPresent()).isTrue();
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.diagnosis()).isEqualTo("DeepSeek 真实调用配置就绪");
        assertThat(config.toString()).doesNotContain("secret-key");
    }

    @Test
    void aiRuntimeConfigShouldExplainMockAndMissingKey() {
        properties.getAi().setApiKey("");

        var missingKey = service.aiRuntimeConfig();

        assertThat(missingKey.ready()).isFalse();
        assertThat(missingKey.diagnosis()).contains("DEEPSEEK_API_KEY");

        properties.getAi().setApiKey("secret-key");
        properties.getAi().setMockEnabled(true);

        var mockEnabled = service.aiRuntimeConfig();

        assertThat(mockEnabled.ready()).isFalse();
        assertThat(mockEnabled.diagnosis()).contains("DEEPSEEK_MOCK_ENABLED=true");
    }

    @Test
    void listDataSourceHealthShouldSummarizeLatestApiState() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 25, 10, 0);
        when(apiCallLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                apiLog("EAST_MONEY", "historical_nav", true, false, now, null),
                apiLog("EAST_MONEY", "historical_nav", false, false, now.minusMinutes(5), "timeout"),
                apiLog("EAST_MONEY", "intraday_estimate", false, false, now.minusMinutes(1), "HTTP 503"),
                apiLog("MOCK_FALLBACK", "fund_search", true, true, now.minusMinutes(2), null)
        ));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(99L);
            var health = service.listDataSourceHealth();

            assertThat(health).hasSize(3);
            assertThat(health.get(0).provider()).isEqualTo("EAST_MONEY");
            assertThat(health.get(0).apiName()).isEqualTo("historical_nav");
            assertThat(health.get(0).healthy()).isTrue();
            assertThat(health.get(0).lastSuccessTime()).isEqualTo(now);
            assertThat(health.get(0).lastFailureReason()).isEqualTo("timeout");
            assertThat(health.get(1).healthy()).isFalse();
            assertThat(health.get(1).statusText()).isEqualTo("最近调用失败");
            assertThat(health.get(2).delayed()).isTrue();
            assertThat(health.get(2).statusText()).isEqualTo("最近调用使用降级数据");
        }
    }

    private ApiCallLogEntity apiLog(String provider,
                                    String apiName,
                                    boolean success,
                                    boolean fallbackUsed,
                                    LocalDateTime callTime,
                                    String errorMessage) {
        ApiCallLogEntity log = new ApiCallLogEntity();
        log.setProvider(provider);
        log.setApiName(apiName);
        log.setSuccess(success ? 1 : 0);
        log.setFallbackUsed(fallbackUsed ? 1 : 0);
        log.setCallTime(callTime);
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(128L);
        return log;
    }
}
