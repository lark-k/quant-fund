package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.entity.AiAnalysisReport;
import com.lk.quantfund.entity.FundEstimateIntraday;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.StrategySignal;
import com.lk.quantfund.mapper.AiAnalysisReportMapper;
import com.lk.quantfund.mapper.FundEstimateIntradayMapper;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.scheduler.HoldingSnapshotBackfillService;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.DashboardService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import com.lk.quantfund.vo.ai.AiAnalysisReportVO;
import com.lk.quantfund.vo.dashboard.DashboardEstimateStatusVO;
import com.lk.quantfund.vo.dashboard.DashboardOverviewVO;
import com.lk.quantfund.vo.dashboard.DashboardPositionSliceVO;
import com.lk.quantfund.vo.dashboard.DashboardProfitTrendPointVO;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import com.lk.quantfund.vo.strategy.StrategySignalVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    private final PortfolioAccountService portfolioAccountService;
    private final FundHoldingMapper fundHoldingMapper;
    private final StrategySignalMapper strategySignalMapper;
    private final AiAnalysisReportMapper aiAnalysisReportMapper;
    private final HoldingSnapshotMapper holdingSnapshotMapper;
    private final FundEstimateIntradayMapper fundEstimateIntradayMapper;
    private final FundNavDailyMapper fundNavDailyMapper;
    private final ObjectMapper objectMapper;
    private final FundValuationService fundValuationService;
    private final TradingCalendarService tradingCalendarService;
    private final HoldingSnapshotBackfillService holdingSnapshotBackfillService;

    public DashboardServiceImpl(PortfolioAccountService portfolioAccountService,
                                FundHoldingMapper fundHoldingMapper,
                                StrategySignalMapper strategySignalMapper,
                                AiAnalysisReportMapper aiAnalysisReportMapper,
                                HoldingSnapshotMapper holdingSnapshotMapper,
                                FundEstimateIntradayMapper fundEstimateIntradayMapper,
                                FundNavDailyMapper fundNavDailyMapper,
                                ObjectMapper objectMapper,
                                FundValuationService fundValuationService,
                                TradingCalendarService tradingCalendarService,
                                HoldingSnapshotBackfillService holdingSnapshotBackfillService) {
        this.portfolioAccountService = portfolioAccountService;
        this.fundHoldingMapper = fundHoldingMapper;
        this.strategySignalMapper = strategySignalMapper;
        this.aiAnalysisReportMapper = aiAnalysisReportMapper;
        this.holdingSnapshotMapper = holdingSnapshotMapper;
        this.fundEstimateIntradayMapper = fundEstimateIntradayMapper;
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.objectMapper = objectMapper;
        this.fundValuationService = fundValuationService;
        this.tradingCalendarService = tradingCalendarService;
        this.holdingSnapshotBackfillService = holdingSnapshotBackfillService;
    }

    @Override
    public DashboardOverviewVO overview() {
        Long userId = UserContext.getUserId();
        holdingSnapshotBackfillService.ensureRecentSnapshots(userId);
        PortfolioSummaryVO summary = portfolioAccountService.summary();
        List<FundHolding> holdings = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId)
                .orderByDesc(FundHolding::getHoldingAmount));
        Set<Long> activeHoldingIds = holdings.stream()
                .map(FundHolding::getId)
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now();
        boolean intradayWindow = tradingCalendarService.isIntradayEstimateWindow(LocalDateTime.now());
        Map<String, FundNavDaily> latestOfficialNavByFund = latestOfficialNavByFund(holdings.stream()
                .map(FundHolding::getFundCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet()));
        List<FundHoldingVO> topHoldings = holdings.stream()
                .limit(10)
                .map(holding -> toHoldingVO(holding, intradayWindow, latestOfficialNavByFund.get(holding.getFundCode())))
                .toList();
        PortfolioSummaryVO effectiveSummary = dashboardSummary(summary, topHoldings);
        List<StrategySignal> signals = strategySignalMapper.selectList(new LambdaQueryWrapper<StrategySignal>()
                .eq(StrategySignal::getUserId, userId)
                .orderByDesc(StrategySignal::getSignalTime)
                .last("LIMIT 10"));
        List<AiAnalysisReport> todayAiReports = aiAnalysisReportMapper.selectList(new LambdaQueryWrapper<AiAnalysisReport>()
                .eq(AiAnalysisReport::getUserId, userId)
                .ge(AiAnalysisReport::getAnalysisTime, today.atStartOfDay())
                .orderByAsc(AiAnalysisReport::getFallbackUsed)
                .orderByDesc(AiAnalysisReport::getAnalysisTime)
                .last("LIMIT 50"));
        List<AiAnalysisReportVO> todayAiSuggestions = todayAiReports.stream()
                .filter(report -> activeHoldingIds.contains(report.getHoldingId()))
                .map(this::toAiReportVO)
                .filter(report -> !Boolean.TRUE.equals(report.fallbackUsed()))
                .limit(10)
                .toList();
        return new DashboardOverviewVO(
                effectiveSummary,
                topHoldings,
                positionDistribution(effectiveSummary),
                profitTrend(userId),
                signals.stream().map(this::toSignalVO).toList(),
                todayAiSuggestions,
                estimateStatus(holdings, today, intradayWindow, latestOfficialNavByFund),
                riskAlertCount(signals),
                todayAiSuggestions.size(),
                SystemConstants.DISCLAIMER
        );
    }

    private List<DashboardPositionSliceVO> positionDistribution(PortfolioSummaryVO summary) {
        return List.of(
                new DashboardPositionSliceVO("权益基金", scale(summary.equityPositionRate())),
                new DashboardPositionSliceVO("债券基金", scale(summary.bondPositionRate())),
                new DashboardPositionSliceVO("现金", scale(summary.cashPositionRate()))
        );
    }

    private List<DashboardProfitTrendPointVO> profitTrend(Long userId) {
        LocalDate startDate = LocalDate.now().minusDays(29);
        List<HoldingSnapshot> snapshots = holdingSnapshotMapper.selectList(new LambdaQueryWrapper<HoldingSnapshot>()
                .eq(HoldingSnapshot::getUserId, userId)
                .ge(HoldingSnapshot::getSnapshotDate, startDate)
                .orderByAsc(HoldingSnapshot::getSnapshotDate));
        Map<LocalDate, List<HoldingSnapshot>> byDate = snapshots.stream()
                .collect(Collectors.groupingBy(HoldingSnapshot::getSnapshotDate, LinkedHashMap::new, Collectors.toList()));
        return byDate.entrySet().stream()
                .map(entry -> new DashboardProfitTrendPointVO(
                        entry.getKey(),
                        entry.getValue().stream().map(HoldingSnapshot::getTotalAsset).filter(value -> value != null).max(BigDecimal::compareTo).orElse(ZERO),
                        sum(entry.getValue().stream().map(HoldingSnapshot::getHoldingProfit).toList()),
                        sum(entry.getValue().stream().map(HoldingSnapshot::getDailyProfit).toList())
                ))
                .toList();
    }

    private DashboardEstimateStatusVO estimateStatus(List<FundHolding> holdings, LocalDate today,
                                                     boolean intradayWindow,
                                                     Map<String, FundNavDaily> latestOfficialNavByFund) {
        Set<String> fundCodes = holdings.stream()
                .map(FundHolding::getFundCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        if (fundCodes.isEmpty()) {
            return new DashboardEstimateStatusVO(0, 0, 0, null, "暂无持仓基金");
        }
        List<FundEstimateIntraday> estimates = intradayWindow
                ? fundEstimateIntradayMapper.selectList(new LambdaQueryWrapper<FundEstimateIntraday>()
                        .in(FundEstimateIntraday::getFundCode, fundCodes)
                        .eq(FundEstimateIntraday::getEstimateDate, today)
                        .orderByDesc(FundEstimateIntraday::getEstimateTime))
                : List.of();
        Map<String, FundEstimateIntraday> latestByFund = new LinkedHashMap<>();
        for (FundEstimateIntraday estimate : estimates) {
            latestByFund.putIfAbsent(estimate.getFundCode(), estimate);
        }
        int delayedCount = (int) latestByFund.values().stream()
                .filter(estimate -> estimate.getDelayed() != null && estimate.getDelayed() == 1)
                .count();
        int officialSyncedCount = (int) holdings.stream()
                .filter(holding -> officialNavUpdated(holding, latestOfficialNavByFund.get(holding.getFundCode())))
                .map(FundHolding::getFundCode)
                .distinct()
                .count();
        LocalDateTime latestTime = latestByFund.values().stream()
                .map(FundEstimateIntraday::getEstimateTime)
                .filter(time -> time != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (officialSyncedCount > 0) {
            latestTime = latestOfficialNavByFund.values().stream()
                    .map(FundNavDaily::getUpdateTime)
                    .filter(time -> time != null)
                    .max(Comparator.naturalOrder())
                    .orElse(latestTime);
        }
        String marketStatus = holdings.stream()
                .map(holding -> fundValuationService.marketStatus(holding.getFundName(), holding.getFundType()))
                .filter(status -> "港股交易中".equals(status))
                .findFirst()
                .orElseGet(() -> holdings.stream()
                        .map(holding -> fundValuationService.marketStatus(holding.getFundName(), holding.getFundType()))
                        .findFirst()
                        .orElse("暂无持仓基金"));
        String statusText = officialSyncedCount >= fundCodes.size()
                ? marketStatus + "，最新正式净值已同步"
                : !intradayWindow
                ? (tradingCalendarService.isBeforeIntradayEstimateWindow(LocalDateTime.now())
                        ? marketStatus + "，未开盘，盘中估值未开始"
                        : marketStatus + "，非盘中估值时间")
                : latestByFund.isEmpty()
                ? marketStatus + "，今日估值尚未刷新"
                : delayedCount > 0 ? marketStatus + "，部分估值延迟" : marketStatus + "，今日估值已刷新";
        return new DashboardEstimateStatusVO(fundCodes.size(), Math.max(latestByFund.size(), officialSyncedCount), delayedCount, latestTime, statusText);
    }

    private int riskAlertCount(List<StrategySignal> signals) {
        return (int) signals.stream()
                .filter(signal -> "HIGH".equals(signal.getRiskLevel())
                        || "SELL".equals(signal.getAction())
                        || "CONVERT".equals(signal.getAction()))
                .count();
    }

    private FundHoldingVO toHoldingVO(FundHolding holding) {
        return toHoldingVO(holding, tradingCalendarService.isIntradayEstimateWindow(LocalDateTime.now()), latestOfficialNav(holding.getFundCode()));
    }

    private FundHoldingVO toHoldingVO(FundHolding holding, boolean intradayWindow, FundNavDaily officialNav) {
        BigDecimal estimateRate = currentEstimateGrowthRate(holding);
        boolean intradayAllowed = intradayWindow && !delayedOfficialNavFund(holding);
        boolean officialUpdated = officialNavUpdated(holding, officialNav);
        BigDecimal dailyProfit = officialUpdated || intradayAllowed ? scale(holding.getDailyProfit()) : ZERO;
        BigDecimal displayEstimateRate = officialUpdated || intradayAllowed ? estimateRate : ZERO;
        FundValuationResult valuation = fundValuationService.estimate(
                holding.getFundCode(), holding.getFundName(), holding.getFundType(), displayEstimateRate);
        if (!officialUpdated && !intradayAllowed) {
            valuation = new FundValuationResult(
                    valuation.themeName(),
                    ZERO,
                    valuation.sourceName(),
                    valuation.basis(),
                    valuation.marketStatus()
            );
        }
        BigDecimal accountTotal = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getAccountId, holding.getAccountId()))
                .stream()
                .map(FundHolding::getHoldingAmount)
                .map(this::scale)
                .reduce(ZERO, BigDecimal::add);
        return new FundHoldingVO(
                holding.getId(),
                holding.getAccountId(),
                holding.getFundCode(),
                holding.getFundName(),
                holding.getFundType(),
                holding.getActiveFund() != null && holding.getActiveFund() == 1,
                scale(holding.getHoldingAmount()),
                scale(holding.getHoldingShare()),
                scale(holding.getHoldingCost()),
                holding.getCurrentEstimateNav(),
                holding.getLatestOfficialNav(),
                scale(holding.getHoldingProfit()),
                scale(holding.getHoldingProfitRate()),
                dailyProfit,
                ZERO,
                rate(scale(holding.getHoldingAmount()), accountTotal),
                displayEstimateRate,
                officialUpdated,
                officialUpdated ? officialNav.getNavDate() : null,
                valuation.themeName(),
                valuation.themeRate(),
                valuation.sourceName(),
                valuation.basis(),
                valuation.marketStatus(),
                holding.getHoldingDays(),
                holding.getSourcePlatform(),
                holding.getRegularInvestment() != null && holding.getRegularInvestment() == 1,
                holding.getCoreHolding() != null && holding.getCoreHolding() == 1,
                holding.getWatchFocus() != null && holding.getWatchFocus() == 1,
                holding.getUpdateTime(),
                SystemConstants.DISCLAIMER
        );
    }

    private BigDecimal currentEstimateGrowthRate(FundHolding holding) {
        FundNavDaily officialNav = latestOfficialNav(holding.getFundCode());
        if (officialNavUpdated(holding, officialNav) && officialNav.getDailyGrowthRate() != null) {
            return scale(officialNav.getDailyGrowthRate());
        }
        if (!tradingCalendarService.isIntradayEstimateWindow(LocalDateTime.now()) || delayedOfficialNavFund(holding)) {
            return ZERO;
        }
        if (holding.getCurrentEstimateNav() == null || holding.getLatestOfficialNav() == null
                || holding.getLatestOfficialNav().compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return rate(holding.getCurrentEstimateNav().subtract(holding.getLatestOfficialNav()), holding.getLatestOfficialNav());
    }

    private boolean officialNavUpdated(FundHolding holding, FundNavDaily nav) {
        if (nav == null) {
            return false;
        }
        return nav.getNavDate() != null && nav.getNavDate().equals(LocalDate.now());
    }

    private FundNavDaily latestOfficialNav(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) {
            return null;
        }
        return fundNavDailyMapper.selectOne(new LambdaQueryWrapper<FundNavDaily>()
                .eq(FundNavDaily::getFundCode, fundCode)
                .orderByDesc(FundNavDaily::getNavDate)
                .last("LIMIT 1"));
    }

    private Map<String, FundNavDaily> latestOfficialNavByFund(Set<String> fundCodes) {
        if (fundCodes.isEmpty()) {
            return Map.of();
        }
        List<FundNavDaily> navs = fundNavDailyMapper.selectList(new LambdaQueryWrapper<FundNavDaily>()
                .in(FundNavDaily::getFundCode, fundCodes)
                .orderByDesc(FundNavDaily::getNavDate));
        Map<String, FundNavDaily> latest = new LinkedHashMap<>();
        for (FundNavDaily nav : navs) {
            latest.putIfAbsent(nav.getFundCode(), nav);
        }
        return latest;
    }

    private boolean delayedOfficialNavFund(FundHolding holding) {
        String name = holding.getFundName() == null ? "" : holding.getFundName().toUpperCase();
        String type = holding.getFundType() == null ? "" : holding.getFundType().toUpperCase();
        if (name.contains("恒生") || name.contains("港股") || name.contains("香港") || name.contains("H股")) {
            return false;
        }
        return type.contains("QDII")
                || name.contains("QDII")
                || name.contains("全球")
                || name.contains("海外")
                || name.contains("美国")
                || name.contains("美股")
                || name.contains("纳指")
                || name.contains("纳斯达克")
                || name.contains("标普")
                || name.contains("道琼斯")
                || name.contains("美元")
                || name.contains("人民币");
    }

    private PortfolioSummaryVO dashboardSummary(PortfolioSummaryVO summary, List<FundHoldingVO> topHoldings) {
        BigDecimal dailyProfit = topHoldings.stream()
                .map(FundHoldingVO::dailyProfit)
                .map(this::scale)
                .reduce(ZERO, BigDecimal::add);
        return new PortfolioSummaryVO(
                summary.totalAsset(),
                summary.totalInvestAmount(),
                summary.currentProfit(),
                summary.currentProfitRate(),
                dailyProfit,
                summary.equityPositionRate(),
                summary.bondPositionRate(),
                summary.cashPositionRate(),
                summary.holdingCount(),
                summary.accounts()
        );
    }

    private StrategySignalVO toSignalVO(StrategySignal signal) {
        return new StrategySignalVO(
                signal.getId(),
                signal.getAccountId(),
                signal.getHoldingId(),
                signal.getFundCode(),
                signal.getSignalType(),
                signal.getAction(),
                signal.getActionText(),
                scale(signal.getSuggestAmount()),
                scale(signal.getSuggestRatio()),
                signal.getRiskLevel(),
                scale(signal.getConfidence()),
                readStringList(signal.getReasonJson()),
                signal.getSignalTime(),
                SystemConstants.DISCLAIMER
        );
    }

    private AiAnalysisReportVO toAiReportVO(AiAnalysisReport report) {
        return new AiAnalysisReportVO(
                report.getId(),
                report.getAccountId(),
                report.getHoldingId(),
                report.getFundCode(),
                report.getModelName(),
                report.getAction(),
                report.getActionText(),
                scale(report.getSuggestAmount()),
                scale(report.getSuggestRatio()),
                scale(report.getConfidence()),
                report.getRiskLevel(),
                report.getDeadline(),
                report.getStrategy(),
                readStringList(report.getReasonsJson()),
                readStringList(report.getRisksJson()),
                report.getDataSummary(),
                report.getFinalConclusion(),
                fallbackUsed(report),
                report.getAnalysisTime(),
                SystemConstants.DISCLAIMER
        );
    }

    private boolean fallbackUsed(AiAnalysisReport report) {
        if (report.getFallbackUsed() != null && report.getFallbackUsed() == 1) {
            return true;
        }
        String strategy = report.getStrategy() == null ? "" : report.getStrategy();
        String reasons = report.getReasonsJson() == null ? "" : report.getReasonsJson();
        String summary = report.getDataSummary() == null ? "" : report.getDataSummary();
        return strategy.contains("Mock AI")
                || reasons.contains("mock")
                || reasons.contains("未配置真实 Key")
                || summary.contains("AI 分析不可用");
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .filter(value -> value != null)
                .map(this::scale)
                .reduce(ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal base = scale(denominator);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return scale(numerator).multiply(ONE_HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }
}
