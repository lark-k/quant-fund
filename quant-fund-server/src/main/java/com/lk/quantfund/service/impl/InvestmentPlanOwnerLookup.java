package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.InvestmentPlan;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.InvestmentPlanMapper;
import com.lk.quantfund.service.ResourceOwnerLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InvestmentPlanOwnerLookup implements ResourceOwnerLookup {

    private final InvestmentPlanMapper investmentPlanMapper;

    public InvestmentPlanOwnerLookup(InvestmentPlanMapper investmentPlanMapper) {
        this.investmentPlanMapper = investmentPlanMapper;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.INVESTMENT_PLAN;
    }

    @Override
    public Optional<Long> findOwnerUserId(Long resourceId) {
        InvestmentPlan plan = investmentPlanMapper.selectById(resourceId);
        return plan == null ? Optional.empty() : Optional.ofNullable(plan.getUserId());
    }
}
