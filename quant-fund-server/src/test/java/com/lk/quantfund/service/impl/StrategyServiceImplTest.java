package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.dto.strategy.RiskProfileRequest;
import com.lk.quantfund.dto.strategy.StrategyConfigRequest;
import com.lk.quantfund.entity.RiskProfile;
import com.lk.quantfund.entity.StrategyConfig;
import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.StrategyType;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.RiskProfileMapper;
import com.lk.quantfund.mapper.StrategyConfigMapper;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.strategy.FundClassificationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class StrategyServiceImplTest {

    @Test
    void savePositionMonitorConfigSyncsRiskProfileLimits() {
        StrategyConfigMapper strategyConfigMapper = mock(StrategyConfigMapper.class);
        RiskProfileMapper riskProfileMapper = mock(RiskProfileMapper.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        StrategyConfig config = config();
        RiskProfile profile = riskProfile();
        when(strategyConfigMapper.selectOne(any())).thenReturn(config);
        when(riskProfileMapper.selectOne(any())).thenReturn(profile);
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of());
        StrategyServiceImpl service = new StrategyServiceImpl(
                List.of(),
                mock(FundClassificationService.class),
                new ObjectMapper(),
                fundHoldingMapper,
                mock(PortfolioAccountMapper.class),
                mock(FundNavDailyMapper.class),
                strategyConfigMapper,
                mock(StrategySignalMapper.class),
                riskProfileMapper
        );
        ArgumentCaptor<RiskProfile> profileCaptor = ArgumentCaptor.forClass(RiskProfile.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.saveConfig(new StrategyConfigRequest(
                    "仓位监控",
                    StrategyType.POSITION_MONITOR,
                    null,
                    "{\"equityLimitPct\":100,\"singleFundLimitPct\":35,\"largeRisePct\":2}",
                    true
            ));
        }

        verify(strategyConfigMapper).updateById(any(StrategyConfig.class));
        verify(riskProfileMapper).updateById(profileCaptor.capture());
        RiskProfile saved = profileCaptor.getValue();
        assertThat(saved.getMaxEquityPositionRate()).isEqualByComparingTo("100.0000");
        assertThat(saved.getMaxSingleFundPositionRate()).isEqualByComparingTo("35.0000");
        assertThat(saved.getDailyRiseAlertRate()).isEqualByComparingTo("2.0000");
        assertThat(saved.getDailyFallAlertRate()).isEqualByComparingTo("2.0000");
        assertThat(saved.getDrawdownAlertRate()).isEqualByComparingTo("8.0000");
    }

    @Test
    void updateRiskProfileSyncsPositionMonitorConfigLimits() {
        StrategyConfigMapper strategyConfigMapper = mock(StrategyConfigMapper.class);
        RiskProfileMapper riskProfileMapper = mock(RiskProfileMapper.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        StrategyConfig config = config();
        config.setParamsJson("{\"equityLimitPct\":70,\"singleFundLimitPct\":25,\"largeRisePct\":2}");
        RiskProfile profile = riskProfile();
        when(riskProfileMapper.selectOne(any())).thenReturn(profile);
        when(strategyConfigMapper.selectOne(any())).thenReturn(config);
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of());
        StrategyServiceImpl service = new StrategyServiceImpl(
                List.of(),
                mock(FundClassificationService.class),
                new ObjectMapper(),
                fundHoldingMapper,
                mock(PortfolioAccountMapper.class),
                mock(FundNavDailyMapper.class),
                strategyConfigMapper,
                mock(StrategySignalMapper.class),
                riskProfileMapper
        );
        ArgumentCaptor<StrategyConfig> configCaptor = ArgumentCaptor.forClass(StrategyConfig.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.updateRiskProfile(new RiskProfileRequest(
                    RiskLevel.MEDIUM,
                    new BigDecimal("100.0000"),
                    new BigDecimal("25.0000"),
                    new BigDecimal("8.0000"),
                    new BigDecimal("2.0000"),
                    new BigDecimal("2.0000"),
                    "{}"
            ));
        }

        verify(strategyConfigMapper).updateById(configCaptor.capture());
        StrategyConfig saved = configCaptor.getValue();
        assertThat(saved.getParamsJson()).contains("\"equityLimitPct\":100.0000");
        assertThat(saved.getParamsJson()).contains("\"singleFundLimitPct\":25.0000");
        assertThat(saved.getParamsJson()).contains("\"largeRisePct\":2.0000");
    }

    private StrategyConfig config() {
        StrategyConfig config = new StrategyConfig();
        config.setId(10L);
        config.setUserId(1L);
        config.setConfigName("仓位监控");
        config.setStrategyType(StrategyType.POSITION_MONITOR.name());
        config.setParamsJson("{}");
        config.setEnabled(1);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        config.setDeleted(0);
        return config;
    }

    private RiskProfile riskProfile() {
        RiskProfile profile = new RiskProfile();
        profile.setId(20L);
        profile.setUserId(1L);
        profile.setRiskLevel(RiskLevel.MEDIUM.name());
        profile.setMaxEquityPositionRate(new BigDecimal("70.0000"));
        profile.setMaxSingleFundPositionRate(new BigDecimal("25.0000"));
        profile.setDrawdownAlertRate(new BigDecimal("8.0000"));
        profile.setDailyRiseAlertRate(new BigDecimal("2.0000"));
        profile.setDailyFallAlertRate(new BigDecimal("2.0000"));
        profile.setConfigJson("{}");
        profile.setCreateTime(LocalDateTime.now());
        profile.setUpdateTime(LocalDateTime.now());
        profile.setDeleted(0);
        return profile;
    }
}
