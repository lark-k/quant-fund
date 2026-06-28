package com.lk.quantfund.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.service.AiAnalysisService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.StrategyService;
import com.lk.quantfund.service.valuation.FundValuationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScheduledFundTaskServiceTest {

    @Test
    void refreshIntradayEstimatesSkipsOutsideTradingWindowWithoutWritingHoldings() {
        QuantFundProperties properties = new QuantFundProperties();
        properties.getScheduler().setHolidays(java.time.LocalDate.now().toString());
        FundQueryService fundQueryService = mock(FundQueryService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        ScheduledFundTaskService service = new ScheduledFundTaskService(
                properties,
                fundQueryService,
                mock(AiAnalysisService.class),
                mock(StrategyService.class),
                portfolioAccountService,
                fundHoldingMapper,
                mock(PortfolioAccountMapper.class),
                mock(HoldingSnapshotMapper.class),
                mock(FundValuationService.class),
                new TradingCalendarService(properties)
        );

        SchedulerTaskResult result = service.refreshIntradayEstimates();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailureCount()).isZero();
        verify(fundHoldingMapper, never()).selectList(any());
        verify(fundQueryService, never()).getIntradayEstimate(any(), any(Boolean.class));
        verify(fundHoldingMapper, never()).updateById(any(FundHolding.class));
        verify(portfolioAccountService, never()).recalculateOwnedAccount(any(), any());
    }

    @Test
    void syncOfficialNavUpsertsDelayedNavSnapshotOnEffectiveDate() {
        QuantFundProperties properties = new QuantFundProperties();
        FundQueryService fundQueryService = mock(FundQueryService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        HoldingSnapshotMapper snapshotMapper = mock(HoldingSnapshotMapper.class);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        FundHolding holding = holding("QDII", "Global Growth QDII");
        LocalDate navDate = LocalDate.now().minusDays(1);
        when(tradingCalendarService.nextTradingDay(navDate)).thenReturn(LocalDate.now());
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of(holding));
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                navPoint(navDate.minusDays(1), "1.0000", null),
                navPoint(navDate, "1.0500", "5.0000")
        ));
        when(accountMapper.selectById(10L)).thenReturn(account());
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        ScheduledFundTaskService service = new ScheduledFundTaskService(
                properties,
                fundQueryService,
                mock(AiAnalysisService.class),
                mock(StrategyService.class),
                portfolioAccountService,
                fundHoldingMapper,
                accountMapper,
                snapshotMapper,
                mock(FundValuationService.class),
                tradingCalendarService
        );
        ArgumentCaptor<HoldingSnapshot> snapshotCaptor = ArgumentCaptor.forClass(HoldingSnapshot.class);

        SchedulerTaskResult result = service.syncOfficialNav();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(fundHoldingMapper).updateById(any(FundHolding.class));
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        HoldingSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.getSnapshotDate()).isEqualTo(LocalDate.now());
        assertThat(snapshot.getHoldingAmount()).isEqualByComparingTo("1050.0000");
        assertThat(snapshot.getDailyProfit()).isEqualByComparingTo("50.0000");
        assertThat(snapshot.getPositionRate()).isEqualByComparingTo("10.5000");
        verify(portfolioAccountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void syncOfficialNavDoesNotOverwriteExistingHistoricalSnapshot() {
        QuantFundProperties properties = new QuantFundProperties();
        FundQueryService fundQueryService = mock(FundQueryService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        HoldingSnapshotMapper snapshotMapper = mock(HoldingSnapshotMapper.class);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        FundHolding holding = holding("QDII", "Global Growth QDII");
        LocalDate snapshotDate = LocalDate.now().minusDays(1);
        LocalDate navDate = snapshotDate.minusDays(1);
        HoldingSnapshot existingSnapshot = new HoldingSnapshot();
        existingSnapshot.setId(200L);
        existingSnapshot.setHoldingId(holding.getId());
        existingSnapshot.setSnapshotDate(snapshotDate);
        existingSnapshot.setDailyProfit(new BigDecimal("91.6200"));
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of(holding));
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                navPoint(navDate.minusDays(1), "1.0000", null),
                navPoint(navDate, "1.0500", "5.0000")
        ));
        when(accountMapper.selectById(10L)).thenReturn(account());
        when(tradingCalendarService.nextTradingDay(navDate)).thenReturn(snapshotDate);
        when(snapshotMapper.selectOne(any())).thenReturn(null, existingSnapshot);
        ScheduledFundTaskService service = new ScheduledFundTaskService(
                properties,
                fundQueryService,
                mock(AiAnalysisService.class),
                mock(StrategyService.class),
                portfolioAccountService,
                fundHoldingMapper,
                accountMapper,
                snapshotMapper,
                mock(FundValuationService.class),
                tradingCalendarService
        );

        SchedulerTaskResult result = service.syncOfficialNav();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(snapshotMapper, never()).insert(any(HoldingSnapshot.class));
        verify(snapshotMapper, never()).updateById(any(HoldingSnapshot.class));
        verify(portfolioAccountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void syncOfficialNavRollsPlatformAmountByDailyRate() {
        QuantFundProperties properties = new QuantFundProperties();
        FundQueryService fundQueryService = mock(FundQueryService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        HoldingSnapshotMapper snapshotMapper = mock(HoldingSnapshotMapper.class);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        FundHolding holding = holding("MIXED", "Active Fund");
        holding.setHoldingAmount(new BigDecimal("1000.0000"));
        LocalDate today = LocalDate.now();
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of(holding));
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                navPoint(today.minusDays(1), "1.0000", null),
                navPoint(today, "0.9800", "-2.0000")
        ));
        when(accountMapper.selectById(10L)).thenReturn(account());
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        ScheduledFundTaskService service = new ScheduledFundTaskService(
                properties,
                fundQueryService,
                mock(AiAnalysisService.class),
                mock(StrategyService.class),
                portfolioAccountService,
                fundHoldingMapper,
                accountMapper,
                snapshotMapper,
                mock(FundValuationService.class),
                new TradingCalendarService(properties)
        );
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        SchedulerTaskResult result = service.syncOfficialNav();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(fundHoldingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("980.0000");
        assertThat(saved.getDailyProfit()).isEqualByComparingTo("-20.0000");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("80.0000");
        verify(portfolioAccountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void refreshIntradayEstimatesKeepsHoldingAmountUntilOfficialNav() {
        QuantFundProperties properties = new QuantFundProperties();
        FundQueryService fundQueryService = mock(FundQueryService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        FundHolding holding = holding("MIXED", "Active Fund");
        holding.setHoldingAmount(new BigDecimal("1000.0000"));
        when(tradingCalendarService.isIntradayEstimateWindow(any())).thenReturn(true);
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of(holding));
        when(fundQueryService.getIntradayEstimate("000001", false)).thenReturn(new FundEstimateDTO(
                "000001",
                "Active Fund",
                new BigDecimal("1.0200"),
                new BigDecimal("2.0000"),
                LocalDate.now(),
                LocalDateTime.now(),
                "TEST",
                false,
                "{}"
        ));
        ScheduledFundTaskService service = new ScheduledFundTaskService(
                properties,
                fundQueryService,
                mock(AiAnalysisService.class),
                mock(StrategyService.class),
                portfolioAccountService,
                fundHoldingMapper,
                mock(PortfolioAccountMapper.class),
                mock(HoldingSnapshotMapper.class),
                mock(FundValuationService.class),
                tradingCalendarService
        );
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        SchedulerTaskResult result = service.refreshIntradayEstimates();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(fundHoldingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getCurrentEstimateNav()).isEqualByComparingTo("1.0200");
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("1000.0000");
        assertThat(saved.getDailyProfit()).isEqualByComparingTo("20.0000");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("120.0000");
        assertThat(saved.getHoldingProfitRate()).isEqualByComparingTo("13.3333");
        verify(portfolioAccountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void refreshIntradayEstimatesKeepsPlatformAmountWhenEstimateMoved() {
        QuantFundProperties properties = new QuantFundProperties();
        FundQueryService fundQueryService = mock(FundQueryService.class);
        FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        FundHolding holding = holding("MIXED", "Active Fund");
        holding.setHoldingAmount(new BigDecimal("1020.0000"));
        when(tradingCalendarService.isIntradayEstimateWindow(any())).thenReturn(true);
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of(holding));
        when(fundQueryService.getIntradayEstimate("000001", false)).thenReturn(new FundEstimateDTO(
                "000001",
                "Active Fund",
                new BigDecimal("1.0200"),
                new BigDecimal("2.0000"),
                LocalDate.now(),
                LocalDateTime.now(),
                "TEST",
                false,
                "{}"
        ));
        ScheduledFundTaskService service = new ScheduledFundTaskService(
                properties,
                fundQueryService,
                mock(AiAnalysisService.class),
                mock(StrategyService.class),
                portfolioAccountService,
                fundHoldingMapper,
                mock(PortfolioAccountMapper.class),
                mock(HoldingSnapshotMapper.class),
                mock(FundValuationService.class),
                tradingCalendarService
        );
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        SchedulerTaskResult result = service.refreshIntradayEstimates();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(fundHoldingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("1020.0000");
        assertThat(saved.getDailyProfit()).isEqualByComparingTo("20.4000");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("140.4000");
    }

    private FundHolding holding(String fundType, String fundName) {
        FundHolding holding = new FundHolding();
        holding.setId(100L);
        holding.setUserId(1L);
        holding.setAccountId(10L);
        holding.setFundCode("000001");
        holding.setFundName(fundName);
        holding.setFundType(fundType);
        holding.setHoldingShare(new BigDecimal("1000"));
        holding.setHoldingCost(new BigDecimal("900"));
        holding.setLatestOfficialNav(new BigDecimal("1.0000"));
        return holding;
    }

    private FundNavPointDTO navPoint(LocalDate navDate, String unitNav, String dailyGrowthRate) {
        return new FundNavPointDTO(
                "000001",
                navDate,
                new BigDecimal(unitNav),
                new BigDecimal(unitNav),
                dailyGrowthRate == null ? null : new BigDecimal(dailyGrowthRate),
                "TEST"
        );
    }

    private PortfolioAccount account() {
        PortfolioAccount account = new PortfolioAccount();
        account.setId(10L);
        account.setTotalAsset(new BigDecimal("10000"));
        return account;
    }
}
