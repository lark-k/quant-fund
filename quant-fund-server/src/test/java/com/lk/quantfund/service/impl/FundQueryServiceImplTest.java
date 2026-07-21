package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.FundDataSourceAdapter;
import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundPeerRankDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
import com.lk.quantfund.entity.FundInfo;
import com.lk.quantfund.entity.FundEstimateIntraday;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundInfoMapper;
import com.lk.quantfund.mapper.FundEstimateIntradayMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class FundQueryServiceImplTest {

    @Test
    void shouldKeepAllDatasourceResultsForFuzzySearch() {
        FundQueryServiceImpl service = serviceWithResults();

        List<FundSearchResultDTO> results = service.search("白酒", "FUZZY");

        assertThat(results)
                .extracting(FundSearchResultDTO::fundCode)
                .containsExactly("161725", "000001", "110022");
    }

    @Test
    void shouldFilterExactSearchByCodeNameOrPinyin() {
        FundQueryServiceImpl service = serviceWithResults();

        assertThat(service.search("161725", "EXACT"))
                .extracting(FundSearchResultDTO::fundName)
                .containsExactly("招商中证白酒指数A");
        assertThat(service.search("华夏成长混合", "EXACT"))
                .extracting(FundSearchResultDTO::fundCode)
                .containsExactly("000001");
        assertThat(service.search("YFDXFHYGP", "EXACT"))
                .extracting(FundSearchResultDTO::fundCode)
                .containsExactly("110022");
    }

    @Test
    void shouldDefaultUnknownSearchModeToFuzzy() {
        FundQueryServiceImpl service = serviceWithResults();

        List<FundSearchResultDTO> results = service.search("白酒", "unexpected");

        assertThat(results).hasSize(3);
    }

    @Test
    void shouldRejectBlankSearchKeywordWithReadableMessage() {
        FundQueryServiceImpl service = serviceWithResults();

        assertThatThrownBy(() -> service.search(" ", "FUZZY"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("基金搜索关键词不能为空");
    }

    @Test
    void shouldAttachMatchedIndexReturnRateToHistoricalNav() {
        StringRedisTemplate redisTemplate = redisTemplate();
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        when(fundInfoMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(fundInfo("中证白酒"));
        when(marketDataService.historicalIndex("399997", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 25)))
                .thenReturn(List.of(
                        indexPoint(LocalDate.of(2026, 6, 24), "4000.0000"),
                        indexPoint(LocalDate.of(2026, 6, 25), "4040.0000")
                ));
        FundQueryServiceImpl service = new FundQueryServiceImpl(
                List.of(new TestFundDataSourceAdapter()),
                redisTemplate,
                new ObjectMapper(),
                new QuantFundProperties(),
                fundInfoMapper,
                fundNavDailyMapper,
                null,
                new TradingCalendarService(new QuantFundProperties()),
                marketDataService
        );

        List<FundNavPointDTO> nav = service.getHistoricalNav("510300", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 25));

        assertThat(nav).hasSize(2);
        assertThat(nav.get(0).indexReturnRate()).isEqualByComparingTo("0.0000");
        assertThat(nav.get(1).indexReturnRate()).isEqualByComparingTo("1.0000");
        assertThat(nav.get(1).indexCode()).isEqualTo("399997");
        assertThat(nav.get(1).indexName()).isEqualTo("中证白酒");
        verify(marketDataService).historicalIndex("399997", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 25));
    }

    @Test
    void shouldMatchNasdaqIndexForQdiiHistoricalNav() {
        StringRedisTemplate redisTemplate = redisTemplate();
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        when(fundInfoMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(fundInfo("纳斯达克100"));
        when(marketDataService.historicalIndex("NDX", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 25)))
                .thenReturn(List.of(
                        indexPoint(LocalDate.of(2026, 6, 24), "20000.0000"),
                        indexPoint(LocalDate.of(2026, 6, 25), "20200.0000")
                ));
        FundQueryServiceImpl service = new FundQueryServiceImpl(
                List.of(new TestFundDataSourceAdapter()),
                redisTemplate,
                new ObjectMapper(),
                new QuantFundProperties(),
                fundInfoMapper,
                fundNavDailyMapper,
                null,
                new TradingCalendarService(new QuantFundProperties()),
                marketDataService
        );

        List<FundNavPointDTO> nav = service.getHistoricalNav("270042", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 25));

        assertThat(nav.get(1).indexCode()).isEqualTo("NDX");
        assertThat(nav.get(1).indexName()).isEqualTo("纳斯达克");
        assertThat(nav.get(1).indexReturnRate()).isEqualByComparingTo("1.0000");
        verify(marketDataService).historicalIndex("NDX", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 25));
    }

    @Test
    void shouldBuildIntradayEstimateFromHeavyStockThemesWhenProviderHasNoEstimate() {
        FundDataSourceAdapter adapter = mock(FundDataSourceAdapter.class);
        when(adapter.priority()).thenReturn(1);
        when(adapter.enabled()).thenReturn(true);
        when(adapter.sourceName()).thenReturn("TEST");
        when(adapter.getIntradayEstimate("021528")).thenReturn(Optional.empty());
        when(adapter.getRelatedThemes("021528")).thenReturn(List.of(
                new FundThemeDTO("021528", "PCB", "HEAVY_STOCK_WEIGHTED",
                        new BigDecimal("40.0000"), new BigDecimal("2.0000"), "TEST"),
                new FundThemeDTO("021528", "CPO", "HEAVY_STOCK_WEIGHTED",
                        new BigDecimal("30.0000"), new BigDecimal("1.0000"), "TEST")
        ));
        FundNavDailyMapper navMapper = mock(FundNavDailyMapper.class);
        FundNavDaily nav = new FundNavDaily();
        nav.setFundCode("021528");
        nav.setNavDate(LocalDate.now().minusDays(1));
        nav.setUnitNav(new BigDecimal("1.0000"));
        when(navMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(nav);
        FundInfoMapper infoMapper = mock(FundInfoMapper.class);
        FundInfo info = new FundInfo();
        info.setFundCode("021528");
        info.setFundName("Active Fund");
        when(infoMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(info);
        FundEstimateIntradayMapper estimateMapper = mock(FundEstimateIntradayMapper.class);
        TradingCalendarService calendar = mock(TradingCalendarService.class);
        when(calendar.isIntradayEstimateDisplayWindow(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        FundQueryServiceImpl service = new FundQueryServiceImpl(
                List.of(adapter), redisTemplate(), new ObjectMapper(), new QuantFundProperties(),
                infoMapper, navMapper, estimateMapper, calendar, mock(MarketDataService.class));

        FundEstimateDTO estimate = service.getIntradayEstimate("021528", false);

        assertThat(estimate.sourceName()).isEqualTo("HEAVY_STOCK_WEIGHTED");
        assertThat(estimate.estimateGrowthRate()).isEqualByComparingTo("1.5714");
        assertThat(estimate.estimateNav()).isEqualByComparingTo("1.0157");
        verify(estimateMapper).insert(org.mockito.ArgumentMatchers.<FundEstimateIntraday>any());
    }


    private FundQueryServiceImpl serviceWithResults() {
        StringRedisTemplate redisTemplate = redisTemplate();
        return new FundQueryServiceImpl(
                List.of(new TestFundDataSourceAdapter()),
                redisTemplate,
                new ObjectMapper(),
                new QuantFundProperties(),
                null,
                null,
                null,
                new TradingCalendarService(new QuantFundProperties()),
                mock(MarketDataService.class)
        );
    }

    private StringRedisTemplate redisTemplate() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return redisTemplate;
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

    private FundInfo fundInfo(String trackingIndex) {
        FundInfo info = new FundInfo();
        info.setFundCode("510300");
        info.setFundName("招商中证白酒指数A");
        info.setFundType("INDEX");
        info.setTrackingIndex(trackingIndex);
        return info;
    }

    private static class TestFundDataSourceAdapter implements FundDataSourceAdapter {

        @Override
        public String sourceName() {
            return "TEST";
        }

        @Override
        public int priority() {
            return 1;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public List<FundSearchResultDTO> searchFunds(String keyword) {
            return List.of(
                    new FundSearchResultDTO("161725", "招商中证白酒指数A", "INDEX", "ZSZZBJZSA", sourceName()),
                    new FundSearchResultDTO("000001", "华夏成长混合", "MIXED", "HXCC", sourceName()),
                    new FundSearchResultDTO("110022", "易方达消费行业股票", "ACTIVE_EQUITY", "YFDXFHYGP", sourceName())
            );
        }

        @Override
        public Optional<FundBasicInfoDTO> getBasicInfo(String fundCode) {
            return Optional.empty();
        }

        @Override
        public List<FundNavPointDTO> getHistoricalNav(String fundCode, LocalDate startDate, LocalDate endDate) {
            return List.of(
                    new FundNavPointDTO(fundCode, LocalDate.of(2026, 6, 24), new BigDecimal("1.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO, sourceName()),
                    new FundNavPointDTO(fundCode, LocalDate.of(2026, 6, 25), new BigDecimal("1.0200"), new BigDecimal("1.0200"), new BigDecimal("2.0000"), sourceName())
            );
        }

        @Override
        public Optional<FundEstimateDTO> getIntradayEstimate(String fundCode) {
            return Optional.empty();
        }

        @Override
        public List<FundStockHoldingDTO> getHeavyStocks(String fundCode) {
            return List.of();
        }

        @Override
        public List<FundThemeDTO> getRelatedThemes(String fundCode) {
            return List.of();
        }

        @Override
        public Optional<FundPeerRankDTO> getPeerRank(String fundCode) {
            return Optional.empty();
        }
    }
}
