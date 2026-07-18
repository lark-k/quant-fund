package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.datasource.FundDataSourceAdapter;
import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundPeerRankDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerFundUniverse;
import com.lk.quantfund.entity.ScreenerUniverseFilter;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerFundUniverseMapper;
import com.lk.quantfund.mapper.ScreenerUniverseFilterMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FundScreenerNavServiceImplTest {

    private final ScreenerUniverseFilterMapper filterMapper = mock(ScreenerUniverseFilterMapper.class);
    private final ScreenerFundUniverseMapper universeMapper = mock(ScreenerFundUniverseMapper.class);
    private final ScreenerFundNavDailyMapper navMapper = mock(ScreenerFundNavDailyMapper.class);
    private final FundScreenerFreshnessPolicy freshnessPolicy = mock(FundScreenerFreshnessPolicy.class);

    @Test
    void shouldSyncNavForIncludedFundsOnlyAndUpsertByCodeDate() {
        when(filterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(included("000001")));
        ScreenerFundNavDaily existing = new ScreenerFundNavDaily();
        existing.setId(9L);
        existing.setFundCode("000001");
        existing.setNavDate(LocalDate.of(2026, 7, 1));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(navMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, existing);
        FundScreenerNavServiceImpl service = service(List.of(adapter()));

        var result = service.syncNav();

        verify(navMapper).upsertBatch(argThat(entities ->
                entities.size() == 2
                        && "000001".equals(entities.getFirst().getFundCode())
                        && LocalDate.of(2026, 6, 30).equals(entities.getFirst().getNavDate())
                        && entities.get(1).getUnitNav().compareTo(new BigDecimal("1.0200")) == 0));
        verify(navMapper, never()).insert(any(ScreenerFundNavDaily.class));
        verify(navMapper, never()).updateById(any(ScreenerFundNavDaily.class));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.successCount()).isEqualTo(2);
    }

    @Test
    void shouldKeepOldNavWhenDatasourceFails() {
        when(filterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(included("000001")));
        FundDataSourceAdapter failing = mock(FundDataSourceAdapter.class);
        when(failing.enabled()).thenReturn(true);
        when(failing.priority()).thenReturn(1);
        when(failing.sourceName()).thenReturn("FAIL");
        when(failing.getHistoricalNav(any(), any(), any())).thenThrow(new IllegalStateException("timeout"));
        FundScreenerNavServiceImpl service = service(List.of(failing));

        var result = service.syncNav();

        verify(navMapper, never()).upsertBatch(any());
        verify(navMapper, never()).insert(any(ScreenerFundNavDaily.class));
        verify(navMapper, never()).updateById(any(ScreenerFundNavDaily.class));
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errorSummaries()).contains("000001: timeout");
    }

    @Test
    void shouldFallbackToSupportedUniverseWhenIncludedFilterIsEmpty() {
        when(filterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(universeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                universe("000001", "MIXED", "A", "NORMAL"),
                universe("000002", "INDEX", "C", "NORMAL"),
                universe("000003", "QDII", "A", "NORMAL"),
                universe("000004", "INDEX", "A", "NORMAL")
        ));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(navMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        FundScreenerNavServiceImpl service = service(List.of(adapter()));

        var result = service.syncNav();

        ArgumentCaptor<List<ScreenerFundNavDaily>> captor = ArgumentCaptor.captor();
        verify(navMapper, times(2)).upsertBatch(captor.capture());
        assertThat(captor.getAllValues().stream().flatMap(List::stream).toList()).extracting(ScreenerFundNavDaily::getFundCode)
                .containsOnly("000001", "000004");
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.successCount()).isEqualTo(4);
    }

    @Test
    void shouldFailWhenDatasourceReturnsEmptyAndStoredNavIsStale() {
        when(filterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(included("000001")));
        ScreenerFundNavDaily stale = new ScreenerFundNavDaily();
        stale.setFundCode("000001");
        stale.setNavDate(LocalDate.of(2026, 7, 8));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stale));
        FundDataSourceAdapter empty = mock(FundDataSourceAdapter.class);
        when(empty.enabled()).thenReturn(true);
        when(empty.priority()).thenReturn(1);
        when(empty.getHistoricalNav(any(), any(), any())).thenReturn(List.of());
        FundScreenerNavServiceImpl service = service(List.of(empty));
        when(freshnessPolicy.isFresh(LocalDate.of(2026, 7, 8))).thenReturn(false);
        when(freshnessPolicy.requiredNavDate()).thenReturn(LocalDate.of(2026, 7, 17));

        var result = service.syncNav();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errorSummaries()).anyMatch(message -> message.contains("2026-07-08") && message.contains("2026-07-17"));
        verify(navMapper, never()).upsertBatch(any());
    }

    private FundScreenerNavServiceImpl service(List<FundDataSourceAdapter> adapters) {
        when(freshnessPolicy.isFresh(any())).thenReturn(true);
        when(freshnessPolicy.requiredNavDate()).thenReturn(LocalDate.of(2026, 7, 1));
        return new FundScreenerNavServiceImpl(adapters, filterMapper, universeMapper, navMapper, freshnessPolicy);
    }

    private ScreenerUniverseFilter included(String fundCode) {
        ScreenerUniverseFilter filter = new ScreenerUniverseFilter();
        filter.setFundCode(fundCode);
        filter.setIncluded(1);
        filter.setUniverseType("MIXED");
        return filter;
    }

    private ScreenerFundUniverse universe(String fundCode, String fundType, String shareClass, String status) {
        ScreenerFundUniverse universe = new ScreenerFundUniverse();
        universe.setFundCode(fundCode);
        universe.setFundName("fund " + fundCode);
        universe.setFundType(fundType);
        universe.setShareClass(shareClass);
        universe.setStatus(status);
        return universe;
    }

    private FundDataSourceAdapter adapter() {
        return new FundDataSourceAdapter() {
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
                return List.of();
            }

            @Override
            public Optional<FundBasicInfoDTO> getBasicInfo(String fundCode) {
                return Optional.empty();
            }

            @Override
            public List<FundNavPointDTO> getHistoricalNav(String fundCode, LocalDate startDate, LocalDate endDate) {
                return List.of(
                        new FundNavPointDTO(fundCode, LocalDate.of(2026, 6, 30), new BigDecimal("1.0100"), new BigDecimal("1.0100"), new BigDecimal("1.0000"), sourceName()),
                        new FundNavPointDTO(fundCode, LocalDate.of(2026, 7, 1), new BigDecimal("1.0200"), new BigDecimal("1.0200"), new BigDecimal("0.9901"), sourceName())
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
        };
    }
}
