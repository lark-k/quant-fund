package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.scheduler.HoldingSnapshotBackfillService;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.AnalyticsService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.vo.analytics.FundProfitRankVO;
import com.lk.quantfund.vo.analytics.ProfitAnalysisVO;
import com.lk.quantfund.vo.analytics.ProfitCalendarDayVO;
import com.lk.quantfund.vo.analytics.ProfitCalendarVO;
import com.lk.quantfund.vo.analytics.ProfitPeriodStatVO;
import com.lk.quantfund.vo.analytics.ProfitTrendPointVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private final HoldingSnapshotMapper holdingSnapshotMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountService portfolioAccountService;
    private final TradingCalendarService tradingCalendarService;
    private final HoldingSnapshotBackfillService holdingSnapshotBackfillService;

    public AnalyticsServiceImpl(HoldingSnapshotMapper holdingSnapshotMapper,
                                FundHoldingMapper fundHoldingMapper,
                                PortfolioAccountService portfolioAccountService,
                                TradingCalendarService tradingCalendarService,
                                HoldingSnapshotBackfillService holdingSnapshotBackfillService) {
        this.holdingSnapshotMapper = holdingSnapshotMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountService = portfolioAccountService;
        this.tradingCalendarService = tradingCalendarService;
        this.holdingSnapshotBackfillService = holdingSnapshotBackfillService;
    }

    @Override
    public ProfitAnalysisVO profitAnalysis(LocalDate startDate, LocalDate endDate) {
        Long userId = UserContext.getUserId();
        holdingSnapshotBackfillService.ensureRecentSnapshots(userId);
        LocalDate today = LocalDate.now();
        LocalDate actualEnd = endDate == null ? today : endDate;
        LocalDate actualStart = startDate == null ? actualEnd.minusDays(29) : startDate;
        if (actualStart.isAfter(actualEnd)) {
            actualStart = actualEnd.minusDays(29);
        }
        List<HoldingSnapshot> selectedSnapshots = snapshots(userId, actualStart, actualEnd);
        PortfolioSummaryVO summary = portfolioAccountService.summary();
        List<FundHolding> currentHoldings = currentHoldings(userId);
        BigDecimal todayProfit = currentDailyProfit(currentHoldings);
        List<ProfitTrendPointVO> trend = withCurrentDay(trend(selectedSnapshots), actualStart, actualEnd, today, summary.totalAsset(), todayProfit);
        BigDecimal selectedProfit = sumTrendProfit(trend);
        BigDecimal selectedAsset = lastTotalAsset(selectedSnapshots, summary.totalAsset());
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
                rate(selectedProfit, selectedAsset),
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
                "Index comparison, profit-user percentile and drawdown-index stats will be enabled after market index and multi-user datasets are connected.",
                SystemConstants.DISCLAIMER
        );
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
        BigDecimal todayProfit = currentDailyProfit(currentHoldings(userId));
        List<ProfitTrendPointVO> trend = withCurrentDay(trend(monthSnapshots), start, end, LocalDate.now(), summary.totalAsset(), todayProfit);
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
                            tradingDay ? "TRADING_DAY" : "NON_TRADING_DAY"
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
                .ge(HoldingSnapshot::getSnapshotDate, startDate)
                .le(HoldingSnapshot::getSnapshotDate, endDate)
                .orderByAsc(HoldingSnapshot::getSnapshotDate));
    }

    private List<FundHolding> currentHoldings(Long userId) {
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId));
    }

    private BigDecimal currentDailyProfit(List<FundHolding> holdings) {
        return sum(holdings.stream()
                .map(holding -> delayedOfficialNavFund(holding) ? ZERO : holding.getDailyProfit())
                .toList());
    }

    private BigDecimal periodProfit(Long userId, LocalDate startDate, LocalDate today, BigDecimal todayProfit) {
        BigDecimal historical = sumDailyProfit(snapshots(userId, startDate, today.minusDays(1)));
        return historical.add(todayProfit).setScale(4, RoundingMode.HALF_UP);
    }

    private List<ProfitTrendPointVO> trend(List<HoldingSnapshot> snapshots) {
        Map<LocalDate, List<HoldingSnapshot>> byDate = snapshots.stream()
                .collect(Collectors.groupingBy(HoldingSnapshot::getSnapshotDate, LinkedHashMap::new, Collectors.toList()));
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
                    return new ProfitTrendPointVO(entry.getKey(), totalAsset, dailyProfit, cumulative[0], rate(dailyProfit, totalAsset));
                })
                .toList();
    }

    private BigDecimal sumDailyProfit(List<HoldingSnapshot> snapshots) {
        return sum(snapshots.stream().map(HoldingSnapshot::getDailyProfit).toList());
    }

    private BigDecimal sumTrendProfit(List<ProfitTrendPointVO> trend) {
        return sum(trend.stream().map(ProfitTrendPointVO::dailyProfit).toList());
    }

    private List<ProfitTrendPointVO> withCurrentDay(List<ProfitTrendPointVO> trend, LocalDate startDate, LocalDate endDate,
                                                    LocalDate today, BigDecimal totalAsset, BigDecimal todayProfit) {
        if (today.isBefore(startDate) || today.isAfter(endDate)) {
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
                rate(todayProfit, totalAsset)
        ));
        withoutToday.sort(Comparator.comparing(ProfitTrendPointVO::date));
        return withoutToday;
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

    private boolean delayedOfficialNavFund(FundHolding holding) {
        String name = holding.getFundName() == null ? "" : holding.getFundName().toUpperCase();
        String type = holding.getFundType() == null ? "" : holding.getFundType().toUpperCase();
        return type.contains("QDII")
                || name.contains("QDII")
                || name.contains("NASDAQ")
                || name.contains("S&P")
                || name.contains("USD");
    }
}
