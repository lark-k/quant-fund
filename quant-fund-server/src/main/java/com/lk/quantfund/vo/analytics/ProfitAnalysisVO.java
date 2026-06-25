package com.lk.quantfund.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfitAnalysisVO(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal todayProfit,
        BigDecimal weekProfit,
        BigDecimal monthProfit,
        BigDecimal yearProfit,
        BigDecimal totalProfit,
        BigDecimal selectedRangeProfit,
        BigDecimal selectedRangeProfitRate,
        List<ProfitPeriodStatVO> periodStats,
        List<ProfitTrendPointVO> trend,
        List<FundProfitRankVO> profitTop5,
        List<FundProfitRankVO> lossTop5,
        IndexCompareVO indexCompare,
        String indexCompareStatus,
        String disclaimer
) {
}
