package com.lk.quantfund.service;

import com.lk.quantfund.vo.analytics.ProfitAnalysisVO;
import com.lk.quantfund.vo.analytics.ProfitCalendarVO;
import com.lk.quantfund.vo.analytics.ProfitIntradayTrendPointVO;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface AnalyticsService {

    default ProfitAnalysisVO profitAnalysis(LocalDate startDate, LocalDate endDate) {
        return profitAnalysis(startDate, endDate, null);
    }

    ProfitAnalysisVO profitAnalysis(LocalDate startDate, LocalDate endDate, String indexCode);

    List<ProfitIntradayTrendPointVO> intradayTrend(String indexCode);

    ProfitCalendarVO profitCalendar(YearMonth month);
}
