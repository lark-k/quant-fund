package com.lk.quantfund.vo.strategy;

import java.time.LocalDateTime;

public record StrategyConfigVO(
        Long id,
        String configName,
        String strategyType,
        String fundType,
        String paramsJson,
        Boolean enabled,
        LocalDateTime updateTime
) {
}

