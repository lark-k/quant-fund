package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.StrategySignal;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.service.ResourceOwnerLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StrategySignalOwnerLookup implements ResourceOwnerLookup {

    private final StrategySignalMapper strategySignalMapper;

    public StrategySignalOwnerLookup(StrategySignalMapper strategySignalMapper) {
        this.strategySignalMapper = strategySignalMapper;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.STRATEGY_SIGNAL;
    }

    @Override
    public Optional<Long> findOwnerUserId(Long resourceId) {
        StrategySignal signal = strategySignalMapper.selectById(resourceId);
        return signal == null ? Optional.empty() : Optional.ofNullable(signal.getUserId());
    }
}

