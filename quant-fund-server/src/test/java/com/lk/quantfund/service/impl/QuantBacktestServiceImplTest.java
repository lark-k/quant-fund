package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.RunRequest;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.TrainingSampleExportRequest;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.TrainingSampleExportResponse;
import com.lk.quantfund.dto.quant.QuantNavPointDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.QuantBacktestResultMapper;
import com.lk.quantfund.quant.QuantEngineClient;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class QuantBacktestServiceImplTest {

    private final QuantEngineClient quantEngineClient = mock(QuantEngineClient.class);
    private final FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
    private final FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
    private final MarketDataService marketDataService = mock(MarketDataService.class);
    private final QuantBacktestServiceImpl service = new QuantBacktestServiceImpl(
            quantEngineClient,
            new QuantFundProperties(),
            new ObjectMapper(),
            mock(PortfolioAccountMapper.class),
            fundHoldingMapper,
            fundNavDailyMapper,
            mock(QuantBacktestResultMapper.class),
            mock(FundQueryService.class),
            marketDataService
    );

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(FundHolding.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FundHolding.class);
        }
        if (TableInfoHelper.getTableInfo(FundNavDaily.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FundNavDaily.class);
        }
    }

    @Test
    void exportTrainingSamplesShouldAttachTrackingAndMarketIndexFactors() {
        when(fundHoldingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(holding()));
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav("013403", LocalDate.of(2026, 1, 2), "1.0000"),
                nav("013403", LocalDate.of(2026, 1, 5), "1.0300")
        ));
        when(marketDataService.historicalIndex(any(), any(), any())).thenAnswer(invocation -> {
            String indexCode = invocation.getArgument(0);
            LocalDate startDate = invocation.getArgument(1);
            return history(indexCode, startDate, LocalDate.of(2026, 1, 5), closeFor(indexCode));
        });
        when(quantEngineClient.exportTrainingSamples(any())).thenReturn(new TrainingSampleExportResponse(
                "samples.csv",
                2,
                1,
                1,
                1,
                List.of("return120d"),
                null,
                "fundCode,return120d\n013403,0\n"
        ));
        ArgumentCaptor<TrainingSampleExportRequest> requestCaptor = ArgumentCaptor.forClass(TrainingSampleExportRequest.class);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(1L);
            service.exportTrainingSamples(new RunRequest(
                    null,
                    List.of("013403"),
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 5),
                    null,
                    null,
                    null,
                    null
            ));
        }

        Mockito.verify(quantEngineClient).exportTrainingSamples(requestCaptor.capture());
        QuantNavPointDTO latest = requestCaptor.getValue().funds().get(0).navSeries().get(1);
        assertThat(latest.indexCode()).isEqualTo("HSTECH");
        assertThat(latest.indexName()).isEqualTo("恒生科技");
        assertThat(latest.indexReturnRate()).isEqualByComparingTo("9.0000");
        assertThat(latest.marketSh000001ReturnRate()).isEqualByComparingTo("1.0000");
        assertThat(latest.marketSz399001ReturnRate()).isEqualByComparingTo("2.0000");
        assertThat(latest.marketCyb399006ReturnRate()).isEqualByComparingTo("3.0000");
        assertThat(latest.marketHs300ReturnRate()).isEqualByComparingTo("4.0000");
        assertThat(latest.marketZz500ReturnRate()).isEqualByComparingTo("5.0000");
    }

    private FundHolding holding() {
        FundHolding holding = new FundHolding();
        holding.setUserId(1L);
        holding.setFundCode("013403");
        holding.setFundName("华夏恒生科技ETF联接");
        holding.setFundType("QDII");
        holding.setHoldingAmount(new BigDecimal("1000.0000"));
        return holding;
    }

    private FundNavDaily nav(String fundCode, LocalDate date, String unitNav) {
        FundNavDaily nav = new FundNavDaily();
        nav.setFundCode(fundCode);
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
