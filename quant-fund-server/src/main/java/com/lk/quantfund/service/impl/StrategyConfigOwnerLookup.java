package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.StrategyConfig;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.StrategyConfigMapper;
import com.lk.quantfund.service.ResourceOwnerLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StrategyConfigOwnerLookup implements ResourceOwnerLookup {

    private final StrategyConfigMapper strategyConfigMapper;

    public StrategyConfigOwnerLookup(StrategyConfigMapper strategyConfigMapper) {
        this.strategyConfigMapper = strategyConfigMapper;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.STRATEGY_CONFIG;
    }

    @Override
    public Optional<Long> findOwnerUserId(Long resourceId) {
        StrategyConfig config = strategyConfigMapper.selectById(resourceId);
        return config == null ? Optional.empty() : Optional.ofNullable(config.getUserId());
    }
}

