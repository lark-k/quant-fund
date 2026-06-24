package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lk.quantfund.entity.InvestmentPlan;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.InvestmentPlanMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvestmentPlanOwnerLookupTest {

    private final InvestmentPlanMapper investmentPlanMapper = mock(InvestmentPlanMapper.class);
    private final InvestmentPlanOwnerLookup lookup = new InvestmentPlanOwnerLookup(investmentPlanMapper);

    @Test
    void resourceTypeShouldBeInvestmentPlan() {
        assertThat(lookup.resourceType()).isEqualTo(ResourceType.INVESTMENT_PLAN);
    }

    @Test
    void findOwnerUserIdShouldReturnOwnerWhenPlanExists() {
        InvestmentPlan plan = new InvestmentPlan();
        plan.setUserId(1001L);
        when(investmentPlanMapper.selectById(9L)).thenReturn(plan);

        Optional<Long> ownerUserId = lookup.findOwnerUserId(9L);

        assertThat(ownerUserId).contains(1001L);
    }

    @Test
    void findOwnerUserIdShouldReturnEmptyWhenPlanMissing() {
        when(investmentPlanMapper.selectById(9L)).thenReturn(null);

        assertThat(lookup.findOwnerUserId(9L)).isEmpty();
    }
}
