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
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.service.PortfolioAccountService;
import java.math.BigDecimal;
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
                mock(PortfolioAccountService.class)
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
        return new TradeRecordServiceImpl(tradeRecordMapper, holdingMapper, accountMapper, accountService);
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
}
