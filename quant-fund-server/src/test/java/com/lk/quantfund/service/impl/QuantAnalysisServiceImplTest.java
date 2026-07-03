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
import com.lk.quantfund.dto.quant.QuantAnalyzeRequest;
import com.lk.quantfund.dto.quant.QuantAnalyzeResponse;
import com.lk.quantfund.dto.quant.QuantNavPointDTO;
import com.lk.quantfund.dto.quant.QuantScoreDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.QuantSignal;
import com.lk.quantfund.entity.RiskProfile;
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
                    "建议持有观察",
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
                    Map.of("mlAvailable", true, "mlModelVersion", "lgbm-v1.4.1-broad-market"),
                    List.of(),
                    List.of(),
                    "QuantRuleEngine",
                    "rule-v1.36.0",
                    null,
                    "test"
            );
        });
        ArgumentCaptor<QuantAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(QuantAnalyzeRequest.class);

        service.analyzeHoldingForUser(1L, 100L);

        Mockito.verify(quantEngineClient).analyze(requestCaptor.capture());
        QuantNavPointDTO latest = requestCaptor.getValue().navSeries().get(1);
        assertThat(latest.indexCode()).isEqualTo("HSTECH");
        assertThat(latest.indexReturnRate()).isEqualByComparingTo("9.0000");
        assertThat(latest.marketHs300ReturnRate()).isEqualByComparingTo("4.0000");
        assertThat(latest.marketZz500ReturnRate()).isEqualByComparingTo("5.0000");
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
