package com.lk.quantfund.service;

import com.lk.quantfund.vo.analytics.ProfitAnalysisVO;
import com.lk.quantfund.vo.analytics.ProfitCalendarVO;
import java.time.LocalDate;
import java.time.YearMonth;

public interface AnalyticsService {

    ProfitAnalysisVO profitAnalysis(LocalDate startDate, LocalDate endDate);

    ProfitCalendarVO profitCalendar(YearMonth month);
}
