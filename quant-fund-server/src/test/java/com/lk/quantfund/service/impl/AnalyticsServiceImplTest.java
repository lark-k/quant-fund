package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundEstimateIntraday;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioIntradaySnapshot;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundEstimateIntradayMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioIntradaySnapshotMapper;
import com.lk.quantfund.scheduler.HoldingSnapshotBackfillService;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.analytics.SnapshotProfitStatusResolver;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import com.lk.quantfund.vo.market.MarketIndexIntradayPointVO;
import com.lk.quantfund.vo.market.MarketIndexVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class AnalyticsServiceImplTest {

    private final HoldingSnapshotMapper holdingSnapshotMapper = mock(HoldingSnapshotMapper.class);
    private final FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
    private final FundEstimateIntradayMapper fundEstimateIntradayMapper = mock(FundEstimateIntradayMapper.class);
    private final FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
    private final PortfolioIntradaySnapshotMapper portfolioIntradaySnapshotMapper = mock(PortfolioIntradaySnapshotMapper.class);
    private final SnapshotProfitStatusResolver snapshotProfitStatusResolver = new SnapshotProfitStatusResolver(fundNavDailyMapper);
    private final PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
    private final TradingCalendarService tradingCalendarService = new TradingCalendarService(new QuantFundProperties());
    private final HoldingSnapshotBackfillService holdingSnapshotBackfillService = mock(HoldingSnapshotBackfillService.class);
    private final MarketDataService marketDataService = mock(MarketDataService.class);
    private final FundValuationService fundValuationService = mock(FundValuationService.class);

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(FundHolding.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FundHolding.class);
        }
        if (TableInfoHelper.getTableInfo(HoldingSnapshot.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), HoldingSnapshot.class);
        }
        when(marketDataService.historicalIndex(Mockito.anyString(), Mockito.any(LocalDate.class), Mockito.any(LocalDate.class)))
                .thenReturn(List.of());
        when(marketDataService.intradayIndex(Mockito.anyString())).thenReturn(List.of());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        LocalDate estimateDate = LocalDate.of(2026, 6, 25);
        when(fundEstimateIntradayMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                estimate("510300", estimateDate.atTime(10, 0)),
                estimate("510300", estimateDate.atTime(11, 30)),
                estimate("510300", estimateDate.atTime(14, 30)),
                estimate("510300", estimateDate.atTime(15, 0)),
                estimate("013403", estimateDate.atTime(10, 0))
        ));
        when(fundValuationService.estimate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> new FundValuationResult("TEST", invocation.getArgument(3), "TEST", "TEST", "TRADING"));
    }

    @Test
    void profitAnalysisShouldIncludeCurrentEstimateOnTradingDay() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25));
        FundHolding holding = holding("100.0000");
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(holding), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(marketDataService.marketReadings()).thenReturn(List.of(hs300("1.2300")));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var analysis = service.profitAnalysis(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

            assertThat(analysis.todayProfit()).isEqualByComparingTo("100.0000");
            assertThat(analysis.trend()).hasSize(1);
            assertThat(analysis.trend().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 25));
            assertThat(analysis.trend().get(0).dailyProfit()).isEqualByComparingTo("100.0000");
            assertThat(analysis.trend().get(0).profitStatus()).isEqualTo("ESTIMATED");
            assertThat(analysis.trend().get(0).profitStatusText()).contains("盘中预估");
            assertThat(analysis.indexCompare().available()).isTrue();
            assertThat(analysis.indexCompareStatus()).contains("沪深300实时涨跌幅");
        }
    }

    @Test
    void profitAnalysisTotalProfitShouldFollowDashboardDisplayProfit() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 7, 1), LocalDateTime.of(2026, 7, 1, 3, 33));
        FundHolding holding = holding("86.5471");
        holding.setHoldingAmount(new BigDecimal("4526.2352"));
        holding.setHoldingCost(new BigDecimal("4036.0200"));
        holding.setHoldingProfit(new BigDecimal("490.2152"));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(holding), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(new PortfolioSummaryVO(
                new BigDecimal("4526.2352"),
                new BigDecimal("4526.2352"),
                BigDecimal.ZERO,
                new BigDecimal("4036.0200"),
                new BigDecimal("576.7623"),
                new BigDecimal("14.2904"),
                new BigDecimal("86.5471"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                List.of()
        ));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var analysis = service.profitAnalysis(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

            assertThat(analysis.totalProfit()).isEqualByComparingTo("490.2152");
            assertThat(analysis.periodStats()).filteredOn(period -> period.period().equals("ALL"))
                    .singleElement()
                    .satisfies(period -> {
                        assertThat(period.profit()).isEqualByComparingTo("490.2152");
                        assertThat(period.profitRate()).isEqualByComparingTo("12.1460");
                    });
        }
    }

    @Test
    void profitCalendarShouldNotCountCurrentEstimateOnNonTradingDay() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 27));
        FundHolding holding = holding("100.0000");
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(holding), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var calendar = service.profitCalendar(YearMonth.of(2026, 6));

            assertThat(calendar.monthlyProfit()).isEqualByComparingTo("0.0000");
            assertThat(calendar.days()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 6, 27)))
                    .singleElement()
                    .satisfies(day -> {
                        assertThat(day.tradingDay()).isFalse();
                        assertThat(day.dailyProfit()).isEqualByComparingTo("0.0000");
                        assertThat(day.heatLevel()).isEqualTo("NON_TRADING");
                    });
        }
    }

    @Test
    void profitCalendarShouldKeepCurrentEstimateDuringLunchBreak() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25), LocalDateTime.of(2026, 6, 25, 11, 31));
        FundHolding holding = holding("100.0000");
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(holding), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var calendar = service.profitCalendar(YearMonth.of(2026, 6));

            assertThat(calendar.days()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 6, 25)))
                    .singleElement()
                    .satisfies(day -> {
                        assertThat(day.dailyProfit()).isEqualByComparingTo("100.0000");
                        assertThat(day.profitStatus()).isEqualTo("ESTIMATED");
                    });
        }
    }

    @Test
    void profitAnalysisTodayShouldUseDashboardDisplayDailyProfitLogic() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25), LocalDateTime.of(2026, 6, 25, 14, 30));
        FundHolding holding = holding("16.0000");
        holding.setCurrentEstimateNav(new BigDecimal("0.9800"));
        holding.setLatestOfficialNav(new BigDecimal("1.0000"));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(holding), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(marketDataService.marketReadings()).thenReturn(List.of(hs300("1.0000")));
        when(fundValuationService.estimate(Mockito.eq("510300"), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new FundValuationResult("沪深300", new BigDecimal("-2.0000"), "TEST", "TEST", "TRADING"));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var analysis = service.profitAnalysis(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 25));

            assertThat(analysis.todayProfit()).isEqualByComparingTo("-200.0000");
            assertThat(analysis.trend()).singleElement()
                    .satisfies(point -> assertThat(point.dailyProfit()).isEqualByComparingTo("-200.0000"));
        }
    }

    @Test
    void profitCalendarTodayShouldUseDashboardDisplayDailyProfitLogic() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25), LocalDateTime.of(2026, 6, 25, 14, 30));
        FundHolding holding = holding("16.0000");
        holding.setCurrentEstimateNav(new BigDecimal("0.9800"));
        holding.setLatestOfficialNav(new BigDecimal("1.0000"));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(holding), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(fundValuationService.estimate(Mockito.eq("510300"), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new FundValuationResult("沪深300", new BigDecimal("-2.0000"), "TEST", "TEST", "TRADING"));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var calendar = service.profitCalendar(YearMonth.of(2026, 6));

            assertThat(calendar.days()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 6, 25)))
                    .singleElement()
                    .satisfies(day -> {
                        assertThat(day.dailyProfit()).isEqualByComparingTo("-200.0000");
                        assertThat(day.profitStatus()).isEqualTo("ESTIMATED");
                    });
        }
    }

    @Test
    void profitCalendarShouldIncludeDelayedOfficialNavWhenEffectiveToday() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25));
        FundHolding normalHolding = holding("38.3100");
        FundHolding qdiiHolding = holding("-11.5700");
        qdiiHolding.setId(11L);
        qdiiHolding.setFundCode("013403");
        qdiiHolding.setFundName("QDII Growth");
        qdiiHolding.setFundType("QDII");
        FundNavDaily qdiiNav = nav("013403", LocalDate.of(2026, 6, 24));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(normalHolding, qdiiHolding), List.of(), List.of());
        when(fundNavDailyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(qdiiNav);
        when(portfolioAccountService.summary()).thenReturn(summary());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var calendar = service.profitCalendar(YearMonth.of(2026, 6));

            assertThat(calendar.days()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 6, 25)))
                    .singleElement()
                    .satisfies(day -> assertThat(day.dailyProfit()).isEqualByComparingTo("26.7400"));
        }
    }

    @Test
    void profitCalendarTodayShouldFollowCurrentHoldingsUntilAllOfficialNavUpdated() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25), LocalDateTime.of(2026, 6, 25, 19, 59));
        FundHolding holding = holding("87.0800");
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                snapshot(LocalDate.of(2026, 6, 25), "104.0000")
        ));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(holding), List.of(), List.of());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav("510300", LocalDate.of(2026, 6, 25))
        ));
        when(fundNavDailyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn((FundNavDaily) null);
        when(portfolioAccountService.summary()).thenReturn(summary());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var calendar = service.profitCalendar(YearMonth.of(2026, 6));

            assertThat(calendar.days()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 6, 25)))
                    .singleElement()
                    .satisfies(day -> {
                        assertThat(day.dailyProfit()).isEqualByComparingTo("87.0800");
                        assertThat(day.profitStatus()).isEqualTo("ESTIMATED");
                    });
        }
    }

    @Test
    void profitAnalysisShouldExplainUnavailableIndexComparison() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding("0.0000")), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav("510300", LocalDate.of(2026, 6, 24)),
                nav("510300", LocalDate.of(2026, 6, 25))
        ));
        when(marketDataService.marketReadings()).thenReturn(List.of());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var analysis = service.profitAnalysis(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

            assertThat(analysis.indexCompare().available()).isFalse();
            assertThat(analysis.indexCompareStatus()).isEqualTo("沪深300实时行情暂不可用，指数对比待数据源恢复");
        }
    }

    @Test
    void profitAnalysisShouldAttachHistoricalIndexReturnRate() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                snapshot(LocalDate.of(2026, 6, 24), "80.0000"),
                snapshot(LocalDate.of(2026, 6, 25), "120.0000")
        ));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding("0.0000")), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(marketDataService.marketReadings()).thenReturn(List.of(hs300("1.0000")));
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav("510300", LocalDate.of(2026, 6, 24)),
                nav("510300", LocalDate.of(2026, 6, 25))
        ));
        when(marketDataService.historicalIndex("000300", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(List.of(
                        indexPoint(LocalDate.of(2026, 6, 24), "4000.0000"),
                        indexPoint(LocalDate.of(2026, 6, 25), "4040.0000")
                ));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var analysis = service.profitAnalysis(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

            assertThat(analysis.trend()).hasSize(2);
            assertThat(analysis.trend().get(0).indexReturnRate()).isEqualByComparingTo("0.0000");
            assertThat(analysis.trend().get(1).indexReturnRate()).isEqualByComparingTo("1.0000");
            assertThat(analysis.trend().get(0).profitStatus()).isEqualTo("CONFIRMED");
            assertThat(analysis.trend().get(0).profitStatusText()).contains("正式净值");
        }
    }

    @Test
    void profitAnalysisShouldMarkSnapshotSyncedWhenOfficialNavEvidenceMissing() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                snapshot(LocalDate.of(2026, 6, 24), "80.0000")
        ));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding("0.0000")), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(marketDataService.marketReadings()).thenReturn(List.of(hs300("1.0000")));
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var analysis = service.profitAnalysis(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

            assertThat(analysis.trend()).hasSize(2);
            assertThat(analysis.trend().get(0).profitStatus()).isEqualTo("SNAPSHOT_SYNCED");
            assertThat(analysis.trend().get(0).profitStatusText()).contains("待确认");
            assertThat(analysis.trend().get(1).profitStatus()).isEqualTo("ESTIMATED");
        }
    }


    @Test
    void profitCalendarShouldIgnoreNonTradingSnapshotsInMonthlyProfit() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 27));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                snapshot(LocalDate.of(2026, 6, 26), "80.0000"),
                snapshot(LocalDate.of(2026, 6, 27), "100.0000")
        ));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var calendar = service.profitCalendar(YearMonth.of(2026, 6));

            assertThat(calendar.monthlyProfit()).isEqualByComparingTo("80.0000");
            assertThat(calendar.days()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 6, 27)))
                    .singleElement()
                    .satisfies(day -> {
                        assertThat(day.tradingDay()).isFalse();
                        assertThat(day.dailyProfit()).isEqualByComparingTo("0.0000");
                    });
        }
    }

    @Test
    void profitCalendarSnapshotQueryShouldExcludeDeletedSnapshots() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 27));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<HoldingSnapshot> wrapper = invocation.getArgument(0);
            assertThat(wrapper.getSqlSegment()).contains("deleted");
            return List.of(snapshot(LocalDate.of(2026, 6, 26), "-124.5900"));
        });
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(), List.of(), List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var calendar = service.profitCalendar(YearMonth.of(2026, 6));

            assertThat(calendar.days()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 6, 26)))
                    .singleElement()
                    .satisfies(day -> assertThat(day.dailyProfit()).isEqualByComparingTo("-124.5900"));
        }
    }

    @Test
    void profitAnalysisRankingsShouldQueryOnlyPositiveAndNegativeHoldings() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25));
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(marketDataService.marketReadings()).thenReturn(List.of(hs300("1.0000")));
        AtomicInteger callIndex = new AtomicInteger();
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<FundHolding> wrapper = invocation.getArgument(0);
            String sqlSegment = wrapper.getSqlSegment();
            int index = callIndex.getAndIncrement();
            if (index == 0) {
                assertThat(sqlSegment).doesNotContain("holding_profit >").doesNotContain("holding_profit <");
                return List.of();
            }
            if (index == 1) {
                assertThat(sqlSegment).contains("holding_profit").contains(">");
                return List.of(rankHolding(11L, "盈利基金", "120.0000"));
            }
            assertThat(sqlSegment).contains("holding_profit").contains("<");
            return List.of(rankHolding(12L, "亏损基金", "-80.0000"));
        });

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var analysis = service.profitAnalysis(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

            assertThat(analysis.profitTop5()).singleElement()
                    .satisfies(rank -> assertThat(rank.holdingProfit()).isPositive());
            assertThat(analysis.lossTop5()).singleElement()
                    .satisfies(rank -> assertThat(rank.holdingProfit()).isNegative());
            assertThat(callIndex).hasValue(3);
        }
    }

    @Test
    void intradayTrendShouldUseDashboardSummaryAsLatestPortfolioPoint() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25), LocalDateTime.of(2026, 6, 25, 10, 5));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding("0.0000")));
        when(portfolioAccountService.summary()).thenReturn(summary("88.0000"));
        when(portfolioIntradaySnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                portfolioSnapshot(LocalDateTime.of(2026, 6, 25, 10, 3), "30.0000", "0.3000")
        ));
        when(marketDataService.intradayIndex("000300")).thenReturn(List.of(
                indexIntraday(LocalDateTime.of(2026, 6, 25, 10, 0), "0.1000"),
                indexIntraday(LocalDateTime.of(2026, 6, 25, 10, 5), "0.2000")
        ));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var points = service.intradayTrend("000300");

            assertThat(points).hasSize(3);
            assertThat(points.getLast().time()).isEqualTo(LocalDateTime.of(2026, 6, 25, 10, 5));
            assertThat(points.getLast().dailyProfit()).isEqualByComparingTo("88.0000");
            assertThat(points.getLast().portfolioReturn()).isEqualByComparingTo("0.8800");
            assertThat(points.getLast().indexReturn()).isEqualByComparingTo("0.2000");
        }
    }

    @Test
    void intradayTrendShouldClampLunchBreakLivePointToMorningClose() {
        AnalyticsServiceImpl service = service(LocalDate.of(2026, 6, 25), LocalDateTime.of(2026, 6, 25, 11, 45));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding("0.0000")));
        when(portfolioAccountService.summary()).thenReturn(summary("77.0000"));
        when(portfolioIntradaySnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var points = service.intradayTrend("000300");

            assertThat(points).singleElement()
                    .satisfies(point -> {
                        assertThat(point.time()).isEqualTo(LocalDateTime.of(2026, 6, 25, 11, 30));
                        assertThat(point.dailyProfit()).isEqualByComparingTo("77.0000");
                        assertThat(point.portfolioReturn()).isEqualByComparingTo("0.7700");
                    });
        }
    }

    private AnalyticsServiceImpl service(LocalDate today) {
        return service(today, LocalDateTime.of(today.getYear(), today.getMonth(), today.getDayOfMonth(), 10, 0));
    }

    private AnalyticsServiceImpl service(LocalDate today, LocalDateTime now) {
        return new AnalyticsServiceImpl(
                holdingSnapshotMapper,
                fundHoldingMapper,
                fundEstimateIntradayMapper,
                fundNavDailyMapper,
                portfolioAccountService,
                portfolioIntradaySnapshotMapper,
                tradingCalendarService,
                holdingSnapshotBackfillService,
                marketDataService,
                snapshotProfitStatusResolver,
                fundValuationService
        ) {
            @Override
            protected LocalDate today() {
                return today;
            }

            @Override
            protected LocalDateTime now() {
                return now;
            }
        };
    }

    private FundHolding holding(String dailyProfit) {
        FundHolding holding = new FundHolding();
        holding.setId(10L);
        holding.setUserId(1L);
        holding.setFundCode("510300");
        holding.setFundName("沪深300ETF");
        holding.setFundType("INDEX");
        holding.setHoldingAmount(new BigDecimal("10000.0000"));
        holding.setHoldingCost(new BigDecimal("9500.0000"));
        holding.setHoldingProfit(new BigDecimal("500.0000"));
        holding.setHoldingProfitRate(new BigDecimal("5.0000"));
        holding.setDailyProfit(new BigDecimal(dailyProfit));
        return holding;
    }

    private FundHolding rankHolding(Long id, String fundName, String holdingProfit) {
        FundHolding holding = new FundHolding();
        holding.setId(id);
        holding.setUserId(1L);
        holding.setFundCode("F" + id);
        holding.setFundName(fundName);
        holding.setHoldingAmount(new BigDecimal("1000.0000"));
        holding.setHoldingProfit(new BigDecimal(holdingProfit));
        holding.setHoldingProfitRate(new BigDecimal(holdingProfit).divide(new BigDecimal("1000.0000")).multiply(new BigDecimal("100.0000")));
        return holding;
    }

    private HoldingSnapshot snapshot(LocalDate date, String dailyProfit) {
        HoldingSnapshot snapshot = new HoldingSnapshot();
        snapshot.setUserId(1L);
        snapshot.setHoldingId(10L);
        snapshot.setSnapshotDate(date);
        snapshot.setTotalAsset(new BigDecimal("10000.0000"));
        snapshot.setDailyProfit(new BigDecimal(dailyProfit));
        return snapshot;
    }

    private PortfolioIntradaySnapshot portfolioSnapshot(LocalDateTime time, String dailyProfit, String dailyProfitRate) {
        PortfolioIntradaySnapshot snapshot = new PortfolioIntradaySnapshot();
        snapshot.setUserId(1L);
        snapshot.setSnapshotDate(time.toLocalDate());
        snapshot.setSnapshotTime(time);
        snapshot.setTotalAsset(new BigDecimal("10000.0000"));
        snapshot.setDailyProfit(new BigDecimal(dailyProfit));
        snapshot.setDailyProfitRate(new BigDecimal(dailyProfitRate));
        return snapshot;
    }

    private FundNavDaily nav(String fundCode, LocalDate navDate) {
        FundNavDaily nav = new FundNavDaily();
        nav.setFundCode(fundCode);
        nav.setNavDate(navDate);
        nav.setUnitNav(BigDecimal.ONE);
        nav.setSourceName("EAST_MONEY");
        return nav;
    }

    private FundEstimateIntraday estimate(String fundCode, LocalDateTime estimateTime) {
        FundEstimateIntraday estimate = new FundEstimateIntraday();
        estimate.setFundCode(fundCode);
        estimate.setEstimateDate(estimateTime.toLocalDate());
        estimate.setEstimateTime(estimateTime);
        estimate.setEstimateGrowthRate(BigDecimal.ONE);
        estimate.setDelayed(0);
        return estimate;
    }

    private MarketIndexVO hs300(String changeRate) {
        return new MarketIndexVO(
                "000300",
                "沪深300",
                new BigDecimal("4100.0000"),
                new BigDecimal("50.0000"),
                new BigDecimal(changeRate),
                new BigDecimal("100000000.0000"),
                LocalDateTime.of(2026, 6, 25, 15, 0),
                "EAST_MONEY"
        );
    }

    private MarketIndexDailyVO indexPoint(LocalDate date, String closePrice) {
        return new MarketIndexDailyVO(
                "000300",
                "沪深300",
                date,
                new BigDecimal(closePrice),
                BigDecimal.ZERO,
                "EAST_MONEY"
        );
    }

    private MarketIndexIntradayPointVO indexIntraday(LocalDateTime time, String returnRate) {
        return new MarketIndexIntradayPointVO(
                "000300",
                "娌繁300",
                time,
                new BigDecimal("4000.0000"),
                new BigDecimal(returnRate),
                "EAST_MONEY"
        );
    }

    private PortfolioSummaryVO summary() {
        return summary("0.0000");
    }

    private PortfolioSummaryVO summary(String dailyProfit) {
        return new PortfolioSummaryVO(
                new BigDecimal("10000.0000"),
                new BigDecimal("10000.0000"),
                BigDecimal.ZERO,
                new BigDecimal("9500.0000"),
                new BigDecimal("500.0000"),
                new BigDecimal("5.0000"),
                new BigDecimal(dailyProfit),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                List.of()
        );
    }
}
