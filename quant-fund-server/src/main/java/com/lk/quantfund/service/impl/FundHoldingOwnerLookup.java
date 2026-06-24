package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.service.ResourceOwnerLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FundHoldingOwnerLookup implements ResourceOwnerLookup {

    private final FundHoldingMapper fundHoldingMapper;

    public FundHoldingOwnerLookup(FundHoldingMapper fundHoldingMapper) {
        this.fundHoldingMapper = fundHoldingMapper;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.FUND_HOLDING;
    }

    @Override
    public Optional<Long> findOwnerUserId(Long resourceId) {
        FundHolding holding = fundHoldingMapper.selectById(resourceId);
        return holding == null ? Optional.empty() : Optional.ofNullable(holding.getUserId());
    }
}

