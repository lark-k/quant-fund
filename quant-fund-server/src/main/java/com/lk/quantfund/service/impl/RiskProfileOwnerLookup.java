package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.RiskProfile;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.RiskProfileMapper;
import com.lk.quantfund.service.ResourceOwnerLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RiskProfileOwnerLookup implements ResourceOwnerLookup {

    private final RiskProfileMapper riskProfileMapper;

    public RiskProfileOwnerLookup(RiskProfileMapper riskProfileMapper) {
        this.riskProfileMapper = riskProfileMapper;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.RISK_PROFILE;
    }

    @Override
    public Optional<Long> findOwnerUserId(Long resourceId) {
        RiskProfile profile = riskProfileMapper.selectById(resourceId);
        return profile == null ? Optional.empty() : Optional.ofNullable(profile.getUserId());
    }
}

