package com.lk.quantfund.vo.analytics;

import java.math.BigDecimal;

public record ProfitPeriodStatVO(
        String period,
        BigDecimal profit,
        BigDecimal profitRate
) {
}
