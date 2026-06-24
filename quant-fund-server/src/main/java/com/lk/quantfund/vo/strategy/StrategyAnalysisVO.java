package com.lk.quantfund.vo.strategy;

import java.util.List;

public record StrategyAnalysisVO(
        Long accountId,
        Long holdingId,
        String fundCode,
        String fundName,
        String classifiedFundType,
        List<StrategySignalVO> signals,
        String disclaimer
) {
}

