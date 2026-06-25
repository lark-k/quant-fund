package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.mapper.AiAnalysisReportMapper;
import com.lk.quantfund.mapper.FundEstimateIntradayMapper;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.scheduler.HoldingSnapshotBackfillService;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.analytics.SnapshotProfitStatusResolver;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class DashboardServiceImplMarketStatusTest {

    @Test
    void marketStatusShouldReturnUnifiedMarketSessions() {
        DashboardServiceImpl service = new DashboardServiceImpl(
                mock(PortfolioAccountService.class),
                mock(FundHoldingMapper.class),
                mock(StrategySignalMapper.class),
                mock(AiAnalysisReportMapper.class),
                mock(HoldingSnapshotMapper.class),
                mock(FundEstimateIntradayMapper.class),
                mock(FundNavDailyMapper.class),
                new ObjectMapper(),
                mock(FundValuationService.class),
                new TradingCalendarService(new QuantFundProperties()),
                mock(HoldingSnapshotBackfillService.class),
                mock(MarketDataService.class),
                new SnapshotProfitStatusResolver(mock(FundNavDailyMapper.class))
        );

        var status = service.marketStatus();

        assertThat(status.primaryStatusText()).isNotBlank();
        assertThat(status.updateTime()).isNotNull();
        assertThat(status.markets()).extracting("market").containsExactly("A股", "港股", "美股");
        assertThat(status.markets()).allSatisfy(item -> assertThat(item.statusText()).isNotBlank());
    }

    @Test
    void overviewProfitTrendShouldAttachHistoricalIndexReturnRateAndConfirmedStatus() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        AiAnalysisReportMapper aiAnalysisReportMapper = mock(AiAnalysisReportMapper.class);
        HoldingSnapshotMapper holdingSnapshotMapper = mock(HoldingSnapshotMapper.class);
        FundEstimateIntradayMapper fundEstimateIntradayMapper = mock(FundEstimateIntradayMapper.class);
        FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        FundValuationService fundValuationService = mock(FundValuationService.class);
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding()));
        when(strategySignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(aiAnalysisReportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                snapshot(yesterday, "80.0000"),
                snapshot(today, "120.0000")
        ));
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav("000001", yesterday),
                nav("000001", today)
        ));
        when(marketDataService.historicalIndex(Mockito.eq("000300"), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
                indexPoint(yesterday, "4000.0000"),
                indexPoint(today, "4040.0000")
        ));
        when(fundValuationService.estimate(any(), any(), any(), any())).thenReturn(new FundValuationResult("沪深300", BigDecimal.ZERO, "TEST", "TEST", "A股交易中"));
        when(fundValuationService.marketStatus(any(), any())).thenReturn("A股交易中");
        DashboardServiceImpl service = new DashboardServiceImpl(
                portfolioAccountService,
                fundHoldingMapper,
                strategySignalMapper,
                aiAnalysisReportMapper,
                holdingSnapshotMapper,
                fundEstimateIntradayMapper,
                fundNavDailyMapper,
                new ObjectMapper(),
                fundValuationService,
                new TradingCalendarService(new QuantFundProperties()),
                mock(HoldingSnapshotBackfillService.class),
                marketDataService,
                new SnapshotProfitStatusResolver(fundNavDailyMapper)
        );

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var overview = service.overview();

            assertThat(overview.profitTrend()).hasSize(2);
            assertThat(overview.profitTrend().get(0).indexReturnRate()).isEqualByComparingTo("0.0000");
            assertThat(overview.profitTrend().get(1).indexReturnRate()).isEqualByComparingTo("1.0000");
            assertThat(overview.profitTrend().get(0).profitStatus()).isEqualTo("CONFIRMED");
            assertThat(overview.profitTrend().get(0).profitStatusText()).contains("正式净值");
        }
    }

    @Test
    void overviewProfitTrendShouldMarkSnapshotSyncedWhenOfficialNavEvidenceMissing() {
        LocalDate fixedToday = LocalDate.of(2026, 6, 25);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        AiAnalysisReportMapper aiAnalysisReportMapper = mock(AiAnalysisReportMapper.class);
        HoldingSnapshotMapper holdingSnapshotMapper = mock(HoldingSnapshotMapper.class);
        FundEstimateIntradayMapper fundEstimateIntradayMapper = mock(FundEstimateIntradayMapper.class);
        FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        FundValuationService fundValuationService = mock(FundValuationService.class);
        when(portfolioAccountService.summary()).thenReturn(summary());
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding()));
        when(strategySignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(aiAnalysisReportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                snapshot(fixedToday.minusDays(1), "80.0000")
        ));
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(marketDataService.historicalIndex(Mockito.eq("000300"), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(fundValuationService.estimate(any(), any(), any(), any())).thenReturn(new FundValuationResult("沪深300", BigDecimal.ZERO, "TEST", "TEST", "A股交易中"));
        when(fundValuationService.marketStatus(any(), any())).thenReturn("A股交易中");
        DashboardServiceImpl service = dashboardService(
                portfolioAccountService,
                fundHoldingMapper,
                strategySignalMapper,
                aiAnalysisReportMapper,
                holdingSnapshotMapper,
                fundEstimateIntradayMapper,
                fundNavDailyMapper,
                fundValuationService,
                marketDataService,
                fixedToday,
                fixedToday.atTime(8, 0)
        );

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var overview = service.overview();

            assertThat(overview.profitTrend()).hasSize(1);
            assertThat(overview.profitTrend().getFirst().profitStatus()).isEqualTo("SNAPSHOT_SYNCED");
            assertThat(overview.profitTrend().getFirst().profitStatusText()).contains("待确认");
        }
    }

    @Test
    void overviewProfitTrendShouldAppendEstimatedTodayWhenSnapshotMissing() {
        LocalDate fixedToday = LocalDate.of(2026, 6, 25);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        AiAnalysisReportMapper aiAnalysisReportMapper = mock(AiAnalysisReportMapper.class);
        HoldingSnapshotMapper holdingSnapshotMapper = mock(HoldingSnapshotMapper.class);
        FundEstimateIntradayMapper fundEstimateIntradayMapper = mock(FundEstimateIntradayMapper.class);
        FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        FundValuationService fundValuationService = mock(FundValuationService.class);
        when(portfolioAccountService.summary()).thenReturn(summary("10000.0000", "500.0000", "100.0000"));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding()));
        when(strategySignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(aiAnalysisReportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                snapshot(fixedToday.minusDays(1), "80.0000")
        ));
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(marketDataService.historicalIndex(Mockito.eq("000300"), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(fundValuationService.estimate(any(), any(), any(), any())).thenReturn(new FundValuationResult("沪深300", BigDecimal.ONE, "TEST", "TEST", "A股交易中"));
        when(fundValuationService.marketStatus(any(), any())).thenReturn("A股交易中");
        DashboardServiceImpl service = dashboardService(
                portfolioAccountService,
                fundHoldingMapper,
                strategySignalMapper,
                aiAnalysisReportMapper,
                holdingSnapshotMapper,
                fundEstimateIntradayMapper,
                fundNavDailyMapper,
                fundValuationService,
                marketDataService,
                fixedToday,
                fixedToday.atTime(10, 0)
        );

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var overview = service.overview();

            assertThat(overview.profitTrend()).hasSize(2);
            assertThat(overview.profitTrend().get(1).date()).isEqualTo(fixedToday);
            assertThat(overview.profitTrend().get(1).dailyProfit()).isEqualByComparingTo("100.0000");
            assertThat(overview.profitTrend().get(1).profitStatus()).isEqualTo("ESTIMATED");
            assertThat(overview.profitTrend().get(1).profitStatusText()).contains("盘中预估");
        }
    }

    @Test
    void overviewShouldKeepTodayProfitDuringLunchBreak() {
        LocalDate fixedToday = LocalDate.of(2026, 6, 25);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        AiAnalysisReportMapper aiAnalysisReportMapper = mock(AiAnalysisReportMapper.class);
        HoldingSnapshotMapper holdingSnapshotMapper = mock(HoldingSnapshotMapper.class);
        FundEstimateIntradayMapper fundEstimateIntradayMapper = mock(FundEstimateIntradayMapper.class);
        FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        FundValuationService fundValuationService = mock(FundValuationService.class);
        when(portfolioAccountService.summary()).thenReturn(summary("10000.0000", "500.0000", "0.0000"));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding()));
        when(strategySignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(aiAnalysisReportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(marketDataService.historicalIndex(Mockito.eq("000300"), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(fundValuationService.estimate(any(), any(), any(), any())).thenReturn(new FundValuationResult("沪深300", BigDecimal.ONE, "TEST", "TEST", "A股午间休市"));
        when(fundValuationService.marketStatus(any(), any())).thenReturn("A股午间休市");
        DashboardServiceImpl service = dashboardService(
                portfolioAccountService,
                fundHoldingMapper,
                strategySignalMapper,
                aiAnalysisReportMapper,
                holdingSnapshotMapper,
                fundEstimateIntradayMapper,
                fundNavDailyMapper,
                fundValuationService,
                marketDataService,
                fixedToday,
                fixedToday.atTime(11, 31)
        );

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var overview = service.overview();

            assertThat(overview.summary().dailyProfit()).isEqualByComparingTo("100.0000");
            assertThat(overview.topHoldings().getFirst().dailyProfit()).isEqualByComparingTo("100.0000");
            assertThat(overview.profitTrend()).singleElement()
                    .satisfies(point -> assertThat(point.dailyProfit()).isEqualByComparingTo("100.0000"));
        }
    }

    @Test
    void overviewSummaryDailyProfitShouldIncludeHoldingsBeyondTopTen() {
        LocalDate fixedToday = LocalDate.of(2026, 6, 25);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        AiAnalysisReportMapper aiAnalysisReportMapper = mock(AiAnalysisReportMapper.class);
        HoldingSnapshotMapper holdingSnapshotMapper = mock(HoldingSnapshotMapper.class);
        FundEstimateIntradayMapper fundEstimateIntradayMapper = mock(FundEstimateIntradayMapper.class);
        FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        FundValuationService fundValuationService = mock(FundValuationService.class);
        List<FundHolding> holdings = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(index -> holding((long) index, "F" + index, "10.0000"))
                .toList();
        when(portfolioAccountService.summary()).thenReturn(summary("11000.0000", "500.0000", "0.0000"));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(holdings);
        when(strategySignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(aiAnalysisReportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(marketDataService.historicalIndex(Mockito.eq("000300"), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(fundValuationService.estimate(any(), any(), any(), any())).thenReturn(new FundValuationResult("沪深300", BigDecimal.ONE, "TEST", "TEST", "A股交易中"));
        when(fundValuationService.marketStatus(any(), any())).thenReturn("A股交易中");
        DashboardServiceImpl service = dashboardService(
                portfolioAccountService,
                fundHoldingMapper,
                strategySignalMapper,
                aiAnalysisReportMapper,
                holdingSnapshotMapper,
                fundEstimateIntradayMapper,
                fundNavDailyMapper,
                fundValuationService,
                marketDataService,
                fixedToday,
                fixedToday.atTime(10, 0)
        );

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var overview = service.overview();

            assertThat(overview.topHoldings()).hasSize(10);
            assertThat(overview.summary().dailyProfit()).isEqualByComparingTo("110.0000");
            assertThat(overview.profitTrend()).singleElement()
                    .satisfies(point -> assertThat(point.dailyProfit()).isEqualByComparingTo("110.0000"));
        }
    }

    @Test
    void overviewShouldRecalculateHoldingProfitFromOfficialNavForDisplay() {
        LocalDate fixedToday = LocalDate.of(2026, 6, 25);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        AiAnalysisReportMapper aiAnalysisReportMapper = mock(AiAnalysisReportMapper.class);
        HoldingSnapshotMapper holdingSnapshotMapper = mock(HoldingSnapshotMapper.class);
        FundEstimateIntradayMapper fundEstimateIntradayMapper = mock(FundEstimateIntradayMapper.class);
        FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        FundValuationService fundValuationService = mock(FundValuationService.class);
        FundHolding holding = holding(1L, "000001", "10.0000");
        holding.setHoldingShare(new BigDecimal("1000.0000"));
        holding.setHoldingCost(new BigDecimal("9500.0000"));
        holding.setHoldingAmount(new BigDecimal("10000.0000"));
        holding.setHoldingProfit(new BigDecimal("500.0000"));
        FundNavDaily officialNav = nav("000001", fixedToday);
        officialNav.setUnitNav(new BigDecimal("10.2000"));
        when(portfolioAccountService.summary()).thenReturn(summary("10000.0000", "500.0000", "10.0000"));
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding));
        when(strategySignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(aiAnalysisReportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(holdingSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(officialNav));
        when(marketDataService.historicalIndex(Mockito.eq("000300"), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(fundValuationService.estimate(any(), any(), any(), any())).thenReturn(new FundValuationResult("TEST", BigDecimal.ONE, "TEST", "TEST", "A鑲′氦鏄撲腑"));
        when(fundValuationService.marketStatus(any(), any())).thenReturn("A鑲′氦鏄撲腑");
        DashboardServiceImpl service = dashboardService(
                portfolioAccountService,
                fundHoldingMapper,
                strategySignalMapper,
                aiAnalysisReportMapper,
                holdingSnapshotMapper,
                fundEstimateIntradayMapper,
                fundNavDailyMapper,
                fundValuationService,
                marketDataService,
                fixedToday,
                fixedToday.atTime(20, 0)
        );

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            var overview = service.overview();

            assertThat(overview.topHoldings()).singleElement()
                    .satisfies(item -> {
                        assertThat(item.officialNavUpdated()).isTrue();
                        assertThat(item.holdingAmount()).isEqualByComparingTo("10200.0000");
                        assertThat(item.holdingProfit()).isEqualByComparingTo("700.0000");
                        assertThat(item.holdingProfitRate()).isEqualByComparingTo("7.3684");
                    });
            assertThat(overview.summary().totalAsset()).isEqualByComparingTo("10200.0000");
            assertThat(overview.summary().currentProfit()).isEqualByComparingTo("700.0000");
        }
    }

    private PortfolioSummaryVO summary() {
        return summary("0.0000", "0.0000", "0.0000");
    }

    private PortfolioSummaryVO summary(String totalAsset, String currentProfit, String dailyProfit) {
        return new PortfolioSummaryVO(
                new BigDecimal(totalAsset),
                BigDecimal.ZERO,
                new BigDecimal(currentProfit),
                BigDecimal.ZERO,
                new BigDecimal(dailyProfit),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                List.of()
        );
    }

    private HoldingSnapshot snapshot(LocalDate date, String dailyProfit) {
        HoldingSnapshot snapshot = new HoldingSnapshot();
        snapshot.setHoldingId(1L);
        snapshot.setSnapshotDate(date);
        snapshot.setTotalAsset(new BigDecimal("10000.0000"));
        snapshot.setHoldingProfit(new BigDecimal("500.0000"));
        snapshot.setDailyProfit(new BigDecimal(dailyProfit));
        return snapshot;
    }

    private FundHolding holding() {
        return holding(1L, "000001", "100.0000");
    }

    private FundHolding holding(Long id, String fundCode, String dailyProfit) {
        FundHolding holding = new FundHolding();
        holding.setId(id);
        holding.setUserId(1L);
        holding.setAccountId(1L);
        holding.setFundCode(fundCode);
        holding.setFundName("测试基金");
        holding.setFundType("指数基金");
        holding.setActiveFund(1);
        holding.setHoldingAmount(new BigDecimal("10000.0000"));
        holding.setHoldingShare(new BigDecimal("1000.0000"));
        holding.setHoldingCost(new BigDecimal("9500.0000"));
        holding.setCurrentEstimateNav(new BigDecimal("1.0100"));
        holding.setLatestOfficialNav(new BigDecimal("1.0000"));
        holding.setHoldingProfit(new BigDecimal("500.0000"));
        holding.setHoldingProfitRate(new BigDecimal("5.0000"));
        holding.setDailyProfit(new BigDecimal(dailyProfit));
        holding.setHoldingDays(30);
        holding.setSourcePlatform("MANUAL");
        holding.setRegularInvestment(0);
        holding.setCoreHolding(1);
        holding.setWatchFocus(1);
        return holding;
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

    private FundNavDaily nav(String fundCode, LocalDate navDate) {
        FundNavDaily nav = new FundNavDaily();
        nav.setFundCode(fundCode);
        nav.setNavDate(navDate);
        nav.setUnitNav(BigDecimal.ONE);
        nav.setSourceName("EAST_MONEY");
        return nav;
    }

    private DashboardServiceImpl dashboardService(PortfolioAccountService portfolioAccountService,
                                                  FundHoldingMapper fundHoldingMapper,
                                                  StrategySignalMapper strategySignalMapper,
                                                  AiAnalysisReportMapper aiAnalysisReportMapper,
                                                  HoldingSnapshotMapper holdingSnapshotMapper,
                                                  FundEstimateIntradayMapper fundEstimateIntradayMapper,
                                                  FundNavDailyMapper fundNavDailyMapper,
                                                  FundValuationService fundValuationService,
                                                  MarketDataService marketDataService,
                                                  LocalDate fixedToday) {
        return dashboardService(portfolioAccountService, fundHoldingMapper, strategySignalMapper,
                aiAnalysisReportMapper, holdingSnapshotMapper, fundEstimateIntradayMapper,
                fundNavDailyMapper, fundValuationService, marketDataService, fixedToday, fixedToday.atTime(10, 0));
    }

    private DashboardServiceImpl dashboardService(PortfolioAccountService portfolioAccountService,
                                                  FundHoldingMapper fundHoldingMapper,
                                                  StrategySignalMapper strategySignalMapper,
                                                  AiAnalysisReportMapper aiAnalysisReportMapper,
                                                  HoldingSnapshotMapper holdingSnapshotMapper,
                                                  FundEstimateIntradayMapper fundEstimateIntradayMapper,
                                                  FundNavDailyMapper fundNavDailyMapper,
                                                  FundValuationService fundValuationService,
                                                  MarketDataService marketDataService,
                                                  LocalDate fixedToday,
                                                  LocalDateTime fixedNow) {
        return new DashboardServiceImpl(
                portfolioAccountService,
                fundHoldingMapper,
                strategySignalMapper,
                aiAnalysisReportMapper,
                holdingSnapshotMapper,
                fundEstimateIntradayMapper,
                fundNavDailyMapper,
                new ObjectMapper(),
                fundValuationService,
                new TradingCalendarService(new QuantFundProperties()),
                mock(HoldingSnapshotBackfillService.class),
                marketDataService,
                new SnapshotProfitStatusResolver(fundNavDailyMapper)
        ) {
            @Override
            protected LocalDate today() {
                return fixedToday;
            }

            @Override
            protected LocalDateTime now() {
                return fixedNow;
            }
        };
    }
}
