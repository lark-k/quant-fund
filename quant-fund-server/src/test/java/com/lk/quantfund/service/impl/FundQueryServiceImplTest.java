package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import com.lk.quantfund.scheduler.TradingCalendarService;
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

    private FundQueryServiceImpl serviceWithResults() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return new FundQueryServiceImpl(
                List.of(new TestFundDataSourceAdapter()),
                redisTemplate,
                new ObjectMapper(),
                new QuantFundProperties(),
                null,
                null,
                null,
                new TradingCalendarService(new QuantFundProperties())
        );
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
            return List.of();
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
