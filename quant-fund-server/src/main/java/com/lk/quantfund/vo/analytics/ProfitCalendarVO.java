package com.lk.quantfund.vo.analytics;

import java.math.BigDecimal;
import java.util.List;

public record ProfitCalendarVO(
        String month,
        BigDecimal monthlyProfit,
        BigDecimal monthlyProfitRate,
        List<ProfitCalendarDayVO> days,
        List<FundProfitRankVO> profitTop5,
        List<FundProfitRankVO> lossTop5,
        String disclaimer
) {
}
