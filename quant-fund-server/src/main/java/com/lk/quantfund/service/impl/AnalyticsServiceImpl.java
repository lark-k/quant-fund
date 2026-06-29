package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.FundEstimateIntraday;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioIntradaySnapshot;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundEstimateIntradayMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioIntradaySnapshotMapper;
import com.lk.quantfund.scheduler.HoldingSnapshotBackfillService;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.AnalyticsService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.analytics.OfficialNavTiming;
import com.lk.quantfund.service.analytics.SnapshotProfitStatusResolver;
import com.lk.quantfund.service.analytics.SnapshotProfitStatusResolver.ProfitStatus;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import com.lk.quantfund.vo.analytics.FundProfitRankVO;
import com.lk.quantfund.vo.analytics.IndexCompareVO;
import com.lk.quantfund.vo.analytics.ProfitAnalysisVO;
import com.lk.quantfund.vo.analytics.ProfitCalendarDayVO;
import com.lk.quantfund.vo.analytics.ProfitCalendarVO;
import com.lk.quantfund.vo.analytics.ProfitIntradayTrendPointVO;
import com.lk.quantfund.vo.analytics.ProfitPeriodStatVO;
import com.lk.quantfund.vo.analytics.ProfitTrendPointVO;
import com.lk.quantfund.vo.market.MarketIndexIntradayPointVO;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import com.lk.quantfund.vo.market.MarketIndexVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");
    private static final String DEFAULT_INDEX_CODE = "000300";
    private static final Map<String, String> SUPPORTED_INDICES = Map.of(
            "000300", "沪深300",
            "000001", "上证指数",
            "399006", "创业板指"
    );

    private final HoldingSnapshotMapper holdingSnapshotMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final FundEstimateIntradayMapper fundEstimateIntradayMapper;
    private final FundNavDailyMapper fundNavDailyMapper;
    private final PortfolioAccountService portfolioAccountService;
    private final PortfolioIntradaySnapshotMapper portfolioIntradaySnapshotMapper;
    private final TradingCalendarService tradingCalendarService;
    private final HoldingSnapshotBackfillService holdingSnapshotBackfillService;
    private final MarketDataService marketDataService;
    private final SnapshotProfitStatusResolver snapshotProfitStatusResolver;
    private final FundValuationService fundValuationService;

    public AnalyticsServiceImpl(HoldingSnapshotMapper holdingSnapshotMapper,
                                FundHoldingMapper fundHoldingMapper,
                                FundEstimateIntradayMapper fundEstimateIntradayMapper,
                                FundNavDailyMapper fundNavDailyMapper,
                                PortfolioAccountService portfolioAccountService,
                                PortfolioIntradaySnapshotMapper portfolioIntradaySnapshotMapper,
                                TradingCalendarService tradingCalendarService,
                                HoldingSnapshotBackfillService holdingSnapshotBackfillService,
                                MarketDataService marketDataService,
                                SnapshotProfitStatusResolver snapshotProfitStatusResolver,
                                FundValuationService fundValuationService) {
        this.holdingSnapshotMapper = holdingSnapshotMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.fundEstimateIntradayMapper = fundEstimateIntradayMapper;
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.portfolioAccountService = portfolioAccountService;
        this.portfolioIntradaySnapshotMapper = portfolioIntradaySnapshotMapper;
        this.tradingCalendarService = tradingCalendarService;
        this.holdingSnapshotBackfillService = holdingSnapshotBackfillService;
        this.marketDataService = marketDataService;
        this.snapshotProfitStatusResolver = snapshotProfitStatusResolver;
        this.fundValuationService = fundValuationService;
    }

    public ProfitAnalysisVO profitAnalysis(LocalDate startDate, LocalDate endDate) {
        return profitAnalysis(startDate, endDate, null);
    }

    @Override
    public ProfitAnalysisVO profitAnalysis(LocalDate startDate, LocalDate endDate, String indexCode) {
        Long userId = UserContext.getUserId();
        String actualIndexCode = normalizeIndexCode(indexCode);
        holdingSnapshotBackfillService.ensureRecentSnapshots(userId);
        LocalDate today = today();
        LocalDate actualEnd = endDate == null ? today : endDate;
        LocalDate actualStart = startDate == null ? actualEnd.minusDays(29) : startDate;
        if (actualStart.isAfter(actualEnd)) {
            actualStart = actualEnd.minusDays(29);
        }
        List<HoldingSnapshot> selectedSnapshots = snapshots(userId, actualStart, actualEnd);
        PortfolioSummaryVO summary = portfolioAccountService.summary();
        List<FundHolding> currentHoldings = currentHoldings(userId);
        boolean todayTradingDay = tradingCalendarService.isTradingDay(today);
        boolean intradayDisplayWindow = tradingCalendarService.isIntradayEstimateDisplayWindow(now());
        BigDecimal todayProfit = todayTradingDay ? currentDailyProfit(currentHoldings, intradayDisplayWindow) : ZERO;
        boolean includeCurrentDay = todayTradingDay && (intradayDisplayWindow || todayProfit.compareTo(ZERO) != 0);
        boolean allOfficialNavUpdated = allOfficialNavUpdated(currentHoldings);
        List<ProfitTrendPointVO> trend = withIndexReturnRates(
                withCurrentDay(trend(selectedSnapshots, currentHoldings, actualStart, actualEnd), actualStart, actualEnd, today, summary.totalAsset(), todayProfit, includeCurrentDay, allOfficialNavUpdated),
                actualStart,
                actualEnd,
                actualIndexCode);
        BigDecimal selectedProfit = sumTrendProfit(trend);
        BigDecimal selectedAsset = lastTotalAsset(selectedSnapshots, summary.totalAsset());
        BigDecimal selectedRangeProfitRate = rate(selectedProfit, selectedAsset);
        IndexCompareVO indexCompare = indexCompare(selectedRangeProfitRate, actualIndexCode);
        BigDecimal weekProfit = periodProfit(userId, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today, todayProfit);
        BigDecimal monthProfit = periodProfit(userId, today.withDayOfMonth(1), today, todayProfit);
        BigDecimal yearProfit = periodProfit(userId, today.withDayOfYear(1), today, todayProfit);
        return new ProfitAnalysisVO(
                actualStart,
                actualEnd,
                todayProfit,
                weekProfit,
                monthProfit,
                yearProfit,
                scale(summary.currentProfit()),
                selectedProfit,
                selectedRangeProfitRate,
                List.of(
                        new ProfitPeriodStatVO("TODAY", todayProfit, rate(todayProfit, summary.totalAsset())),
                        new ProfitPeriodStatVO("THIS_WEEK", weekProfit, rate(weekProfit, summary.totalAsset())),
                        new ProfitPeriodStatVO("THIS_MONTH", monthProfit, rate(monthProfit, summary.totalAsset())),
                        new ProfitPeriodStatVO("THIS_YEAR", yearProfit, rate(yearProfit, summary.totalAsset())),
                        new ProfitPeriodStatVO("ALL", scale(summary.currentProfit()), scale(summary.currentProfitRate()))
                ),
                trend,
                profitTop5(userId),
                lossTop5(userId),
                indexCompare,
                indexCompare.statusText(),
                SystemConstants.DISCLAIMER
        );
    }

    @Override
    public List<ProfitIntradayTrendPointVO> intradayTrend(String indexCode) {
        Long userId = UserContext.getUserId();
        holdingSnapshotBackfillService.ensureRecentSnapshots(userId);
        LocalDate today = today();
        if (!tradingCalendarService.isTradingDay(today)) {
            return List.of();
        }
        String actualIndexCode = normalizeIndexCode(indexCode);
        List<FundHolding> holdings = currentHoldings(userId);
        if (holdings.isEmpty()) {
            return List.of();
        }
        LocalDateTime currentTime = now();
        List<MarketIndexIntradayPointVO> indexPoints = marketDataService.intradayIndex(actualIndexCode).stream()
                .filter(point -> isAShareIntradayMinute(point.time()))
                .toList();
        PortfolioSummaryVO summary = portfolioAccountService.summary();
        BigDecimal totalAsset = summary.totalAsset();
        BigDecimal currentDashboardDailyProfit = scale(summary.dailyProfit());
        BigDecimal currentDashboardReturn = rate(currentDashboardDailyProfit, totalAsset);
        List<PortfolioIntradaySnapshot> snapshots = portfolioIntradaySnapshotMapper.selectList(new LambdaQueryWrapper<PortfolioIntradaySnapshot>()
                .eq(PortfolioIntradaySnapshot::getUserId, userId)
                .eq(PortfolioIntradaySnapshot::getSnapshotDate, today)
                .orderByAsc(PortfolioIntradaySnapshot::getSnapshotTime))
                .stream()
                .filter(snapshot -> isAShareIntradayMinute(snapshot.getSnapshotTime()))
                .toList();
        Map<LocalDateTime, BigDecimal> indexReturnByTime = indexPoints.stream()
                .collect(Collectors.toMap(
                        point -> point.time().withSecond(0).withNano(0),
                        MarketIndexIntradayPointVO::returnRate,
                        (left, right) -> right,
                        LinkedHashMap::new));
        Map<LocalDateTime, PortfolioIntradaySnapshot> snapshotByTime = snapshots.stream()
                .collect(Collectors.toMap(
                        snapshot -> snapshot.getSnapshotTime().withSecond(0).withNano(0),
                        snapshot -> snapshot,
                        (left, right) -> right,
                        LinkedHashMap::new));
        LocalDateTime liveTrendTime = livePortfolioTrendTime(currentTime);
        if (liveTrendTime != null && today.equals(liveTrendTime.toLocalDate())) {
            PortfolioIntradaySnapshot liveSnapshot = new PortfolioIntradaySnapshot();
            liveSnapshot.setSnapshotTime(liveTrendTime);
            liveSnapshot.setDailyProfit(currentDashboardDailyProfit);
            liveSnapshot.setDailyProfitRate(currentDashboardReturn);
            snapshotByTime.put(liveTrendTime, liveSnapshot);
        }
        if (indexReturnByTime.isEmpty() && snapshotByTime.isEmpty()) {
            return List.of();
        }
        java.util.Set<LocalDateTime> allTimes = new java.util.LinkedHashSet<>();
        allTimes.addAll(indexReturnByTime.keySet());
        allTimes.addAll(snapshotByTime.keySet());
        PortfolioIntradaySnapshot latestSnapshot = null;
        List<ProfitIntradayTrendPointVO> points = new ArrayList<>();
        for (LocalDateTime time : allTimes.stream().sorted().toList()) {
            PortfolioIntradaySnapshot snapshot = snapshotByTime.get(time);
            if (snapshot != null) {
                latestSnapshot = snapshot;
            }
            BigDecimal portfolioReturn = latestSnapshot == null ? null : scale(latestSnapshot.getDailyProfitRate());
            BigDecimal dailyProfit = latestSnapshot == null ? null : scale(latestSnapshot.getDailyProfit());
            points.add(new ProfitIntradayTrendPointVO(
                    time,
                    portfolioReturn,
                    indexReturnByTime.get(time),
                    dailyProfit
            ));
        }
        return points;
    }

    private LocalDateTime livePortfolioTrendTime(LocalDateTime time) {
        if (time == null || !tradingCalendarService.isIntradayEstimateDisplayWindow(time)) {
            return null;
        }
        LocalTime localTime = time.toLocalTime();
        if (localTime.isBefore(LocalTime.of(9, 30))) {
            return null;
        }
        if (!localTime.isAfter(LocalTime.of(11, 30))) {
            return time.withSecond(0).withNano(0);
        }
        if (localTime.isBefore(LocalTime.of(13, 0))) {
            return time.with(LocalTime.of(11, 30)).withSecond(0).withNano(0);
        }
        if (!localTime.isAfter(LocalTime.of(15, 0))) {
            return time.withSecond(0).withNano(0);
        }
        return time.with(LocalTime.of(15, 0)).withSecond(0).withNano(0);
    }

    private boolean isAShareIntradayMinute(LocalDateTime time) {
        if (time == null) {
            return false;
        }
        int minutes = time.getHour() * 60 + time.getMinute();
        return (minutes >= 9 * 60 + 30 && minutes <= 11 * 60 + 30)
                || (minutes >= 13 * 60 && minutes <= 15 * 60);
    }

    @Override
    public ProfitCalendarVO profitCalendar(YearMonth month) {
        Long userId = UserContext.getUserId();
        holdingSnapshotBackfillService.ensureRecentSnapshots(userId);
        YearMonth actualMonth = month == null ? YearMonth.now() : month;
        LocalDate start = actualMonth.atDay(1);
        LocalDate end = actualMonth.atEndOfMonth();
        List<HoldingSnapshot> monthSnapshots = snapshots(userId, start, end);
        PortfolioSummaryVO summary = portfolioAccountService.summary();
        LocalDate today = today();
        boolean todayTradingDay = tradingCalendarService.isTradingDay(today);
        List<FundHolding> currentHoldings = currentHoldings(userId);
        boolean intradayDisplayWindow = tradingCalendarService.isIntradayEstimateDisplayWindow(now());
        BigDecimal todayProfit = todayTradingDay ? currentDailyProfit(currentHoldings, intradayDisplayWindow) : ZERO;
        boolean includeCurrentDay = todayTradingDay && (intradayDisplayWindow || todayProfit.compareTo(ZERO) != 0);
        boolean allOfficialNavUpdated = allOfficialNavUpdated(currentHoldings);
        List<ProfitTrendPointVO> trend = withCurrentDay(trend(monthSnapshots, currentHoldings, start, end), start, end, today, summary.totalAsset(), todayProfit, includeCurrentDay, allOfficialNavUpdated);
        BigDecimal monthlyProfit = trend.stream()
                .map(ProfitTrendPointVO::dailyProfit)
                .reduce(ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal latestAsset = trend.isEmpty() ? ZERO : trend.get(trend.size() - 1).totalAsset();
        Map<LocalDate, ProfitTrendPointVO> trendByDate = trend.stream()
                .collect(Collectors.toMap(ProfitTrendPointVO::date, point -> point, (left, right) -> right, LinkedHashMap::new));
        BigDecimal[] cumulative = new BigDecimal[]{ZERO};
        List<ProfitCalendarDayVO> days = start.datesUntil(end.plusDays(1))
                .map(date -> {
                    ProfitTrendPointVO point = trendByDate.get(date);
                    BigDecimal dailyProfit = point == null ? ZERO : point.dailyProfit();
                    BigDecimal dailyProfitRate = point == null ? ZERO : point.dailyProfitRate();
                    if (point != null) {
                        cumulative[0] = point.cumulativeProfit();
                    }
                    boolean tradingDay = tradingCalendarService.isTradingDay(date);
                    return new ProfitCalendarDayVO(
                            date,
                            dailyProfit,
                            dailyProfitRate,
                            cumulative[0],
                            tradingDay ? heatLevel(dailyProfit) : "NON_TRADING",
                            tradingDay,
                            tradingDay ? "TRADING_DAY" : "NON_TRADING_DAY",
                            tradingDay ? statusOf(point) : "NON_TRADING",
                            tradingDay ? statusTextOf(point) : "休市/非交易日"
                    );
                })
                .toList();
        return new ProfitCalendarVO(
                actualMonth.toString(),
                monthlyProfit,
                rate(monthlyProfit, latestAsset),
                days,
                profitTop5(userId),
                lossTop5(userId),
                SystemConstants.DISCLAIMER
        );
    }

    private List<HoldingSnapshot> snapshots(Long userId, LocalDate startDate, LocalDate endDate) {
        return holdingSnapshotMapper.selectList(new LambdaQueryWrapper<HoldingSnapshot>()
                .eq(HoldingSnapshot::getUserId, userId)
                .eq(HoldingSnapshot::getDeleted, 0)
                .ge(HoldingSnapshot::getSnapshotDate, startDate)
                .le(HoldingSnapshot::getSnapshotDate, endDate)
                .orderByAsc(HoldingSnapshot::getSnapshotDate));
    }

    private List<FundHolding> currentHoldings(Long userId) {
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId));
    }

    private BigDecimal currentDailyProfit(List<FundHolding> holdings, boolean intradayDisplayWindow) {
        Set<String> todayEstimateFundCodes = todayEstimateFundCodes(holdings, today());
        return sum(holdings.stream()
                .map(holding -> currentDailyProfit(holding, intradayDisplayWindow, todayEstimateFundCodes))
                .toList());
    }

    private BigDecimal currentDailyProfit(FundHolding holding, boolean intradayDisplayWindow, Set<String> todayEstimateFundCodes) {
        FundNavDaily officialNav = latestOfficialNav(holding.getFundCode());
        if (officialNavUpdated(holding, officialNav)) {
            return scale(holding.getDailyProfit());
        }
        if (intradayDisplayWindow && intradayDataFreshToday(holding, todayEstimateFundCodes)) {
            BigDecimal estimateRate = currentEstimateGrowthRate(holding, officialNav, intradayDisplayWindow, true);
            FundValuationResult valuation = fundValuationService.estimate(
                    holding.getFundCode(), holding.getFundName(), holding.getFundType(), estimateRate);
            return displayDailyProfit(holding, valuation.themeRate());
        }
        return ZERO;
    }

    private BigDecimal currentEstimateGrowthRate(FundHolding holding, FundNavDaily officialNav,
                                                 boolean intradayDisplayWindow, boolean intradayFresh) {
        if (officialNavUpdated(holding, officialNav) && officialNav.getDailyGrowthRate() != null) {
            return scale(officialNav.getDailyGrowthRate());
        }
        if (!intradayDisplayWindow || !intradayFresh) {
            return ZERO;
        }
        if (holding.getCurrentEstimateNav() == null || holding.getLatestOfficialNav() == null
                || holding.getLatestOfficialNav().compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return rate(holding.getCurrentEstimateNav().subtract(holding.getLatestOfficialNav()), holding.getLatestOfficialNav());
    }

    private BigDecimal displayDailyProfit(FundHolding holding, BigDecimal valuationRate) {
        BigDecimal storedDailyProfit = scale(holding.getDailyProfit());
        BigDecimal rate = scale(valuationRate);
        if (rate.compareTo(BigDecimal.ZERO) == 0
                || storedDailyProfit.compareTo(BigDecimal.ZERO) == 0
                || storedDailyProfit.signum() == rate.signum()) {
            return storedDailyProfit;
        }
        return amountChangeByRate(effectiveHoldingAmount(holding), rate);
    }

    private BigDecimal effectiveHoldingAmount(FundHolding holding) {
        return scale(holding.getHoldingAmount());
    }

    private BigDecimal amountChangeByRate(BigDecimal amount, BigDecimal rate) {
        if (rate == null) {
            return ZERO;
        }
        return scale(amount).multiply(rate).divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }

    private Set<String> todayEstimateFundCodes(List<FundHolding> holdings, LocalDate today) {
        Set<String> fundCodes = holdings.stream()
                .map(FundHolding::getFundCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        if (fundCodes.isEmpty()) {
            return Set.of();
        }
        return fundEstimateIntradayMapper.selectList(new LambdaQueryWrapper<FundEstimateIntraday>()
                        .in(FundEstimateIntraday::getFundCode, fundCodes)
                        .eq(FundEstimateIntraday::getEstimateDate, today))
                .stream()
                .map(FundEstimateIntraday::getFundCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
    }

    private boolean intradayDataFreshToday(FundHolding holding, Set<String> todayEstimateFundCodes) {
        if (todayEstimateFundCodes.contains(holding.getFundCode())) {
            return true;
        }
        if (holding.getUpdateTime() != null) {
            return today().equals(holding.getUpdateTime().toLocalDate());
        }
        if (holding.getCurrentEstimateNav() == null && holding.getLatestOfficialNav() == null) {
            return true;
        }
        return holding.getCurrentEstimateNav() != null
                && holding.getLatestOfficialNav() != null
                && holding.getLatestOfficialNav().compareTo(BigDecimal.ZERO) > 0
                && holding.getCurrentEstimateNav().compareTo(holding.getLatestOfficialNav()) != 0;
    }

    private BigDecimal periodProfit(Long userId, LocalDate startDate, LocalDate today, BigDecimal todayProfit) {
        BigDecimal historical = sumDailyProfit(snapshots(userId, startDate, today.minusDays(1)));
        return historical.add(todayProfit).setScale(4, RoundingMode.HALF_UP);
    }

    private List<ProfitTrendPointVO> trend(List<HoldingSnapshot> snapshots, List<FundHolding> holdings, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, List<HoldingSnapshot>> byDate = snapshots.stream()
                .filter(snapshot -> snapshot.getSnapshotDate() != null)
                .filter(snapshot -> tradingCalendarService.isTradingDay(snapshot.getSnapshotDate()))
                .collect(Collectors.groupingBy(HoldingSnapshot::getSnapshotDate, LinkedHashMap::new, Collectors.toList()));
        Map<LocalDate, ProfitStatus> statusByDate = snapshotProfitStatusResolver.resolve(snapshots, holdings, startDate, endDate);
        BigDecimal[] cumulative = new BigDecimal[]{ZERO};
        return byDate.entrySet().stream()
                .map(entry -> {
                    BigDecimal totalAsset = entry.getValue().stream()
                            .map(HoldingSnapshot::getTotalAsset)
                            .filter(value -> value != null)
                            .max(BigDecimal::compareTo)
                            .orElse(ZERO);
                    BigDecimal dailyProfit = sum(entry.getValue().stream().map(HoldingSnapshot::getDailyProfit).toList());
                    cumulative[0] = cumulative[0].add(dailyProfit).setScale(4, RoundingMode.HALF_UP);
                    ProfitStatus status = statusByDate.getOrDefault(entry.getKey(), snapshotProfitStatusResolver.snapshotSynced());
                    return new ProfitTrendPointVO(entry.getKey(), totalAsset, dailyProfit, cumulative[0], rate(dailyProfit, totalAsset), null, status.code(), status.text());
                })
                .toList();
    }

    private BigDecimal sumDailyProfit(List<HoldingSnapshot> snapshots) {
        return sum(snapshots.stream()
                .filter(snapshot -> snapshot.getSnapshotDate() != null)
                .filter(snapshot -> tradingCalendarService.isTradingDay(snapshot.getSnapshotDate()))
                .map(HoldingSnapshot::getDailyProfit)
                .toList());
    }

    private BigDecimal sumTrendProfit(List<ProfitTrendPointVO> trend) {
        return sum(trend.stream().map(ProfitTrendPointVO::dailyProfit).toList());
    }

    private List<ProfitTrendPointVO> withCurrentDay(List<ProfitTrendPointVO> trend, LocalDate startDate, LocalDate endDate,
                                                    LocalDate today, BigDecimal totalAsset, BigDecimal todayProfit,
                                                    boolean includeCurrentDayEstimate,
                                                    boolean allOfficialNavUpdated) {
        if (!includeCurrentDayEstimate || today.isBefore(startDate) || today.isAfter(endDate)) {
            return trend;
        }
        ProfitTrendPointVO existingToday = trend.stream()
                .filter(point -> today.equals(point.date()))
                .findFirst()
                .orElse(null);
        if (existingToday != null && "CONFIRMED".equals(existingToday.profitStatus()) && allOfficialNavUpdated) {
            return trend;
        }
        List<ProfitTrendPointVO> withoutToday = trend.stream()
                .filter(point -> !today.equals(point.date()))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        BigDecimal cumulativeBeforeToday = withoutToday.stream()
                .filter(point -> point.date().isBefore(today))
                .map(ProfitTrendPointVO::dailyProfit)
                .reduce(ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        withoutToday.add(new ProfitTrendPointVO(
                today,
                scale(totalAsset),
                scale(todayProfit),
                cumulativeBeforeToday.add(scale(todayProfit)).setScale(4, RoundingMode.HALF_UP),
                rate(todayProfit, totalAsset),
                null,
                "ESTIMATED",
                "盘中预估，待正式净值确认"
        ));
        withoutToday.sort(Comparator.comparing(ProfitTrendPointVO::date));
        return withoutToday;
    }

    private boolean allOfficialNavUpdated(List<FundHolding> holdings) {
        if (holdings.isEmpty()) {
            return false;
        }
        return holdings.stream()
                .allMatch(holding -> officialNavUpdated(holding, latestOfficialNav(holding.getFundCode())));
    }

    private List<ProfitTrendPointVO> withIndexReturnRates(List<ProfitTrendPointVO> trend, LocalDate startDate, LocalDate endDate, String indexCode) {
        if (trend.isEmpty()) {
            return trend;
        }
        List<MarketIndexDailyVO> history = marketDataService.historicalIndex(indexCode, startDate, endDate);
        if (history.size() < 2) {
            return trend;
        }
        Map<LocalDate, BigDecimal> closeByDate = history.stream()
                .collect(Collectors.toMap(MarketIndexDailyVO::tradeDate, MarketIndexDailyVO::closePrice, (left, right) -> right, LinkedHashMap::new));
        BigDecimal baseClose = history.getFirst().closePrice();
        if (baseClose == null || baseClose.compareTo(BigDecimal.ZERO) <= 0) {
            return trend;
        }
        return trend.stream()
                .map(point -> new ProfitTrendPointVO(
                        point.date(),
                        point.totalAsset(),
                        point.dailyProfit(),
                        point.cumulativeProfit(),
                        point.dailyProfitRate(),
                        indexReturnRate(closeByDate.get(point.date()), baseClose),
                        point.profitStatus(),
                        point.profitStatusText()
                ))
                .toList();
    }

    private BigDecimal indexReturnRate(BigDecimal closePrice, BigDecimal baseClose) {
        if (closePrice == null || baseClose == null || baseClose.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return closePrice.subtract(baseClose).multiply(HUNDRED).divide(baseClose, 4, RoundingMode.HALF_UP);
    }

    protected LocalDate today() {
        return LocalDate.now();
    }

    protected LocalDateTime now() {
        return LocalDateTime.now();
    }

    private String statusOf(ProfitTrendPointVO point) {
        return point == null ? "NONE" : point.profitStatus();
    }

    private String statusTextOf(ProfitTrendPointVO point) {
        return point == null ? "无收益记录" : point.profitStatusText();
    }

    private IndexCompareVO indexCompare(BigDecimal selectedRangeProfitRate, String indexCode) {
        String indexName = indexName(indexCode);
        List<MarketIndexVO> indices;
        try {
            indices = marketDataService.marketReadings();
        } catch (Exception exception) {
            indices = List.of();
        }
        MarketIndexVO selectedIndex = indices.stream()
                .filter(index -> indexCode.equals(index.code()) || indexName.equals(index.name()))
                .findFirst()
                .orElse(null);
        if (selectedIndex == null) {
            return new IndexCompareVO(
                    indexCode,
                    indexName,
                    ZERO,
                    scale(selectedRangeProfitRate),
                    ZERO,
                    null,
                    "EAST_MONEY",
                    indexName + "实时行情暂不可用，指数对比待数据源恢复",
                    false
            );
        }
        BigDecimal indexRate = scale(selectedIndex.changeRate());
        BigDecimal excessReturn = scale(selectedRangeProfitRate).subtract(indexRate).setScale(4, RoundingMode.HALF_UP);
        String direction = excessReturn.compareTo(BigDecimal.ZERO) >= 0 ? "跑赢" : "落后";
        return new IndexCompareVO(
                selectedIndex.code(),
                selectedIndex.name(),
                indexRate,
                scale(selectedRangeProfitRate),
                excessReturn,
                selectedIndex.updateTime(),
                selectedIndex.sourceName(),
                String.format("组合区间收益率 %s%%，%s实时涨跌幅 %s%%，%s %s%%",
                        scale(selectedRangeProfitRate).stripTrailingZeros().toPlainString(),
                        selectedIndex.name(),
                        indexRate.stripTrailingZeros().toPlainString(),
                        direction,
                        excessReturn.abs().stripTrailingZeros().toPlainString()),
                true
        );
    }

    private String normalizeIndexCode(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            return DEFAULT_INDEX_CODE;
        }
        String trimmed = indexCode.trim();
        return SUPPORTED_INDICES.containsKey(trimmed) ? trimmed : DEFAULT_INDEX_CODE;
    }

    private String indexName(String indexCode) {
        return SUPPORTED_INDICES.getOrDefault(indexCode, SUPPORTED_INDICES.get(DEFAULT_INDEX_CODE));
    }

    private BigDecimal lastTotalAsset(List<HoldingSnapshot> snapshots, BigDecimal fallback) {
        return snapshots.stream()
                .max(Comparator.comparing(HoldingSnapshot::getSnapshotDate))
                .map(HoldingSnapshot::getTotalAsset)
                .orElse(scale(fallback));
    }

    private List<FundProfitRankVO> profitTop5(Long userId) {
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getUserId, userId)
                        .gt(FundHolding::getHoldingProfit, BigDecimal.ZERO)
                        .orderByDesc(FundHolding::getHoldingProfit)
                        .last("LIMIT 5"))
                .stream()
                .map(this::toRankVO)
                .toList();
    }

    private List<FundProfitRankVO> lossTop5(Long userId) {
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getUserId, userId)
                        .lt(FundHolding::getHoldingProfit, BigDecimal.ZERO)
                        .orderByAsc(FundHolding::getHoldingProfit)
                        .last("LIMIT 5"))
                .stream()
                .map(this::toRankVO)
                .toList();
    }

    private FundProfitRankVO toRankVO(FundHolding holding) {
        return new FundProfitRankVO(
                holding.getId(),
                holding.getFundCode(),
                holding.getFundName(),
                scale(holding.getHoldingAmount()),
                scale(holding.getHoldingProfit()),
                scale(holding.getHoldingProfitRate())
        );
    }

    private String heatLevel(BigDecimal dailyProfit) {
        BigDecimal value = scale(dailyProfit);
        if (value.compareTo(new BigDecimal("1000.0000")) >= 0) {
            return "STRONG_PROFIT";
        }
        if (value.compareTo(BigDecimal.ZERO) > 0) {
            return "PROFIT";
        }
        if (value.compareTo(new BigDecimal("-1000.0000")) <= 0) {
            return "STRONG_LOSS";
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return "LOSS";
        }
        return "FLAT";
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .filter(value -> value != null)
                .map(this::scale)
                .reduce(ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal profit, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return scale(profit).multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
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

    private boolean delayedOfficialNavFund(FundHolding holding) {
        return OfficialNavTiming.isDelayedOfficialNavFund(holding);
    }
}
