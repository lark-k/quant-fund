package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.dto.quant.QuantAnalyzeRequest;
import com.lk.quantfund.dto.quant.QuantAnalyzeResponse;
import com.lk.quantfund.dto.quant.QuantNavPointDTO;
import com.lk.quantfund.dto.quant.QuantScoreDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.QuantSignal;
import com.lk.quantfund.entity.RiskProfile;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.QuantSignalMapper;
import com.lk.quantfund.mapper.RiskProfileMapper;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.quant.QuantEngineClient;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class QuantAnalysisServiceImplTest {

    private final QuantEngineClient quantEngineClient = mock(QuantEngineClient.class);
    private final FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
    private final PortfolioAccountMapper portfolioAccountMapper = mock(PortfolioAccountMapper.class);
    private final RiskProfileMapper riskProfileMapper = mock(RiskProfileMapper.class);
    private final FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
    private final TradeRecordMapper tradeRecordMapper = mock(TradeRecordMapper.class);
    private final QuantSignalMapper quantSignalMapper = mock(QuantSignalMapper.class);
    private final StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
    private final TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
    private final FundQueryService fundQueryService = mock(FundQueryService.class);
    private final MarketDataService marketDataService = mock(MarketDataService.class);
    private final QuantAnalysisServiceImpl service = new QuantAnalysisServiceImpl(
            quantEngineClient,
            new QuantFundProperties(),
            new ObjectMapper(),
            fundHoldingMapper,
            portfolioAccountMapper,
            riskProfileMapper,
            fundNavDailyMapper,
            tradeRecordMapper,
            quantSignalMapper,
            strategySignalMapper,
            tradingCalendarService,
            fundQueryService,
            marketDataService
    );

    @BeforeEach
    void setUp() {
        initTable(FundHolding.class);
        initTable(FundNavDaily.class);
        initTable(PortfolioAccount.class);
        initTable(RiskProfile.class);
        initTable(QuantSignal.class);
    }

    @Test
    void analyzeHoldingShouldAttachMarketFactorsForLatestLgbmFeatures() {
        when(fundHoldingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(holding());
        when(portfolioAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account());
        when(riskProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(riskProfile());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav(LocalDate.of(2026, 1, 2), "1.0000"),
                nav(LocalDate.of(2026, 1, 5), "1.0300")
        ));
        when(tradeRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(quantSignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(quantSignalMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(strategySignalMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(tradingCalendarService.isIntradayEstimateWindow(any())).thenReturn(false);
        when(tradingCalendarService.isTradingDay(any(LocalDate.class))).thenReturn(true);
        when(marketDataService.historicalIndex(any(), any(), any())).thenAnswer(invocation -> {
            String indexCode = invocation.getArgument(0);
            LocalDate startDate = invocation.getArgument(1);
            LocalDate endDate = invocation.getArgument(2);
            return history(indexCode, startDate, endDate, closeFor(indexCode));
        });
        when(quantEngineClient.analyze(any())).thenAnswer(invocation -> {
            QuantAnalyzeRequest request = invocation.getArgument(0);
            return new QuantAnalyzeResponse(
                    request.requestId(),
                    request.holding().fundCode(),
                    request.holding().holdingId(),
                    "BUY",
                    "建议持有观察",
                    BigDecimal.ZERO,
                    new BigDecimal("20.0000"),
                    new BigDecimal("0.8000"),
                    "MEDIUM",
                    new QuantScoreDTO(
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000")
                    ),
                    Map.of("mlAvailable", true, "mlModelVersion", "lgbm-v1.4.1-broad-market"),
                    List.of(),
                    List.of(),
                    "QuantRuleEngine",
                    "rule-v1.39.0",
                    null,
                    "test"
            );
        });
        ArgumentCaptor<QuantAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(QuantAnalyzeRequest.class);
        ArgumentCaptor<QuantSignal> signalCaptor = ArgumentCaptor.forClass(QuantSignal.class);

        service.analyzeHoldingForUser(1L, 100L);

        Mockito.verify(quantEngineClient).analyze(requestCaptor.capture());
        Mockito.verify(quantSignalMapper).insert(signalCaptor.capture());
        QuantNavPointDTO latest = requestCaptor.getValue().navSeries().get(1);
        assertThat(latest.indexCode()).isEqualTo("HSTECH");
        assertThat(latest.indexReturnRate()).isEqualByComparingTo("9.0000");
        assertThat(latest.marketHs300ReturnRate()).isEqualByComparingTo("4.0000");
        assertThat(latest.marketZz500ReturnRate()).isEqualByComparingTo("5.0000");
        assertThat(requestCaptor.getValue().strategyState().weakTrendCandidateDays()).isZero();
        assertThat(requestCaptor.getValue().strategyState().weakTrendDefenseHandled()).isFalse();
        assertThat(signalCaptor.getValue().getSuggestAmount()).isEqualByComparingTo("1000.0000");
    }

    @Test
    void analyzeHoldingShouldAppendFreshIntradayEstimateAsTemporaryNavPoint() {
        stubAnalyzeHolding(List.of());
        LocalDateTime observedAt = LocalDateTime.now().minusMinutes(1);
        when(tradingCalendarService.isIntradayEstimateWindow(any())).thenReturn(true);
        when(fundQueryService.getIntradayEstimate("013403", false)).thenReturn(new FundEstimateDTO(
                "013403", "Intraday Fund", new BigDecimal("0.9500"), new BigDecimal("-5.0000"),
                observedAt.toLocalDate(), observedAt, "TEST_ESTIMATE", false, "{}"
        ));
        ArgumentCaptor<QuantAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(QuantAnalyzeRequest.class);

        service.analyzeHoldingForUser(1L, 100L);

        Mockito.verify(quantEngineClient).analyze(requestCaptor.capture());
        QuantAnalyzeRequest request = requestCaptor.getValue();
        QuantNavPointDTO latest = request.navSeries().get(request.navSeries().size() - 1);
        assertThat(request.navSeries()).hasSize(3);
        assertThat(latest.date()).isEqualTo(LocalDate.now());
        assertThat(latest.nav()).isEqualByComparingTo("0.9500");
        assertThat(latest.dailyGrowthRate()).isEqualByComparingTo("-5.0000");
        assertThat(latest.estimated()).isTrue();
        assertThat(latest.observedAt()).isEqualTo(observedAt);
        assertThat(latest.navSource()).isEqualTo("TEST_ESTIMATE");
        assertThat(request.holding().currentEstimateGrowthRate()).isEqualByComparingTo("-5.0000");
    }

    @Test
    void analyzeHoldingShouldRejectStaleIntradayEstimate() {
        stubAnalyzeHolding(List.of());
        LocalDateTime observedAt = LocalDateTime.now().minusMinutes(6);
        when(tradingCalendarService.isIntradayEstimateWindow(any())).thenReturn(true);
        when(fundQueryService.getIntradayEstimate("013403", false)).thenReturn(new FundEstimateDTO(
                "013403", "Stale Fund", new BigDecimal("0.9500"), new BigDecimal("-5.0000"),
                observedAt.toLocalDate(), observedAt, "TEST_ESTIMATE", false, "{}"
        ));
        ArgumentCaptor<QuantAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(QuantAnalyzeRequest.class);

        service.analyzeHoldingForUser(1L, 100L);

        Mockito.verify(quantEngineClient).analyze(requestCaptor.capture());
        QuantAnalyzeRequest request = requestCaptor.getValue();
        assertThat(request.navSeries()).hasSize(2);
        assertThat(request.navSeries().get(1).estimated()).isFalse();
        assertThat(request.holding().currentEstimateNav()).isNull();
        assertThat(request.holding().currentEstimateGrowthRate()).isEqualByComparingTo("0.0000");
        Mockito.verify(fundHoldingMapper, Mockito.never()).updateById(any(FundHolding.class));
    }

    @Test
    void analyzeHoldingShouldPreferOfficialNavWhenTodayIsAlreadyPublished() {
        stubAnalyzeHolding(List.of());
        LocalDateTime observedAt = LocalDateTime.now().minusMinutes(1);
        when(tradingCalendarService.isIntradayEstimateWindow(any())).thenReturn(true);
        when(fundQueryService.getIntradayEstimate("013403", false)).thenReturn(new FundEstimateDTO(
                "013403", "Official Today Fund", new BigDecimal("0.9500"), new BigDecimal("-5.0000"),
                observedAt.toLocalDate(), observedAt, "TEST_ESTIMATE", false, "{}"
        ));
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav(LocalDate.now().minusDays(1), "1.0000"),
                nav(LocalDate.now(), "0.9700")
        ));
        ArgumentCaptor<QuantAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(QuantAnalyzeRequest.class);

        service.analyzeHoldingForUser(1L, 100L);

        Mockito.verify(quantEngineClient).analyze(requestCaptor.capture());
        QuantAnalyzeRequest request = requestCaptor.getValue();
        assertThat(request.navSeries()).hasSize(2);
        assertThat(request.navSeries().get(1).estimated()).isFalse();
        assertThat(request.navSeries().get(1).nav()).isEqualByComparingTo("0.9700");
        assertThat(request.holding().currentEstimateNav()).isNull();
        assertThat(request.holding().currentEstimateGrowthRate()).isEqualByComparingTo("0.0000");
    }

    @Test
    void analyzeHoldingShouldRestoreLegacyDefenseState() {
        QuantSignal legacySignal = new QuantSignal();
        legacySignal.setTradeDate(LocalDate.now().minusDays(1));
        legacySignal.setAction("SELL");
        legacySignal.setSuggestRatio(new BigDecimal("15.0000"));

        when(fundHoldingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(holding());
        when(portfolioAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account());
        when(riskProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(riskProfile());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav(LocalDate.of(2026, 1, 2), "1.0000"),
                nav(LocalDate.of(2026, 1, 5), "1.0300")
        ));
        when(tradeRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(quantSignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(legacySignal));
        when(quantSignalMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(strategySignalMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(tradingCalendarService.isIntradayEstimateWindow(any())).thenReturn(false);
        when(tradingCalendarService.isTradingDay(any(LocalDate.class))).thenReturn(true);
        when(marketDataService.historicalIndex(any(), any(), any())).thenAnswer(invocation -> {
            String indexCode = invocation.getArgument(0);
            LocalDate startDate = invocation.getArgument(1);
            LocalDate endDate = invocation.getArgument(2);
            return history(indexCode, startDate, endDate, closeFor(indexCode));
        });
        when(quantEngineClient.analyze(any())).thenAnswer(invocation -> {
            QuantAnalyzeRequest request = invocation.getArgument(0);
            return new QuantAnalyzeResponse(
                    request.requestId(),
                    request.holding().fundCode(),
                    request.holding().holdingId(),
                    "HOLD",
                    "hold",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("0.8000"),
                    "MEDIUM",
                    new QuantScoreDTO(
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"),
                            new BigDecimal("60.0000")
                    ),
                    Map.of(),
                    List.of(),
                    List.of(),
                    "QuantRuleEngine",
                    "rule-v1.39.0",
                    null,
                    "test"
            );
        });
        ArgumentCaptor<QuantAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(QuantAnalyzeRequest.class);

        service.analyzeHoldingForUser(1L, 100L);

        Mockito.verify(quantEngineClient).analyze(requestCaptor.capture());
        assertThat(requestCaptor.getValue().strategyState().weakTrendCandidateDays()).isEqualTo(4);
        assertThat(requestCaptor.getValue().strategyState().weakTrendDefenseHandled()).isTrue();
        assertThat(requestCaptor.getValue().strategyState().weakTrendCooldownDays()).isEqualTo(45);
        assertThat(requestCaptor.getValue().strategyState().weakRecoveryRequired()).isTrue();
        assertThat(requestCaptor.getValue().strategyState().lastDefenseDate())
                .isEqualTo(legacySignal.getTradeDate().toString());
    }

    @Test
    void analyzeHoldingShouldNotAdvanceExtremeStageWithoutCompletedReduction() {
        LocalDate signalDate = LocalDate.now().minusDays(1);
        QuantSignal previousSignal = new QuantSignal();
        previousSignal.setTradeDate(signalDate);
        previousSignal.setSignalTime(signalDate.atTime(14, 30));
        previousSignal.setAction("SELL");
        previousSignal.setMetricsJson("""
                {
                  "decisionReason": "extreme_risk_exit",
                  "weakTrendCandidateDaysAfter": 2,
                  "weakTrendDefenseHandledAfter": true,
                  "weakTrendCooldownDaysAfter": 41,
                  "positionRebalanceCooldownDaysAfter": 17,
                  "weakRecoveryRequiredAfter": true,
                  "extremeRiskStageAfter": 1,
                  "lastExtremeRiskDateAfter": "%s",
                  "lastExtremeDrawdownAfter": 24.5,
                  "lastActionDateAfter": "%s",
                  "extremeRiskSellCountAfter": 1,
                  "lastDefenseDateAfter": "%s"
                }
                """.formatted(signalDate, signalDate, signalDate));
        TradeRecord processingSell = new TradeRecord();
        processingSell.setTradeType("SELL");
        processingSell.setTradeStatus("PROCESSING");
        processingSell.setTradeAmount(new BigDecimal("500.0000"));
        processingSell.setTradeTime(signalDate.atTime(14, 45));
        processingSell.setCreateTime(signalDate.atTime(14, 46));
        stubAnalyzeHolding(List.of(previousSignal), List.of(processingSell));
        ArgumentCaptor<QuantAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(QuantAnalyzeRequest.class);

        service.analyzeHoldingForUser(1L, 100L);

        Mockito.verify(quantEngineClient).analyze(requestCaptor.capture());
        var state = requestCaptor.getValue().strategyState();
        assertThat(state.weakTrendCandidateDays()).isEqualTo(2);
        assertThat(state.weakTrendCooldownDays()).isEqualTo(41);
        assertThat(state.positionRebalanceCooldownDays()).isEqualTo(17);
        assertThat(state.extremeRiskStage()).isZero();
        assertThat(state.lastExtremeRiskDate()).isNull();
        assertThat(state.lastExtremeDrawdown()).isNull();
        assertThat(state.lastActionDate()).isEqualTo(signalDate.toString());
    }

    @Test
    void analyzeHoldingShouldAdvanceExtremeStageAfterCompletedReduction() {
        LocalDate signalDate = LocalDate.now().minusDays(1);
        QuantSignal previousSignal = new QuantSignal();
        previousSignal.setTradeDate(signalDate);
        previousSignal.setSignalTime(signalDate.atTime(14, 30));
        previousSignal.setAction("SELL");
        previousSignal.setMetricsJson("""
                {
                  "decisionReason": "extreme_risk_exit",
                  "weakTrendCandidateDaysAfter": 2,
                  "weakTrendDefenseHandledAfter": true,
                  "weakTrendCooldownDaysAfter": 41,
                  "positionRebalanceCooldownDaysAfter": 0,
                  "weakRecoveryRequiredAfter": true,
                  "extremeRiskStageAfter": 1,
                  "lastExtremeRiskDateAfter": "%s",
                  "lastExtremeDrawdownAfter": 24.5,
                  "lastActionDateAfter": "%s",
                  "extremeRiskSellCountAfter": 1,
                  "lastDefenseDateAfter": "%s"
                }
                """.formatted(signalDate, signalDate, signalDate));
        TradeRecord completedSell = new TradeRecord();
        completedSell.setTradeType("SELL");
        completedSell.setTradeStatus("COMPLETED");
        completedSell.setTradeAmount(new BigDecimal("500.0000"));
        completedSell.setTradeShare(new BigDecimal("500.0000"));
        completedSell.setTradeNav(BigDecimal.ONE);
        completedSell.setTradeTime(signalDate.atTime(14, 45));
        completedSell.setCreateTime(signalDate.atTime(14, 46));
        stubAnalyzeHolding(List.of(previousSignal), List.of(completedSell));
        ArgumentCaptor<QuantAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(QuantAnalyzeRequest.class);

        service.analyzeHoldingForUser(1L, 100L);

        Mockito.verify(quantEngineClient).analyze(requestCaptor.capture());
        var state = requestCaptor.getValue().strategyState();
        assertThat(state.extremeRiskStage()).isEqualTo(1);
        assertThat(state.lastExtremeRiskDate()).isEqualTo(signalDate.toString());
        assertThat(state.lastExtremeDrawdown()).isEqualByComparingTo("24.5");
    }

    private void stubAnalyzeHolding(List<QuantSignal> previousSignals) {
        stubAnalyzeHolding(previousSignals, List.of());
    }

    private void stubAnalyzeHolding(List<QuantSignal> previousSignals, List<TradeRecord> trades) {
        when(fundHoldingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(holding());
        when(portfolioAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account());
        when(riskProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(riskProfile());
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav(LocalDate.of(2026, 1, 2), "1.0000"),
                nav(LocalDate.of(2026, 1, 5), "1.0300")
        ));
        when(tradeRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(trades);
        when(quantSignalMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(previousSignals);
        when(quantSignalMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(strategySignalMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(tradingCalendarService.isIntradayEstimateWindow(any())).thenReturn(false);
        when(tradingCalendarService.isTradingDay(any(LocalDate.class))).thenReturn(true);
        when(marketDataService.historicalIndex(any(), any(), any())).thenAnswer(invocation -> {
            String indexCode = invocation.getArgument(0);
            LocalDate startDate = invocation.getArgument(1);
            LocalDate endDate = invocation.getArgument(2);
            return history(indexCode, startDate, endDate, closeFor(indexCode));
        });
        when(quantEngineClient.analyze(any())).thenAnswer(invocation -> {
            QuantAnalyzeRequest request = invocation.getArgument(0);
            return new QuantAnalyzeResponse(
                    request.requestId(), request.holding().fundCode(), request.holding().holdingId(),
                    "WATCH", "建议重点观察", BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("0.8000"), "HIGH",
                    new QuantScoreDTO(
                            new BigDecimal("60.0000"), new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"), new BigDecimal("60.0000"),
                            new BigDecimal("60.0000"), new BigDecimal("60.0000")
                    ),
                    Map.of(), List.of(), List.of(), "QuantRuleEngine", "rule-v1.39.0", null, "test"
            );
        });
    }

    private void initTable(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }

    private FundHolding holding() {
        FundHolding holding = new FundHolding();
        holding.setId(100L);
        holding.setUserId(1L);
        holding.setAccountId(10L);
        holding.setFundCode("013403");
        holding.setFundName("华夏恒生科技ETF发起式联接");
        holding.setFundType("QDII");
        holding.setHoldingAmount(new BigDecimal("1000.0000"));
        holding.setHoldingShare(new BigDecimal("1000.0000"));
        holding.setHoldingCost(new BigDecimal("900.0000"));
        holding.setHoldingProfit(new BigDecimal("100.0000"));
        holding.setHoldingProfitRate(new BigDecimal("11.1111"));
        holding.setDailyProfit(BigDecimal.ZERO);
        holding.setHoldingDays(120);
        holding.setActiveFund(1);
        return holding;
    }

    private PortfolioAccount account() {
        PortfolioAccount account = new PortfolioAccount();
        account.setId(10L);
        account.setUserId(1L);
        account.setTotalAsset(new BigDecimal("5000.0000"));
        account.setTotalInvestAmount(new BigDecimal("4500.0000"));
        account.setCurrentProfit(new BigDecimal("500.0000"));
        account.setCurrentProfitRate(new BigDecimal("11.1111"));
        account.setDailyProfit(BigDecimal.ZERO);
        account.setEquityPositionRate(new BigDecimal("80.0000"));
        account.setMaxSingleFundPositionRate(new BigDecimal("25.0000"));
        return account;
    }

    private RiskProfile riskProfile() {
        RiskProfile profile = new RiskProfile();
        profile.setUserId(1L);
        profile.setRiskLevel("MEDIUM");
        profile.setMaxEquityPositionRate(new BigDecimal("70.0000"));
        profile.setMaxSingleFundPositionRate(new BigDecimal("25.0000"));
        profile.setDrawdownAlertRate(new BigDecimal("8.0000"));
        profile.setDailyRiseAlertRate(new BigDecimal("2.0000"));
        profile.setDailyFallAlertRate(new BigDecimal("2.0000"));
        return profile;
    }

    private FundNavDaily nav(LocalDate date, String unitNav) {
        FundNavDaily nav = new FundNavDaily();
        nav.setFundCode("013403");
        nav.setNavDate(date);
        nav.setUnitNav(new BigDecimal(unitNav));
        nav.setAccumulatedNav(new BigDecimal(unitNav));
        nav.setDailyGrowthRate(BigDecimal.ZERO);
        return nav;
    }

    private List<MarketIndexDailyVO> history(String indexCode, LocalDate startDate, LocalDate endDate, BigDecimal latestClose) {
        return List.of(
                new MarketIndexDailyVO(indexCode, indexCode, startDate, new BigDecimal("100.0000"), BigDecimal.ZERO, "TEST"),
                new MarketIndexDailyVO(indexCode, indexCode, endDate, latestClose, BigDecimal.ZERO, "TEST")
        );
    }

    private BigDecimal closeFor(String indexCode) {
        return switch (indexCode) {
            case "000001" -> new BigDecimal("101.0000");
            case "399001" -> new BigDecimal("102.0000");
            case "399006" -> new BigDecimal("103.0000");
            case "000300" -> new BigDecimal("104.0000");
            case "000905" -> new BigDecimal("105.0000");
            case "HSTECH" -> new BigDecimal("109.0000");
            default -> new BigDecimal("100.0000");
        };
    }
}
