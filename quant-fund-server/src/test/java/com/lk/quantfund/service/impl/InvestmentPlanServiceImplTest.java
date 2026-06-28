package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.InvestmentPlan;
import com.lk.quantfund.mapper.InvestmentPlanMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.scheduler.TradingCalendarService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class InvestmentPlanServiceImplTest {

    private final InvestmentPlanMapper investmentPlanMapper = mock(InvestmentPlanMapper.class);
    private final PortfolioAccountMapper portfolioAccountMapper = mock(PortfolioAccountMapper.class);
    private final InvestmentPlanServiceImpl service = new InvestmentPlanServiceImpl(
            investmentPlanMapper,
            portfolioAccountMapper,
            new TradingCalendarService(new QuantFundProperties())
    );

    @Test
    void resumePausedPlanShouldSkipMissedDates() {
        InvestmentPlan plan = plan("PAUSED", LocalDate.now().minusWeeks(4), "WEEKLY");
        when(investmentPlanMapper.selectOne(any())).thenReturn(plan);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);

            service.updateStatus(9L, "ENABLED");
        }

        ArgumentCaptor<InvestmentPlan> captor = ArgumentCaptor.forClass(InvestmentPlan.class);
        verify(investmentPlanMapper).updateById(captor.capture());
        InvestmentPlan saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ENABLED");
        assertThat(saved.getNextExecuteDate().isBefore(LocalDate.now())).isFalse();
    }

    @Test
    void pausePlanShouldKeepOriginalNextExecuteDate() {
        LocalDate nextDate = LocalDate.now().plusWeeks(1);
        InvestmentPlan plan = plan("ENABLED", nextDate, "WEEKLY");
        when(investmentPlanMapper.selectOne(any())).thenReturn(plan);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);

            service.updateStatus(9L, "PAUSED");
        }

        ArgumentCaptor<InvestmentPlan> captor = ArgumentCaptor.forClass(InvestmentPlan.class);
        verify(investmentPlanMapper).updateById(captor.capture());
        InvestmentPlan saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PAUSED");
        assertThat(saved.getNextExecuteDate()).isEqualTo(nextDate);
    }

    private InvestmentPlan plan(String status, LocalDate nextExecuteDate, String frequency) {
        InvestmentPlan plan = new InvestmentPlan();
        plan.setId(9L);
        plan.setUserId(1L);
        plan.setAccountId(1L);
        plan.setFundCode("016874");
        plan.setFundName("广发远见智选混合C");
        plan.setPlanName("广发远见智选混合C定投");
        plan.setPlanType("REGULAR_INVEST");
        plan.setFrequency(frequency);
        plan.setNextExecuteDate(nextExecuteDate);
        plan.setStatus(status);
        return plan;
    }
}
