package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.dto.holding.ClearHoldingRequest;
import com.lk.quantfund.dto.holding.UpdateHoldingRequest;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.enums.FundType;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.AiAnalysisReportMapper;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.scheduler.HoldingSnapshotBackfillService;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class FundHoldingServiceImplTest {

    private final FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
    private final PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
    private final AiAnalysisReportMapper aiAnalysisReportMapper = mock(AiAnalysisReportMapper.class);
    private final HoldingSnapshotMapper snapshotMapper = mock(HoldingSnapshotMapper.class);
    private final FundQueryService fundQueryService = mock(FundQueryService.class);
    private final PortfolioAccountService portfolioAccountService = mock(PortfolioAccountService.class);
    private final FundValuationService valuationService = mock(FundValuationService.class);
    private final HoldingSnapshotBackfillService backfillService = mock(HoldingSnapshotBackfillService.class);
    private final TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);

    @Test
    void syncOfficialNavUpsertsDelayedNavSnapshotOnEffectiveDate() {
        FundHolding holding = holding();
        PortfolioAccount account = account();
        LocalDate navDate = LocalDate.now().minusDays(1);
        when(holdingMapper.selectList(any())).thenReturn(List.of(holding), List.of());
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                navPoint(navDate.minusDays(1), "1.0000"),
                navPoint(navDate, "1.0500")
        ));
        when(accountMapper.selectById(10L)).thenReturn(account);
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        when(valuationService.estimate(any(), any(), any(), any()))
                .thenReturn(new FundValuationResult("TEST", BigDecimal.ZERO, "TEST", "TEST", "TEST"));
        FundHoldingServiceImpl service = new FundHoldingServiceImpl(
                holdingMapper,
                accountMapper,
                mock(AiAnalysisReportMapper.class),
                portfolioAccountService,
                fundQueryService,
                valuationService,
                new TradingCalendarService(new QuantFundProperties()),
                snapshotMapper,
                backfillService,
                tradeRecordMapper
        );
        ArgumentCaptor<HoldingSnapshot> snapshotCaptor = ArgumentCaptor.forClass(HoldingSnapshot.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.syncOfficialNav();
        }

        verify(holdingMapper).updateById(any(FundHolding.class));
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        HoldingSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.getSnapshotDate()).isEqualTo(new TradingCalendarService(new QuantFundProperties()).nextTradingDay(navDate));
        assertThat(snapshot.getHoldingAmount()).isEqualByComparingTo("1050.0000");
        assertThat(snapshot.getDailyProfit()).isEqualByComparingTo("50.0000");
        assertThat(snapshot.getPositionRate()).isEqualByComparingTo("10.5000");
        verify(portfolioAccountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void recalculateShouldRejectMissingAmountAndShareWithReadableMessage() {
        FundHolding holding = holding();
        holding.setHoldingAmount(BigDecimal.ZERO);
        holding.setHoldingShare(BigDecimal.ZERO);
        holding.setHoldingCost(BigDecimal.ONE);
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        FundHoldingServiceImpl service = service();

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.recalculate(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("持有金额或持有份额不能为空");
        }
    }

    @Test
    void detailShouldReportReadableMessageWhenHoldingMissing() {
        when(holdingMapper.selectOne(any())).thenReturn(null);
        FundHoldingServiceImpl service = service();

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.detail(404L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("基金持仓不存在");
        }
    }

    @Test
    void clearShouldZeroCurrentPositionAndKeepHoldingRecord() {
        FundHolding holding = holding();
        holding.setHoldingAmount(new BigDecimal("1200.0000"));
        holding.setHoldingShare(new BigDecimal("1000.0000"));
        holding.setHoldingCost(new BigDecimal("900.0000"));
        holding.setHoldingProfit(new BigDecimal("300.0000"));
        holding.setHoldingProfitRate(new BigDecimal("33.3333"));
        holding.setDailyProfit(new BigDecimal("12.0000"));
        holding.setActiveFund(1);
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(valuationService.estimate(any(), any(), any(), any()))
                .thenReturn(new FundValuationResult("TEST", BigDecimal.ZERO, "TEST", "TEST", "TEST"));
        FundHoldingServiceImpl service = service();
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);
        ArgumentCaptor<TradeRecord> tradeCaptor = ArgumentCaptor.forClass(TradeRecord.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.clear(100L, null);
        }

        verify(holdingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getActiveFund()).isZero();
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("0.0000");
        assertThat(saved.getHoldingShare()).isEqualByComparingTo("0.0000");
        assertThat(saved.getHoldingCost()).isEqualByComparingTo("0.0000");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("0.0000");
        assertThat(saved.getHoldingProfitRate()).isEqualByComparingTo("0.0000");
        assertThat(saved.getDailyProfit()).isEqualByComparingTo("0.0000");
        verify(tradeRecordMapper).insert(tradeCaptor.capture());
        TradeRecord trade = tradeCaptor.getValue();
        assertThat(trade.getTradeType()).isEqualTo("SELL");
        assertThat(trade.getTradeStatus()).isEqualTo("COMPLETED");
        assertThat(trade.getTradeAmount()).isEqualByComparingTo("1200.0000");
        assertThat(trade.getTradeShare()).isEqualByComparingTo("1000.0000");
        assertThat(trade.getTradeNav()).isEqualByComparingTo("1.0000");
        assertThat(trade.getRemark()).contains("清仓");
        assertThat(trade.getRemark()).contains(SystemConstants.SIMULATED_TRADE_NOTICE);
        verify(holdingMapper, never()).deleteById(any(Long.class));
        verify(aiAnalysisReportMapper, never()).delete(any());
        verify(portfolioAccountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void clearEmptyHoldingShouldNotCreateTradeRecord() {
        FundHolding holding = holding();
        holding.setHoldingAmount(BigDecimal.ZERO);
        holding.setHoldingShare(BigDecimal.ZERO);
        holding.setHoldingCost(BigDecimal.ZERO);
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(valuationService.estimate(any(), any(), any(), any()))
                .thenReturn(new FundValuationResult("TEST", BigDecimal.ZERO, "TEST", "TEST", "TEST"));
        FundHoldingServiceImpl service = service();

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.clear(100L, null);
        }

        verify(tradeRecordMapper, never()).insert(any(TradeRecord.class));
        verify(holdingMapper).updateById(any(FundHolding.class));
        verify(portfolioAccountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void clearShouldUseConfirmedAmountAndFeeInTradeRecord() {
        FundHolding holding = holding();
        holding.setHoldingAmount(new BigDecimal("1200.0000"));
        holding.setHoldingShare(new BigDecimal("1000.0000"));
        holding.setHoldingCost(new BigDecimal("900.0000"));
        holding.setCurrentEstimateNav(new BigDecimal("1.2000"));
        holding.setActiveFund(1);
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(valuationService.estimate(any(), any(), any(), any()))
                .thenReturn(new FundValuationResult("TEST", BigDecimal.ZERO, "TEST", "TEST", "TEST"));
        FundHoldingServiceImpl service = service();
        ArgumentCaptor<TradeRecord> tradeCaptor = ArgumentCaptor.forClass(TradeRecord.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.clear(100L, new ClearHoldingRequest(
                    new BigDecimal("1198.5000"),
                    new BigDecimal("1.5000"),
                    "确认清仓流水"
            ));
        }

        verify(tradeRecordMapper).insert(tradeCaptor.capture());
        TradeRecord trade = tradeCaptor.getValue();
        assertThat(trade.getTradeAmount()).isEqualByComparingTo("1198.5000");
        assertThat(trade.getTradeFee()).isEqualByComparingTo("1.5000");
        assertThat(trade.getTradeShare()).isEqualByComparingTo("1000.0000");
        assertThat(trade.getTradeNav()).isEqualByComparingTo("1.2000");
        assertThat(trade.getRemark()).isEqualTo("确认清仓流水，" + SystemConstants.SIMULATED_TRADE_NOTICE);
    }

    @Test
    void updateShouldNotFetchIntradayEstimateOutsideTradingWindow() {
        FundHolding holding = holding();
        holding.setFundName("天弘电网设备特高压指数C");
        holding.setFundType("INDEX");
        holding.setCurrentEstimateNav(new BigDecimal("1.5298"));
        holding.setLatestOfficialNav(new BigDecimal("1.5117"));
        holding.setHoldingAmount(new BigDecimal("1062.7600"));
        holding.setHoldingShare(new BigDecimal("694.7052"));
        holding.setHoldingCost(new BigDecimal("956.6800"));
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(holdingMapper.selectList(any())).thenReturn(List.of(holding));
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                navPoint(LocalDate.of(2026, 6, 23), "1.5130"),
                navPoint(LocalDate.of(2026, 6, 24), "1.5117")
        ));
        when(valuationService.estimate(any(), any(), any(), any()))
                .thenReturn(new FundValuationResult("TEST", BigDecimal.ZERO, "TEST", "TEST", "TEST"));
        FundHoldingServiceImpl service = service(LocalDateTime.of(2026, 6, 25, 18, 0));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.update(100L, new UpdateHoldingRequest(
                    10L,
                    "025833",
                    "天弘电网设备特高压指数C",
                    FundType.INDEX,
                    false,
                    new BigDecimal("1062.7600"),
                    new BigDecimal("694.7052"),
                    new BigDecimal("956.6800"),
                    new BigDecimal("106.0800"),
                    null,
                    null,
                    "手动添加",
                    false,
                    true,
                    true
            ));
        }

        verify(fundQueryService, never()).getIntradayEstimate(any(), any(Boolean.class));
        verify(holdingMapper).updateById(any(FundHolding.class));
    }

    @Test
    void updateShouldPreserveEnteredAmountAndCurrentEstimateOutsideTradingWindow() {
        FundHolding holding = holding();
        holding.setFundName("GF Vision");
        holding.setFundType("ACTIVE_EQUITY");
        holding.setCurrentEstimateNav(new BigDecimal("1.5298"));
        holding.setLatestOfficialNav(new BigDecimal("1.5117"));
        holding.setHoldingAmount(new BigDecimal("1062.7600"));
        holding.setHoldingShare(new BigDecimal("694.7052"));
        holding.setHoldingCost(new BigDecimal("956.6800"));
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(holdingMapper.selectList(any())).thenReturn(List.of(holding));
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                navPoint(LocalDate.of(2026, 6, 23), "1.5130"),
                navPoint(LocalDate.of(2026, 6, 24), "1.5117")
        ));
        when(valuationService.estimate(any(), any(), any(), any()))
                .thenReturn(new FundValuationResult("TEST", null, "TEST", "TEST", "TEST"));
        FundHoldingServiceImpl service = service(LocalDateTime.of(2026, 6, 25, 18, 0));
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.update(100L, new UpdateHoldingRequest(
                    10L,
                    "025833",
                    "GF Vision",
                    FundType.ACTIVE_EQUITY,
                    false,
                    new BigDecimal("1062.7600"),
                    new BigDecimal("694.7052"),
                    new BigDecimal("956.6800"),
                    new BigDecimal("106.0800"),
                    null,
                    null,
                    "manual",
                    false,
                    true,
                    true
            ));
        }

        verify(holdingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("1062.7600");
        assertThat(saved.getCurrentEstimateNav()).isEqualByComparingTo("1.5298");
        assertThat(saved.getLatestOfficialNav()).isEqualByComparingTo("1.5117");
        assertThat(saved.getDailyProfit()).isGreaterThan(BigDecimal.ZERO);
        verify(fundQueryService, never()).getIntradayEstimate(any(), any(Boolean.class));
    }

    private FundHoldingServiceImpl service() {
        return service(null);
    }

    private FundHoldingServiceImpl service(LocalDateTime fixedNow) {
        return new FundHoldingServiceImpl(
                holdingMapper,
                accountMapper,
                aiAnalysisReportMapper,
                portfolioAccountService,
                fundQueryService,
                valuationService,
                new TradingCalendarService(new QuantFundProperties()),
                snapshotMapper,
                backfillService,
                tradeRecordMapper
        ) {
            @Override
            protected LocalDateTime now() {
                return fixedNow == null ? super.now() : fixedNow;
            }
        };
    }

    private FundHolding holding() {
        FundHolding holding = new FundHolding();
        holding.setId(100L);
        holding.setUserId(1L);
        holding.setAccountId(10L);
        holding.setFundCode("000001");
        holding.setFundName("Global Growth QDII");
        holding.setFundType("QDII");
        holding.setHoldingShare(new BigDecimal("1000"));
        holding.setHoldingCost(new BigDecimal("900"));
        holding.setLatestOfficialNav(new BigDecimal("1.0000"));
        return holding;
    }

    private FundNavPointDTO navPoint(LocalDate navDate, String unitNav) {
        return new FundNavPointDTO(
                "000001",
                navDate,
                new BigDecimal(unitNav),
                new BigDecimal(unitNav),
                null,
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
