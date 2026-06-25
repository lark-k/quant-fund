package com.lk.quantfund.vo.dashboard;

import com.lk.quantfund.vo.ai.AiAnalysisReportVO;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import com.lk.quantfund.vo.strategy.StrategySignalVO;
import java.util.List;

public record DashboardOverviewVO(
        PortfolioSummaryVO summary,
        List<FundHoldingVO> topHoldings,
        List<DashboardPositionSliceVO> positionDistribution,
        List<DashboardProfitTrendPointVO> profitTrend,
        List<StrategySignalVO> latestStrategySignals,
        List<AiAnalysisReportVO> todayAiSuggestions,
        DashboardEstimateStatusVO estimateStatus,
        List<DashboardRiskAlertVO> riskAlerts,
        Integer riskAlertCount,
        Integer aiSuggestionCount,
        String disclaimer
) {
}
