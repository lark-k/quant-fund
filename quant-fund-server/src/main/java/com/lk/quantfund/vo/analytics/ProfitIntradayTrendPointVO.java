package com.lk.quantfund.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProfitIntradayTrendPointVO(
        LocalDateTime time,
        BigDecimal portfolioReturn,
        BigDecimal indexReturn,
        BigDecimal dailyProfit
) {
}
