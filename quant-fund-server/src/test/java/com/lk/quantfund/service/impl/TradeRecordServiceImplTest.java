package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.trade.ConvertPairTradeRequest;
import com.lk.quantfund.dto.trade.TradeRecordRequest;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.InvestmentPlan;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.InvestmentPlanMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.scheduler.SchedulerTaskResult;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.PortfolioAccountService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class TradeRecordServiceImplTest {

    @Test
    void createShouldRejectMissingTradeType() {
        TradeRecordServiceImpl tradeRecordService = new TradeRecordServiceImpl(
                mock(TradeRecordMapper.class),
                mock(FundHoldingMapper.class),
                mock(PortfolioAccountMapper.class),
                mock(PortfolioAccountService.class),
                mock(InvestmentPlanMapper.class),
                mock(FundQueryService.class),
                mock(TradingCalendarService.class)
        );
        TradeRecordRequest request = new TradeRecordRequest(
                1L,
                null,
                "000001",
                "Mock Fund",
                null,
                null,
                new BigDecimal("100.00"),
                null,
                new BigDecimal("1.0000"),
                BigDecimal.ZERO,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> tradeRecordService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("trade type is required");
    }

    @Test
    void completedBuyShouldIncreaseShareAndCostWithFee() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        FundHolding holding = holding();
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.create(completedRequest("BUY", "120.00", "100.0000", "1.2000", "1.50"));
        }

        verify(holdingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getHoldingShare()).isEqualByComparingTo("1100.0000");
        assertThat(saved.getHoldingCost()).isEqualByComparingTo("1121.5000");
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("1320.0000");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("198.5000");
        verify(accountService).adjustCashAmountOwnedAccount(
                1L,
                10L,
                new BigDecimal("-121.5000")
        );
        verify(accountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void convertPairShouldCreateLinkedOutAndInTrades() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        FundHolding outHolding = holding();
        FundHolding inHolding = holding();
        inHolding.setId(101L);
        inHolding.setFundCode("000002");
        inHolding.setFundName("Target Fund");
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(holdingMapper.selectOne(any())).thenReturn(outHolding, outHolding, outHolding, inHolding);
        doAnswer(invocation -> {
            TradeRecord record = invocation.getArgument(0);
            record.setId(record.getTradeType().equals("CONVERT_OUT") ? 200L : 201L);
            return 1;
        }).when(tradeRecordMapper).insert(any(TradeRecord.class));
        when(tradeRecordMapper.selectOne(any())).thenReturn(convertOutRecord());
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);
        ArgumentCaptor<TradeRecord> recordCaptor = ArgumentCaptor.forClass(TradeRecord.class);

        List<com.lk.quantfund.vo.trade.TradeRecordVO> result;
        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            result = service.createConvertPair(convertPairRequest());
        }

        verify(tradeRecordMapper, Mockito.times(2)).insert(recordCaptor.capture());
        List<TradeRecord> records = recordCaptor.getAllValues();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).tradeType()).isEqualTo("CONVERT_OUT");
        assertThat(result.get(1).tradeType()).isEqualTo("CONVERT_IN");
        assertThat(records.get(0).getFundCode()).isEqualTo("000001");
        assertThat(records.get(0).getFundName()).isEqualTo("Mock Fund");
        assertThat(records.get(1).getFundCode()).isEqualTo("000002");
        assertThat(records.get(1).getFundName()).isEqualTo("Target Fund");
        assertThat(records.get(1).getRelatedTradeId()).isEqualTo(200L);
        assertThat(records).allSatisfy(record ->
                assertThat(record.getRemark()).contains(SystemConstants.SIMULATED_TRADE_NOTICE));
        verify(accountService, Mockito.times(2)).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void createShouldAppendSimulatedNoticeToCustomRemark() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(holdingMapper.selectOne(any())).thenReturn(holding());
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);
        ArgumentCaptor<TradeRecord> recordCaptor = ArgumentCaptor.forClass(TradeRecord.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.create(request(
                    "BUY",
                    "120.00",
                    "100.0000",
                    "1.2000",
                    "0",
                    com.lk.quantfund.enums.TradeStatus.PROCESSING,
                    null,
                    "用户确认追加"
            ));
        }

        verify(tradeRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRemark())
                .isEqualTo("用户确认追加，" + SystemConstants.SIMULATED_TRADE_NOTICE);
    }

    @Test
    void convertPairShouldRejectOutHoldingFromDifferentAccount() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        FundHolding outHolding = holding();
        outHolding.setAccountId(99L);
        when(holdingMapper.selectOne(any())).thenReturn(outHolding);
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.createConvertPair(convertPairRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("holding does not belong to the selected account");
        }
    }

    @Test
    void completedRegularInvestShouldUseSameCostMethodAsBuy() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        FundHolding holding = holding();
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.create(completedRequest("REGULAR_INVEST", "60.00", null, "1.2000", "0"));
        }

        verify(holdingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getHoldingShare()).isEqualByComparingTo("1050.0000");
        assertThat(saved.getHoldingCost()).isEqualByComparingTo("1060.0000");
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("1260.0000");
    }

    @Test
    void dueRegularInvestCompensationShouldCreateTodayProcessingTradeAndAdvanceDomesticDailyPlanOneTradingDay() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        InvestmentPlanMapper investmentPlanMapper = mock(InvestmentPlanMapper.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        LocalDate runDate = LocalDate.of(2026, 7, 9);
        LocalDate nextTradingDay = LocalDate.of(2026, 7, 10);
        InvestmentPlan plan = dailyInvestmentPlan(runDate);
        when(investmentPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(tradeRecordMapper.selectOne(any())).thenReturn(null);
        when(holdingMapper.selectOne(any())).thenReturn(holding());
        when(tradingCalendarService.nextTradingDay(runDate)).thenReturn(nextTradingDay);
        TradeRecordServiceImpl service = new TradeRecordServiceImpl(
                tradeRecordMapper,
                holdingMapper,
                mock(PortfolioAccountMapper.class),
                mock(PortfolioAccountService.class),
                investmentPlanMapper,
                mock(FundQueryService.class),
                tradingCalendarService);
        ArgumentCaptor<TradeRecord> recordCaptor = ArgumentCaptor.forClass(TradeRecord.class);
        ArgumentCaptor<InvestmentPlan> planCaptor = ArgumentCaptor.forClass(InvestmentPlan.class);

        SchedulerTaskResult result = service.createDueRegularInvestTrades(runDate);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isZero();
        verify(tradeRecordMapper).insert(recordCaptor.capture());
        TradeRecord savedRecord = recordCaptor.getValue();
        assertThat(savedRecord.getUserId()).isEqualTo(1L);
        assertThat(savedRecord.getAccountId()).isEqualTo(10L);
        assertThat(savedRecord.getHoldingId()).isEqualTo(100L);
        assertThat(savedRecord.getFundCode()).isEqualTo("021528");
        assertThat(savedRecord.getFundName()).isEqualTo("Mock Daily Plan Fund");
        assertThat(savedRecord.getTradeType()).isEqualTo("REGULAR_INVEST");
        assertThat(savedRecord.getTradeStatus()).isEqualTo("PROCESSING");
        assertThat(savedRecord.getTradeAmount()).isEqualByComparingTo("20.0000");
        assertThat(savedRecord.getTradeTime()).isEqualTo(LocalDateTime.of(2026, 7, 9, 14, 59));
        assertThat(savedRecord.getRemark()).contains("定投计划#3 2026-07-09");
        verify(investmentPlanMapper).updateById(planCaptor.capture());
        assertThat(planCaptor.getValue().getNextExecuteDate()).isEqualTo(nextTradingDay);
    }

    @Test
    void dueRegularInvestCompensationShouldAdvanceOverseasDailyPlanTwoTradingDays() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        InvestmentPlanMapper investmentPlanMapper = mock(InvestmentPlanMapper.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        LocalDate runDate = LocalDate.of(2026, 7, 9);
        LocalDate firstTradingDay = LocalDate.of(2026, 7, 10);
        LocalDate secondTradingDay = LocalDate.of(2026, 7, 13);
        InvestmentPlan plan = dailyInvestmentPlan(runDate, "012922", "Global Growth QDII");
        when(investmentPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(tradeRecordMapper.selectOne(any())).thenReturn(null);
        when(holdingMapper.selectOne(any())).thenReturn(null);
        when(tradingCalendarService.nextTradingDay(runDate)).thenReturn(firstTradingDay);
        when(tradingCalendarService.nextTradingDay(firstTradingDay)).thenReturn(secondTradingDay);
        TradeRecordServiceImpl service = new TradeRecordServiceImpl(
                tradeRecordMapper,
                holdingMapper,
                mock(PortfolioAccountMapper.class),
                mock(PortfolioAccountService.class),
                investmentPlanMapper,
                mock(FundQueryService.class),
                tradingCalendarService);
        ArgumentCaptor<InvestmentPlan> planCaptor = ArgumentCaptor.forClass(InvestmentPlan.class);

        SchedulerTaskResult result = service.createDueRegularInvestTrades(runDate);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isZero();
        verify(investmentPlanMapper).updateById(planCaptor.capture());
        assertThat(planCaptor.getValue().getNextExecuteDate()).isEqualTo(secondTradingDay);
    }

    @Test
    void completedSellShouldReduceCostByShareRatio() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        FundHolding holding = holding();
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.create(completedRequest("SELL", "240.00", "200.0000", "1.2000", "0"));
        }

        verify(holdingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getHoldingShare()).isEqualByComparingTo("800.0000");
        assertThat(saved.getHoldingCost()).isEqualByComparingTo("800.0000");
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("960.0000");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("160.0000");
    }

    @Test
    void completedSellAllShouldClearAmountCostAndProfit() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        FundHolding holding = holding();
        holding.setHoldingAmount(new BigDecimal("1200.0000"));
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.create(completedRequest("SELL", "800.00", "1000.0000", "0.8000", "0"));
        }

        verify(holdingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(saved.getHoldingShare()).isEqualByComparingTo("0.0000");
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("0.0000");
        assertThat(saved.getHoldingCost()).isEqualByComparingTo("0.0000");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("0.0000");
        assertThat(saved.getHoldingProfitRate()).isEqualByComparingTo("0.0000");
    }

    @Test
    void processingTradeShouldNotMutateHoldingOrRecalculateAccount() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(holdingMapper.selectOne(any())).thenReturn(holding());
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.create(request("BUY", "120.00", "100.0000", "1.2000", "0", com.lk.quantfund.enums.TradeStatus.PROCESSING));
        }

        verify(holdingMapper, never()).updateById(any(FundHolding.class));
        verify(accountService, never()).recalculateOwnedAccount(any(), any());
    }

    @Test
    void deleteProcessingTradeShouldRemovePendingRecordOnly() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        TradeRecord record = processingBuyRecord();
        when(tradeRecordMapper.selectOne(any())).thenReturn(record);
        TradeRecordServiceImpl service = service(
                tradeRecordMapper,
                mock(FundHoldingMapper.class),
                mock(PortfolioAccountMapper.class),
                mock(PortfolioAccountService.class));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.deleteProcessing(300L);
        }

        verify(tradeRecordMapper).deleteById(300L);
    }

    @Test
    void deleteProcessingTradeShouldRejectCompletedRecord() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        TradeRecord record = processingBuyRecord();
        record.setTradeStatus("COMPLETED");
        when(tradeRecordMapper.selectOne(any())).thenReturn(record);
        TradeRecordServiceImpl service = service(
                tradeRecordMapper,
                mock(FundHoldingMapper.class),
                mock(PortfolioAccountMapper.class),
                mock(PortfolioAccountService.class));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.deleteProcessing(300L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("only pending trade record can be deleted");
        }

        verify(tradeRecordMapper, never()).deleteById(Mockito.anyLong());
    }

    @Test
    void dueProcessingBuyShouldSettleWithOfficialNavBeforeIntradayEstimate() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        FundQueryService fundQueryService = mock(FundQueryService.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        FundHolding holding = holding();
        holding.setHoldingAmount(new BigDecimal("1343.1600"));
        holding.setHoldingShare(new BigDecimal("543.1500"));
        holding.setHoldingCost(new BigDecimal("1000.0000"));
        holding.setHoldingProfit(new BigDecimal("343.1600"));
        holding.setHoldingProfitRate(new BigDecimal("34.3200"));
        holding.setLatestOfficialNav(new BigDecimal("2.4729"));
        holding.setCurrentEstimateNav(new BigDecimal("2.4729"));
        TradeRecord record = processingBuyRecord();
        LocalDate tradeDate = LocalDate.of(2026, 6, 26);
        LocalDate settleDate = LocalDate.of(2026, 6, 29);
        when(tradeRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(tradingCalendarService.isTradingDay(tradeDate)).thenReturn(true);
        when(tradingCalendarService.nextTradingDay(tradeDate)).thenReturn(settleDate);
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                new FundNavPointDTO("016874", tradeDate, new BigDecimal("2.4729"), null, null, "TEST")
        ));
        TradeRecordServiceImpl service = new TradeRecordServiceImpl(
                tradeRecordMapper,
                holdingMapper,
                accountMapper,
                accountService,
                mock(InvestmentPlanMapper.class),
                fundQueryService,
                tradingCalendarService);
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        service.settleDueProcessingTrades(settleDate);

        verify(holdingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(record.getTradeStatus()).isEqualTo("COMPLETED");
        assertThat(record.getTradeShare()).isEqualByComparingTo("40.4384");
        assertThat(record.getTradeNav()).isEqualByComparingTo("2.4729");
        assertThat(saved.getHoldingShare()).isEqualByComparingTo("583.5884");
        assertThat(saved.getHoldingCost()).isEqualByComparingTo("1100.0000");
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("1443.1558");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("343.1558");
        assertThat(saved.getHoldingProfitRate()).isEqualByComparingTo("31.1960");
        assertThat(saved.getDailyProfit()).isEqualByComparingTo("0.0000");
        verify(accountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void overseasProcessingBuyShouldUseApplicationDateNavAndDelaySettlement() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        FundQueryService fundQueryService = mock(FundQueryService.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        FundHolding holding = holding();
        holding.setFundCode("012922");
        holding.setFundName("易方达全球成长精选混合(QDII)人民币C");
        TradeRecord record = processingBuyRecord();
        record.setFundCode("012922");
        record.setFundName("易方达全球成长精选混合(QDII)人民币C");
        record.setTradeTime(LocalDateTime.of(2026, 6, 25, 14, 59));
        LocalDate applicationDate = LocalDate.of(2026, 6, 25);
        LocalDate firstNextTradingDay = LocalDate.of(2026, 6, 26);
        LocalDate settleDate = LocalDate.of(2026, 6, 29);
        when(tradeRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(tradingCalendarService.isTradingDay(applicationDate)).thenReturn(true);
        when(tradingCalendarService.nextTradingDay(applicationDate)).thenReturn(firstNextTradingDay);
        when(tradingCalendarService.nextTradingDay(firstNextTradingDay)).thenReturn(settleDate);
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                new FundNavPointDTO("012922", applicationDate, new BigDecimal("5.1000"), null, null, "TEST"),
                new FundNavPointDTO("012922", firstNextTradingDay, new BigDecimal("5.2000"), null, null, "TEST")
        ));
        TradeRecordServiceImpl service = new TradeRecordServiceImpl(
                tradeRecordMapper,
                holdingMapper,
                accountMapper,
                accountService,
                mock(InvestmentPlanMapper.class),
                fundQueryService,
                tradingCalendarService);

        service.settleDueProcessingTrades(settleDate);

        assertThat(record.getTradeStatus()).isEqualTo("COMPLETED");
        assertThat(record.getTradeNav()).isEqualByComparingTo("5.1000");
        assertThat(record.getTradeShare()).isEqualByComparingTo("19.6078");
        verify(accountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void dueProcessingSellShouldRecalculateAmountWithOfficialNav() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        FundQueryService fundQueryService = mock(FundQueryService.class);
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        FundHolding holding = holding();
        holding.setHoldingAmount(new BigDecimal("1200.0000"));
        holding.setHoldingShare(new BigDecimal("1000.0000"));
        holding.setHoldingCost(new BigDecimal("1000.0000"));
        holding.setLatestOfficialNav(new BigDecimal("1.2000"));
        holding.setCurrentEstimateNav(new BigDecimal("1.2000"));
        TradeRecord record = processingBuyRecord();
        record.setTradeType("SELL");
        record.setTradeAmount(new BigDecimal("220.0000"));
        record.setTradeShare(new BigDecimal("200.0000"));
        record.setTradeNav(null);
        LocalDate tradeDate = LocalDate.of(2026, 6, 26);
        LocalDate settleDate = LocalDate.of(2026, 6, 29);
        when(tradeRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(tradingCalendarService.isTradingDay(tradeDate)).thenReturn(true);
        when(tradingCalendarService.nextTradingDay(tradeDate)).thenReturn(settleDate);
        when(fundQueryService.getHistoricalNav(any(), any(), any())).thenReturn(List.of(
                new FundNavPointDTO("016874", tradeDate, new BigDecimal("1.2500"), null, null, "TEST")
        ));
        TradeRecordServiceImpl service = new TradeRecordServiceImpl(
                tradeRecordMapper,
                holdingMapper,
                accountMapper,
                accountService,
                mock(InvestmentPlanMapper.class),
                fundQueryService,
                tradingCalendarService);
        ArgumentCaptor<FundHolding> holdingCaptor = ArgumentCaptor.forClass(FundHolding.class);

        service.settleDueProcessingTrades(settleDate);

        verify(holdingMapper).updateById(holdingCaptor.capture());
        FundHolding saved = holdingCaptor.getValue();
        assertThat(record.getTradeStatus()).isEqualTo("COMPLETED");
        assertThat(record.getTradeNav()).isEqualByComparingTo("1.2500");
        assertThat(record.getTradeShare()).isEqualByComparingTo("200.0000");
        assertThat(record.getTradeAmount()).isEqualByComparingTo("250.0000");
        assertThat(saved.getHoldingShare()).isEqualByComparingTo("800.0000");
        assertThat(saved.getHoldingCost()).isEqualByComparingTo("800.0000");
        assertThat(saved.getHoldingAmount()).isEqualByComparingTo("1000.0000");
        assertThat(saved.getHoldingProfit()).isEqualByComparingTo("200.0000");
        verify(accountService).adjustCashAmountOwnedAccount(
                1L,
                10L,
                new BigDecimal("250.0000")
        );
        verify(accountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void completedConvertInCanReferenceOwnedConvertOutTrade() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(tradeRecordMapper.selectOne(any())).thenReturn(convertOutRecord());
        when(holdingMapper.selectOne(any())).thenReturn(holding());
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);
        ArgumentCaptor<TradeRecord> recordCaptor = ArgumentCaptor.forClass(TradeRecord.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.create(request("CONVERT_IN", "800.00", "900.0000", "0.8889", "0", com.lk.quantfund.enums.TradeStatus.COMPLETED, 200L));
        }

        verify(tradeRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRelatedTradeId()).isEqualTo(200L);
        verify(accountService).recalculateOwnedAccount(1L, 10L);
    }

    @Test
    void nonConvertInShouldRejectRelatedTradeId() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.create(request("BUY", "120.00", "100.0000", "1.2000", "0", com.lk.quantfund.enums.TradeStatus.COMPLETED, 200L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("related trade is only allowed for convert-in trade");
        }
    }

    @Test
    void convertInShouldRejectMissingRelatedConvertOutTrade() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(tradeRecordMapper.selectOne(any())).thenReturn(null);
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.create(request("CONVERT_IN", "800.00", "900.0000", "0.8889", "0", com.lk.quantfund.enums.TradeStatus.COMPLETED, 404L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("related convert-out trade not found");
        }
    }

    @Test
    void completedConvertOutShouldRejectShareAboveHolding() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(holdingMapper.selectOne(any())).thenReturn(holding());
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.create(completedRequest("CONVERT_OUT", "1800.00", "1500.0000", "1.2000", "0")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("trade share cannot exceed holding share");
        }
    }

    @Test
    void completedBuyShouldRejectMissingShareAndNav() {
        TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        PortfolioAccountService accountService = mock(PortfolioAccountService.class);
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(holdingMapper.selectOne(any())).thenReturn(holding());
        TradeRecordServiceImpl service = service(tradeRecordMapper, holdingMapper, accountMapper, accountService);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.create(completedRequest("BUY", "120.00", null, null, "0")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("trade share or trade nav is required for buy or convert-in trade");
        }
    }

    private TradeRecordServiceImpl service(TradeRecordMapper tradeRecordMapper,
                                           FundHoldingMapper holdingMapper,
                                           PortfolioAccountMapper accountMapper,
                                           PortfolioAccountService accountService) {
        TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
        when(tradingCalendarService.isTradingDay(any())).thenReturn(true);
        when(tradingCalendarService.nextTradingDay(any())).thenAnswer(invocation ->
                ((java.time.LocalDate) invocation.getArgument(0)).plusDays(1));
        return new TradeRecordServiceImpl(
                tradeRecordMapper,
                holdingMapper,
                accountMapper,
                accountService,
                mock(InvestmentPlanMapper.class),
                mock(FundQueryService.class),
                tradingCalendarService);
    }

    private TradeRecordRequest completedRequest(String tradeType,
                                                String amount,
                                                String share,
                                                String nav,
                                                String fee) {
        return request(tradeType, amount, share, nav, fee, com.lk.quantfund.enums.TradeStatus.COMPLETED);
    }

    private TradeRecordRequest request(String tradeType,
                                       String amount,
                                       String share,
                                       String nav,
                                       String fee,
                                       com.lk.quantfund.enums.TradeStatus status) {
        return request(tradeType, amount, share, nav, fee, status, null);
    }

    private TradeRecordRequest request(String tradeType,
                                       String amount,
                                       String share,
                                       String nav,
                                       String fee,
                                       com.lk.quantfund.enums.TradeStatus status,
                                       Long relatedTradeId) {
        return request(tradeType, amount, share, nav, fee, status, relatedTradeId, null);
    }

    private TradeRecordRequest request(String tradeType,
                                       String amount,
                                       String share,
                                       String nav,
                                       String fee,
                                       com.lk.quantfund.enums.TradeStatus status,
                                       Long relatedTradeId,
                                       String remark) {
        return new TradeRecordRequest(
                10L,
                100L,
                "000001",
                "Mock Fund",
                com.lk.quantfund.enums.TradeType.valueOf(tradeType),
                status,
                new BigDecimal(amount),
                share == null ? null : new BigDecimal(share),
                nav == null ? null : new BigDecimal(nav),
                fee == null ? BigDecimal.ZERO : new BigDecimal(fee),
                null,
                relatedTradeId,
                remark
        );
    }

    private ConvertPairTradeRequest convertPairRequest() {
        return new ConvertPairTradeRequest(
                10L,
                100L,
                new BigDecimal("240.00"),
                new BigDecimal("200.0000"),
                new BigDecimal("1.2000"),
                BigDecimal.ZERO,
                101L,
                "000002",
                "Target Fund",
                new BigDecimal("240.00"),
                new BigDecimal("300.0000"),
                new BigDecimal("0.8000"),
                BigDecimal.ZERO,
                com.lk.quantfund.enums.TradeStatus.COMPLETED,
                null,
                null
        );
    }

    private FundHolding holding() {
        FundHolding holding = new FundHolding();
        holding.setId(100L);
        holding.setUserId(1L);
        holding.setAccountId(10L);
        holding.setFundCode("000001");
        holding.setFundName("Mock Fund");
        holding.setHoldingShare(new BigDecimal("1000.0000"));
        holding.setHoldingCost(new BigDecimal("1000.0000"));
        holding.setHoldingAmount(new BigDecimal("1000.0000"));
        holding.setCurrentEstimateNav(new BigDecimal("1.0000"));
        holding.setLatestOfficialNav(new BigDecimal("1.0000"));
        return holding;
    }

    private InvestmentPlan dailyInvestmentPlan(LocalDate nextExecuteDate) {
        return dailyInvestmentPlan(nextExecuteDate, "021528", "Mock Daily Plan Fund");
    }

    private InvestmentPlan dailyInvestmentPlan(LocalDate nextExecuteDate, String fundCode, String fundName) {
        InvestmentPlan plan = new InvestmentPlan();
        plan.setId(3L);
        plan.setUserId(1L);
        plan.setAccountId(10L);
        plan.setFundCode(fundCode);
        plan.setFundName(fundName);
        plan.setPlanName(fundName + " Regular Invest");
        plan.setPlanType("REGULAR_INVEST");
        plan.setAmount(new BigDecimal("20.0000"));
        plan.setFrequency("DAILY");
        plan.setNextExecuteDate(nextExecuteDate);
        plan.setStatus("ENABLED");
        plan.setDeleted(0);
        return plan;
    }

    private PortfolioAccount account() {
        PortfolioAccount account = new PortfolioAccount();
        account.setId(10L);
        account.setUserId(1L);
        return account;
    }

    private TradeRecord convertOutRecord() {
        TradeRecord record = new TradeRecord();
        record.setId(200L);
        record.setUserId(1L);
        record.setAccountId(10L);
        record.setTradeType("CONVERT_OUT");
        record.setTradeStatus("COMPLETED");
        return record;
    }

    private TradeRecord processingBuyRecord() {
        TradeRecord record = new TradeRecord();
        record.setId(300L);
        record.setUserId(1L);
        record.setAccountId(10L);
        record.setHoldingId(100L);
        record.setFundCode("016874");
        record.setFundName("广发远见智选混合C");
        record.setTradeType("BUY");
        record.setTradeStatus("PROCESSING");
        record.setTradeAmount(new BigDecimal("100.0000"));
        record.setTradeFee(BigDecimal.ZERO);
        record.setTradeTime(LocalDateTime.of(2026, 6, 26, 14, 59));
        record.setRemark(SystemConstants.SIMULATED_TRADE_NOTICE);
        return record;
    }

}
