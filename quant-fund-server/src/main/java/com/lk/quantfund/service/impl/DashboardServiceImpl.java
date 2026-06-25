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
import com.lk.quantfund.scheduler.MarketType;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.DashboardService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.analytics.OfficialNavTiming;
import com.lk.quantfund.service.analytics.SnapshotProfitStatusResolver;
import com.lk.quantfund.service.analytics.SnapshotProfitStatusResolver.ProfitStatus;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import com.lk.quantfund.vo.ai.AiAnalysisReportVO;
import com.lk.quantfund.vo.dashboard.DashboardEstimateStatusVO;
import com.lk.quantfund.vo.dashboard.DashboardOverviewVO;
import com.lk.quantfund.vo.dashboard.DashboardPositionSliceVO;
import com.lk.quantfund.vo.dashboard.DashboardProfitTrendPointVO;
import com.lk.quantfund.vo.dashboard.DashboardRiskAlertVO;
import com.lk.quantfund.vo.dashboard.MarketSessionItemVO;
import com.lk.quantfund.vo.dashboard.MarketSessionStatusVO;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import com.lk.quantfund.vo.strategy.StrategySignalVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
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
    private final MarketDataService marketDataService;
    private final SnapshotProfitStatusResolver snapshotProfitStatusResolver;

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
                                HoldingSnapshotBackfillService holdingSnapshotBackfillService,
                                MarketDataService marketDataService,
                                SnapshotProfitStatusResolver snapshotProfitStatusResolver) {
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
        this.marketDataService = marketDataService;
        this.snapshotProfitStatusResolver = snapshotProfitStatusResolver;
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
        LocalDate today = today();
        LocalDateTime now = now();
        boolean intradayWindow = tradingCalendarService.isIntradayEstimateWindow(now);
        boolean intradayDisplayWindow = tradingCalendarService.isIntradayEstimateDisplayWindow(now);
        Map<String, FundNavDaily> latestOfficialNavByFund = latestOfficialNavByFund(holdings.stream()
                .map(FundHolding::getFundCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet()));
        List<FundHoldingVO> topHoldings = holdings.stream()
                .limit(10)
                .map(holding -> toHoldingVO(holding, intradayDisplayWindow, latestOfficialNavByFund))
                .toList();
        PortfolioSummaryVO effectiveSummary = dashboardSummary(summary, holdings, latestOfficialNavByFund, dashboardDailyProfit(holdings, intradayDisplayWindow, latestOfficialNavByFund));
        List<StrategySignal> signals = activeHoldingIds.isEmpty()
                ? List.of()
                : strategySignalMapper.selectList(new LambdaQueryWrapper<StrategySignal>()
                .eq(StrategySignal::getUserId, userId)
                .in(StrategySignal::getHoldingId, activeHoldingIds)
                .orderByDesc(StrategySignal::getSignalTime)
                .orderByDesc(StrategySignal::getId));
        Map<Long, String> holdingNameById = holdings.stream()
                .collect(Collectors.toMap(FundHolding::getId, FundHolding::getFundName, (left, right) -> left));
        List<StrategySignal> latestStrategySignals = latestStrategySignals(signals, activeHoldingIds);
        List<AiAnalysisReport> todayAiReports = aiAnalysisReportMapper.selectList(new LambdaQueryWrapper<AiAnalysisReport>()
                .eq(AiAnalysisReport::getUserId, userId)
                .ge(AiAnalysisReport::getAnalysisTime, today.atStartOfDay())
                .orderByAsc(AiAnalysisReport::getFallbackUsed)
                .orderByDesc(AiAnalysisReport::getAnalysisTime)
                .last("LIMIT 50"));
        List<AiAnalysisReportVO> todayAiSuggestions = latestAiSuggestions(todayAiReports, activeHoldingIds)
                .stream()
                .map(this::toAiReportVO)
                .toList();
        List<DashboardRiskAlertVO> riskAlerts = riskAlerts(signals, todayAiReports, holdingNameById, activeHoldingIds);
        return new DashboardOverviewVO(
                effectiveSummary,
                topHoldings,
                positionDistribution(effectiveSummary),
                profitTrend(userId, effectiveSummary, intradayDisplayWindow, holdings),
                latestStrategySignals.stream().map(signal -> toSignalVO(signal, holdingNameById)).toList(),
                todayAiSuggestions,
                estimateStatus(holdings, today, intradayWindow, latestOfficialNavByFund),
                riskAlerts,
                riskAlerts.size(),
                todayAiSuggestions.size(),
                SystemConstants.DISCLAIMER
        );
    }

    @Override
    public MarketSessionStatusVO marketStatus() {
        LocalDateTime now = now();
        List<MarketSessionItemVO> markets = List.of(
                marketItem(MarketType.A_SHARE, "A股", now),
                marketItem(MarketType.HONG_KONG, "港股", now),
                marketItem(MarketType.US, "美股", now)
        );
        MarketSessionItemVO primary = markets.stream()
                .filter(MarketSessionItemVO::trading)
                .findFirst()
                .orElse(markets.getFirst());
        return new MarketSessionStatusVO(
                primary.statusText(),
                primary.trading(),
                now,
                markets
        );
    }

    private List<DashboardPositionSliceVO> positionDistribution(PortfolioSummaryVO summary) {
        return List.of(
                new DashboardPositionSliceVO("权益基金", scale(summary.equityPositionRate())),
                new DashboardPositionSliceVO("债券基金", scale(summary.bondPositionRate())),
                new DashboardPositionSliceVO("现金", scale(summary.cashPositionRate()))
        );
    }

    private MarketSessionItemVO marketItem(MarketType market, String label, LocalDateTime now) {
        String status = tradingCalendarService.marketSession(market, now);
        return new MarketSessionItemVO(label, status, tradingCalendarService.isTradingWindow(market, now));
    }

    private List<DashboardProfitTrendPointVO> profitTrend(Long userId, PortfolioSummaryVO summary,
                                                          boolean intradayDisplayWindow, List<FundHolding> holdings) {
        LocalDate today = today();
        LocalDate startDate = today.minusDays(29);
        LocalDate endDate = today;
        List<HoldingSnapshot> snapshots = holdingSnapshotMapper.selectList(new LambdaQueryWrapper<HoldingSnapshot>()
                .eq(HoldingSnapshot::getUserId, userId)
                .ge(HoldingSnapshot::getSnapshotDate, startDate)
                .orderByAsc(HoldingSnapshot::getSnapshotDate));
        Map<LocalDate, List<HoldingSnapshot>> byDate = snapshots.stream()
                .collect(Collectors.groupingBy(HoldingSnapshot::getSnapshotDate, LinkedHashMap::new, Collectors.toList()));
        Map<LocalDate, BigDecimal> indexReturns = indexReturns(startDate, endDate);
        Map<LocalDate, ProfitStatus> statusByDate = snapshotProfitStatusResolver.resolve(snapshots, holdings, startDate, endDate);
        List<DashboardProfitTrendPointVO> trend = byDate.entrySet().stream()
                .map(entry -> {
                    ProfitStatus status = statusByDate.getOrDefault(entry.getKey(), snapshotProfitStatusResolver.snapshotSynced());
                    return new DashboardProfitTrendPointVO(
                            entry.getKey(),
                            entry.getValue().stream().map(HoldingSnapshot::getTotalAsset).filter(value -> value != null).max(BigDecimal::compareTo).orElse(ZERO),
                            sum(entry.getValue().stream().map(HoldingSnapshot::getHoldingProfit).toList()),
                            sum(entry.getValue().stream().map(HoldingSnapshot::getDailyProfit).toList()),
                            indexReturns.get(entry.getKey()),
                            status.code(),
                            status.text()
                    );
                })
                .toList();
        return withCurrentDayEstimate(trend, today, summary, intradayDisplayWindow);
    }

    private List<DashboardProfitTrendPointVO> withCurrentDayEstimate(List<DashboardProfitTrendPointVO> trend, LocalDate today, PortfolioSummaryVO summary, boolean intradayDisplayWindow) {
        DashboardProfitTrendPointVO existingToday = trend.stream()
                .filter(point -> today.equals(point.date()))
                .findFirst()
                .orElse(null);
        if (!intradayDisplayWindow
                || !tradingCalendarService.isTradingDay(today)
                || (existingToday != null && "CONFIRMED".equals(existingToday.profitStatus()))
                || scale(summary.dailyProfit()).compareTo(BigDecimal.ZERO) == 0) {
            return trend;
        }
        List<DashboardProfitTrendPointVO> withToday = trend.stream()
                .filter(point -> !today.equals(point.date()))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        BigDecimal todayProfit = scale(summary.dailyProfit());
        withToday.add(new DashboardProfitTrendPointVO(
                today,
                scale(summary.totalAsset()),
                scale(summary.currentProfit()),
                todayProfit,
                null,
                "ESTIMATED",
                "盘中预估，待正式净值确认"
        ));
        withToday.sort(Comparator.comparing(DashboardProfitTrendPointVO::date));
        return withToday;
    }

    private Map<LocalDate, BigDecimal> indexReturns(LocalDate startDate, LocalDate endDate) {
        List<MarketIndexDailyVO> history;
        try {
            history = marketDataService.historicalIndex("000300", startDate, endDate);
        } catch (Exception exception) {
            return Map.of();
        }
        if (history.size() < 2 || history.getFirst().closePrice() == null || history.getFirst().closePrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of();
        }
        BigDecimal baseClose = history.getFirst().closePrice();
        Map<LocalDate, BigDecimal> returns = new LinkedHashMap<>();
        for (MarketIndexDailyVO point : history) {
            if (point.tradeDate() != null && point.closePrice() != null) {
                returns.put(point.tradeDate(), point.closePrice().subtract(baseClose).multiply(ONE_HUNDRED).divide(baseClose, 4, RoundingMode.HALF_UP));
            }
        }
        return returns;
    }

    protected LocalDate today() {
        return LocalDate.now();
    }

    protected LocalDateTime now() {
        return LocalDateTime.now();
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
                ? (tradingCalendarService.isBeforeIntradayEstimateWindow(now())
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

    private List<StrategySignal> latestStrategySignals(List<StrategySignal> signals, Set<Long> activeHoldingIds) {
        if (signals.isEmpty()) {
            return List.of();
        }
        Comparator<StrategySignal> latestFirst = Comparator
                .comparing(StrategySignal::getSignalTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StrategySignal::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed();
        Map<Long, StrategySignal> latestByHolding = new LinkedHashMap<>();
        signals.stream()
                .filter(signal -> signal.getHoldingId() != null && activeHoldingIds.contains(signal.getHoldingId()))
                .sorted(latestFirst)
                .forEach(signal -> latestByHolding.putIfAbsent(signal.getHoldingId(), signal));
        return new ArrayList<>(latestByHolding.values());
    }

    private List<AiAnalysisReport> latestAiSuggestions(List<AiAnalysisReport> reports, Set<Long> activeHoldingIds) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Comparator<AiAnalysisReport> latestFirst = Comparator
                .comparing(AiAnalysisReport::getAnalysisTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AiAnalysisReport::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed();
        Map<Long, AiAnalysisReport> latestByHolding = new LinkedHashMap<>();
        reports.stream()
                .filter(report -> activeHoldingIds.contains(report.getHoldingId()))
                .filter(report -> !Boolean.TRUE.equals(report.getFallbackUsed()))
                .sorted(latestFirst)
                .forEach(report -> latestByHolding.putIfAbsent(report.getHoldingId(), report));
        return new ArrayList<>(latestByHolding.values());
    }

    private List<DashboardRiskAlertVO> riskAlerts(List<StrategySignal> signals,
                                                  List<AiAnalysisReport> reports,
                                                  Map<Long, String> holdingNameById,
                                                  Set<Long> activeHoldingIds) {
        List<DashboardRiskAlertVO> alerts = new ArrayList<>();
        signals.stream()
                .filter(signal -> activeHoldingIds.contains(signal.getHoldingId()))
                .filter(this::isRiskAlertSignal)
                .forEach(signal -> alerts.add(strategyRiskAlert(signal, holdingNameById)));
        reports.stream()
                .filter(report -> activeHoldingIds.contains(report.getHoldingId()))
                .filter(report -> !fallbackUsed(report))
                .filter(this::isRiskAlertReport)
                .forEach(report -> alerts.add(aiRiskAlert(report, holdingNameById)));
        return alerts.stream()
                .collect(Collectors.groupingBy(this::riskAlertGroupKey, LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(this::mergeRiskAlerts)
                .sorted(Comparator.comparing(DashboardRiskAlertVO::alertTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(8)
                .toList();
    }

    private String riskAlertGroupKey(DashboardRiskAlertVO alert) {
        return alert.fundCode() == null || alert.fundCode().isBlank() ? alert.id() : alert.fundCode();
    }

    private DashboardRiskAlertVO mergeRiskAlerts(List<DashboardRiskAlertVO> alerts) {
        DashboardRiskAlertVO latest = alerts.stream()
                .max(Comparator.comparing(DashboardRiskAlertVO::alertTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(alerts.getFirst());
        String riskLevel = alerts.stream()
                .map(DashboardRiskAlertVO::riskLevel)
                .max(Comparator.comparingInt(this::riskLevelRank))
                .orElse(latest.riskLevel());
        List<String> alertTypes = alerts.stream()
                .map(DashboardRiskAlertVO::alertType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .toList();
        List<String> contents = alerts.stream()
                .sorted(Comparator.comparing(DashboardRiskAlertVO::alertTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(alert -> defaultText(alert.alertType(), "风险") + "：" + alert.content())
                .distinct()
                .toList();
        String typeText = joinLimited(alertTypes, 3);
        String contentText = joinLimited(contents, 3);
        return new DashboardRiskAlertVO(
                "RISK_" + riskAlertGroupKey(latest),
                alerts.stream().map(DashboardRiskAlertVO::sourceType).distinct().count() > 1 ? "MIXED" : latest.sourceType(),
                typeText,
                riskLevel,
                displayRiskLevel(riskLevel) + " · " + (alertTypes.size() > 1 ? "综合风险" : typeText),
                latest.fundCode(),
                latest.fundName(),
                contentText + (contents.size() > 3 ? "；等 " + contents.size() + " 项风险" : ""),
                latest.alertTime()
        );
    }

    private String joinLimited(List<String> values, int limit) {
        if (values.isEmpty()) {
            return "风险预警";
        }
        String joined = values.stream().limit(limit).collect(Collectors.joining(" / "));
        return values.size() > limit ? joined + " / +" + (values.size() - limit) : joined;
    }

    private int riskLevelRank(String riskLevel) {
        return switch (riskLevel == null ? "" : riskLevel) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private boolean isRiskAlertSignal(StrategySignal signal) {
        return "HIGH".equals(signal.getRiskLevel())
                || "SELL".equals(signal.getAction())
                || "CONVERT".equals(signal.getAction())
                || "MARKET_RISK".equals(signal.getSignalType())
                || "POSITION_RISK".equals(signal.getSignalType())
                || "RISK_ALERT".equals(signal.getSignalType());
    }

    private boolean isRiskAlertReport(AiAnalysisReport report) {
        return ("HIGH".equals(report.getRiskLevel()) || "MEDIUM".equals(report.getRiskLevel()))
                && !readStringList(report.getRisksJson()).isEmpty();
    }

    private DashboardRiskAlertVO strategyRiskAlert(StrategySignal signal, Map<Long, String> holdingNameById) {
        List<String> reasons = readStringList(signal.getReasonJson());
        String content = !reasons.isEmpty() ? reasons.getFirst() : defaultText(signal.getActionText(), "策略信号触发风险预警");
        return new DashboardRiskAlertVO(
                "SIGNAL_" + signal.getId(),
                "STRATEGY",
                displaySignalType(signal.getSignalType()),
                signal.getRiskLevel(),
                displayRiskLevel(signal.getRiskLevel()) + " · " + displaySignalType(signal.getSignalType()),
                signal.getFundCode(),
                holdingNameById.getOrDefault(signal.getHoldingId(), signal.getFundCode()),
                content,
                signal.getSignalTime()
        );
    }

    private DashboardRiskAlertVO aiRiskAlert(AiAnalysisReport report, Map<Long, String> holdingNameById) {
        List<String> risks = readStringList(report.getRisksJson());
        String content = risks.isEmpty() ? defaultText(report.getFinalConclusion(), "AI 分析提示存在风险点") : risks.getFirst();
        return new DashboardRiskAlertVO(
                "AI_" + report.getId(),
                "AI",
                "AI 风险",
                report.getRiskLevel(),
                displayRiskLevel(report.getRiskLevel()) + " · AI 风险",
                report.getFundCode(),
                holdingNameById.getOrDefault(report.getHoldingId(), report.getFundCode()),
                content,
                report.getAnalysisTime()
        );
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String displayRiskLevel(String level) {
        return switch (level == null ? "" : level) {
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            case "LOW" -> "低";
            default -> "未知";
        };
    }

    private String displaySignalType(String type) {
        return switch (type == null ? "" : type) {
            case "TAKE_PROFIT" -> "止盈回撤";
            case "ADD_POSITION" -> "加仓信号";
            case "POSITION_RISK" -> "仓位风险";
            case "MARKET_RISK" -> "市场风险";
            case "CLASSIFICATION" -> "基金分类";
            case "WATCH" -> "观察信号";
            case "POSITION_MONITOR" -> "仓位监控";
            case "RISK_ALERT" -> "风险预警";
            default -> type;
        };
    }

    private FundHoldingVO toHoldingVO(FundHolding holding) {
        FundNavDaily officialNav = latestOfficialNav(holding.getFundCode());
        Map<String, FundNavDaily> officialNavByFund = officialNav == null ? Map.of() : Map.of(holding.getFundCode(), officialNav);
        return toHoldingVO(holding, tradingCalendarService.isIntradayEstimateDisplayWindow(now()), officialNavByFund);
    }

    private FundHoldingVO toHoldingVO(FundHolding holding, boolean intradayDisplayWindow, Map<String, FundNavDaily> latestOfficialNavByFund) {
        FundNavDaily officialNav = latestOfficialNavByFund.get(holding.getFundCode());
        BigDecimal estimateRate = currentEstimateGrowthRate(holding);
        boolean intradayAllowed = intradayDisplayWindow && !delayedOfficialNavFund(holding);
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
        BigDecimal holdingAmount = effectiveHoldingAmount(holding, officialNav);
        BigDecimal holdingProfit = effectiveHoldingProfit(holding, officialNav);
        BigDecimal accountTotal = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getAccountId, holding.getAccountId()))
                .stream()
                .map(accountHolding -> effectiveHoldingAmount(accountHolding, latestOfficialNavByFund.get(accountHolding.getFundCode())))
                .reduce(ZERO, BigDecimal::add);
        return new FundHoldingVO(
                holding.getId(),
                holding.getAccountId(),
                holding.getFundCode(),
                holding.getFundName(),
                holding.getFundType(),
                holding.getActiveFund() != null && holding.getActiveFund() == 1,
                holdingAmount,
                scale(holding.getHoldingShare()),
                scale(holding.getHoldingCost()),
                officialUpdated ? officialNav.getUnitNav() : holding.getCurrentEstimateNav(),
                officialUpdated ? officialNav.getUnitNav() : holding.getLatestOfficialNav(),
                holdingProfit,
                effectiveHoldingProfitRate(holding, officialNav),
                dailyProfit,
                ZERO,
                rate(holdingAmount, accountTotal),
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
        if (!tradingCalendarService.isIntradayEstimateDisplayWindow(now()) || delayedOfficialNavFund(holding)) {
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
        return nav.getNavDate() != null && officialNavEffectiveDate(holding, nav.getNavDate()).equals(today());
    }

    private LocalDate officialNavEffectiveDate(FundHolding holding, LocalDate navDate) {
        return OfficialNavTiming.effectiveDate(holding, navDate, tradingCalendarService);
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
        return OfficialNavTiming.isDelayedOfficialNavFund(holding);
    }

    private BigDecimal dashboardDailyProfit(List<FundHolding> holdings, boolean intradayDisplayWindow, Map<String, FundNavDaily> latestOfficialNavByFund) {
        return holdings.stream()
                .map(holding -> holdingDailyProfit(holding, intradayDisplayWindow, latestOfficialNavByFund.get(holding.getFundCode())))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal holdingDailyProfit(FundHolding holding, boolean intradayDisplayWindow, FundNavDaily officialNav) {
        boolean intradayAllowed = intradayDisplayWindow && !delayedOfficialNavFund(holding);
        boolean officialUpdated = officialNavUpdated(holding, officialNav);
        return officialUpdated || intradayAllowed ? scale(holding.getDailyProfit()) : ZERO;
    }

    private PortfolioSummaryVO dashboardSummary(PortfolioSummaryVO summary, List<FundHolding> holdings,
                                                Map<String, FundNavDaily> latestOfficialNavByFund,
                                                BigDecimal dailyProfit) {
        if (holdings.isEmpty()) {
            return dashboardSummary(summary, summary.totalAsset(), summary.currentProfit(),
                    summary.equityPositionRate(), summary.bondPositionRate(), dailyProfit);
        }
        BigDecimal totalAsset = holdings.stream()
                .map(holding -> effectiveHoldingAmount(holding, latestOfficialNavByFund.get(holding.getFundCode())))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal currentProfit = holdings.stream()
                .map(holding -> effectiveHoldingProfit(holding, latestOfficialNavByFund.get(holding.getFundCode())))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal equityAmount = holdings.stream()
                .filter(holding -> isEquityType(holding.getFundType()))
                .map(holding -> effectiveHoldingAmount(holding, latestOfficialNavByFund.get(holding.getFundCode())))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal bondAmount = holdings.stream()
                .filter(holding -> isBondType(holding.getFundType()))
                .map(holding -> effectiveHoldingAmount(holding, latestOfficialNavByFund.get(holding.getFundCode())))
                .reduce(ZERO, BigDecimal::add);
        return dashboardSummary(summary, totalAsset, currentProfit, rate(equityAmount, totalAsset), rate(bondAmount, totalAsset), dailyProfit);
    }

    private PortfolioSummaryVO dashboardSummary(PortfolioSummaryVO summary, BigDecimal totalAsset,
                                                BigDecimal currentProfit, BigDecimal equityPositionRate,
                                                BigDecimal bondPositionRate, BigDecimal dailyProfit) {
        return new PortfolioSummaryVO(
                scale(totalAsset),
                summary.totalInvestAmount(),
                scale(currentProfit),
                rate(currentProfit, summary.totalInvestAmount()),
                scale(dailyProfit),
                scale(equityPositionRate),
                scale(bondPositionRate),
                summary.cashPositionRate(),
                summary.holdingCount(),
                summary.accounts()
        );
    }

    private BigDecimal effectiveHoldingAmount(FundHolding holding, FundNavDaily officialNav) {
        if (officialNavUpdated(holding, officialNav)
                && officialNav.getUnitNav() != null
                && officialNav.getUnitNav().compareTo(BigDecimal.ZERO) > 0
                && holding.getHoldingShare() != null
                && holding.getHoldingShare().compareTo(BigDecimal.ZERO) > 0) {
            return scale(holding.getHoldingShare().multiply(officialNav.getUnitNav()));
        }
        return scale(holding.getHoldingAmount());
    }

    private BigDecimal effectiveHoldingProfit(FundHolding holding, FundNavDaily officialNav) {
        if (officialNavUpdated(holding, officialNav)) {
            return scale(effectiveHoldingAmount(holding, officialNav).subtract(scale(holding.getHoldingCost())));
        }
        return scale(holding.getHoldingProfit());
    }

    private BigDecimal effectiveHoldingProfitRate(FundHolding holding, FundNavDaily officialNav) {
        if (officialNavUpdated(holding, officialNav)) {
            return rate(effectiveHoldingProfit(holding, officialNav), holding.getHoldingCost());
        }
        return scale(holding.getHoldingProfitRate());
    }

    private boolean isEquityType(String fundType) {
        String value = fundType == null ? "" : fundType.toUpperCase();
        return value.contains("EQUITY") || value.contains("INDEX") || value.contains("ETF") || value.contains("MIXED") || value.contains("QDII");
    }

    private boolean isBondType(String fundType) {
        String value = fundType == null ? "" : fundType.toUpperCase();
        return value.contains("BOND") || value.contains("FIXED_INCOME");
    }

    private StrategySignalVO toSignalVO(StrategySignal signal, Map<Long, String> holdingNameById) {
        return new StrategySignalVO(
                signal.getId(),
                signal.getAccountId(),
                signal.getHoldingId(),
                signal.getFundCode(),
                holdingNameById.getOrDefault(signal.getHoldingId(), signal.getFundCode()),
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
                fundName(report.getHoldingId(), report.getFundCode()),
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

    private String fundName(Long holdingId, String fundCode) {
        FundHolding holding = holdingId == null ? null : fundHoldingMapper.selectById(holdingId);
        if (holding != null && holding.getFundName() != null && !holding.getFundName().isBlank()) {
            return holding.getFundName();
        }
        return fundCode;
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
