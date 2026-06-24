package com.lk.quantfund.vo.strategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RiskProfileVO(
        Long id,
        String riskLevel,
        BigDecimal maxEquityPositionRate,
        BigDecimal maxSingleFundPositionRate,
        BigDecimal drawdownAlertRate,
        BigDecimal dailyRiseAlertRate,
        BigDecimal dailyFallAlertRate,
        String configJson,
        LocalDateTime updateTime
) {
}

