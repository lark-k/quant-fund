package com.lk.quantfund.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfitCalendarDayVO(
        LocalDate date,
        BigDecimal dailyProfit,
        BigDecimal dailyProfitRate,
        BigDecimal cumulativeProfit,
        String heatLevel,
        boolean tradingDay,
        String tradingDayLabel
) {
}
