package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.datasource.FundUniverseDataSourceAdapter;
import com.lk.quantfund.datasource.model.MarketFundDTO;
import com.lk.quantfund.entity.ScreenerFundUniverse;
import com.lk.quantfund.entity.ScreenerUniverseFilter;
import com.lk.quantfund.mapper.ScreenerFundUniverseMapper;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerUniverseFilterMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FundUniverseServiceImplTest {

    private final ScreenerFundUniverseMapper mapper = mock(ScreenerFundUniverseMapper.class);
    private final ScreenerUniverseFilterMapper filterMapper = mock(ScreenerUniverseFilterMapper.class);
    private final ScreenerFundNavDailyMapper navMapper = mock(ScreenerFundNavDailyMapper.class);

    @Test
    void shouldInsertNewMarketFundsFromFirstAvailableAdapter() {
        FundUniverseDataSourceAdapter adapter = adapter(List.of(marketFund("000001", "华夏成长混合A", "混合型-灵活")));
        FundUniverseServiceImpl service = service(List.of(adapter));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        var result = service.syncUniverse();

        ArgumentCaptor<ScreenerFundUniverse> captor = ArgumentCaptor.forClass(ScreenerFundUniverse.class);
        verify(mapper).insert(captor.capture());
        ScreenerFundUniverse saved = captor.getValue();
        assertThat(saved.getFundCode()).isEqualTo("000001");
        assertThat(saved.getFundType()).isEqualTo("MIXED");
        assertThat(saved.getShareClass()).isEqualTo("A");
        assertThat(saved.getStatus()).isEqualTo("NORMAL");
        assertThat(saved.getDeleted()).isZero();
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void shouldUpdateExistingMarketFundIdempotently() {
        ScreenerFundUniverse existing = new ScreenerFundUniverse();
        existing.setId(8L);
        existing.setFundCode("510300");
        existing.setCreateTime(java.time.LocalDateTime.now().minusDays(1));
        FundUniverseServiceImpl service = service(List.of(adapter(List.of(marketFund("510300", "沪深300ETF", "指数型-股票")))));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        var result = service.syncUniverse();

        ArgumentCaptor<ScreenerFundUniverse> captor = ArgumentCaptor.forClass(ScreenerFundUniverse.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(8L);
        assertThat(captor.getValue().getFundType()).isEqualTo("INDEX");
        assertThat(captor.getValue().getFundName()).isEqualTo("沪深300ETF");
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void shouldPreserveExistingProfileWhenMarketListHasNoProfileFields() {
        ScreenerFundUniverse existing = new ScreenerFundUniverse();
        existing.setId(9L);
        existing.setFundCode("006976");
        existing.setCompanyName("鹏华基金");
        existing.setManagerName("黄奕松");
        existing.setEstablishDate(LocalDate.of(2019, 4, 3));
        existing.setFundSize(new BigDecimal("1.5408"));
        existing.setCreateTime(java.time.LocalDateTime.now().minusDays(1));
        MarketFundDTO marketListFund = new MarketFundDTO(
                "006976",
                "鹏华核心优势混合A",
                "混合型-偏股",
                "A",
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                "NORMAL",
                "TEST"
        );
        FundUniverseServiceImpl service = service(List.of(adapter(List.of(marketListFund))));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.syncUniverse();

        ArgumentCaptor<ScreenerFundUniverse> captor = ArgumentCaptor.forClass(ScreenerFundUniverse.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getCompanyName()).isEqualTo("鹏华基金");
        assertThat(captor.getValue().getManagerName()).isEqualTo("黄奕松");
        assertThat(captor.getValue().getEstablishDate()).isEqualTo(LocalDate.of(2019, 4, 3));
        assertThat(captor.getValue().getFundSize()).isEqualByComparingTo("1.5408");
    }

    @Test
    void shouldKeepExistingDataWhenDatasourceFails() {
        FundUniverseDataSourceAdapter failing = mock(FundUniverseDataSourceAdapter.class);
        when(failing.enabled()).thenReturn(true);
        when(failing.priority()).thenReturn(1);
        when(failing.sourceName()).thenReturn("FAIL");
        when(failing.listAllFunds()).thenThrow(new IllegalStateException("remote unavailable"));
        FundUniverseServiceImpl service = service(List.of(failing));

        var result = service.syncUniverse();

        verify(mapper, never()).insert(any(ScreenerFundUniverse.class));
        verify(mapper, never()).updateById(any(ScreenerFundUniverse.class));
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errorSummaries()).contains("FAIL: remote unavailable");
    }

    @Test
    void shouldRebuildUniverseFilterWithReasons() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                universe("000001", "MIXED", "A", "NORMAL"),
                universe("000002", "MIXED", "C", "NORMAL"),
                universe("000003", "MONEY", "A", "NORMAL"),
                universe("000004", "INDEX", "A", "TERMINATED"),
                universe("000005", "ACTIVE_EQUITY", "A", "NORMAL")
        ));
        when(navMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(250L, 250L, 250L, 250L, 20L);
        when(filterMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        var result = service(List.of()).rebuildUniverse();

        ArgumentCaptor<ScreenerUniverseFilter> captor = ArgumentCaptor.forClass(ScreenerUniverseFilter.class);
        verify(filterMapper, org.mockito.Mockito.times(5)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(item -> item.getFundCode().equals("000001"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getIncluded()).isEqualTo(1);
                    assertThat(item.getUniverseType()).isEqualTo("MIXED");
                    assertThat(item.getExcludeReason()).isNull();
                });
        assertThat(captor.getAllValues())
                .filteredOn(item -> item.getFundCode().equals("000002"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getIncluded()).isZero();
                    assertThat(item.getExcludeReason()).contains("重复份额");
                });
        assertThat(captor.getAllValues())
                .filteredOn(item -> item.getFundCode().equals("000003"))
                .singleElement()
                .satisfies(item -> assertThat(item.getExcludeReason()).contains("暂不支持"));
        assertThat(captor.getAllValues())
                .filteredOn(item -> item.getFundCode().equals("000004"))
                .singleElement()
                .satisfies(item -> assertThat(item.getExcludeReason()).contains("状态异常"));
        assertThat(captor.getAllValues())
                .filteredOn(item -> item.getFundCode().equals("000005"))
                .singleElement()
                .satisfies(item -> assertThat(item.getExcludeReason()).contains("净值样本不足"));
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(4);
    }

    @Test
    void shouldEnrichIncludedFundProfileBeforeRebuildFilter() {
        ScreenerFundUniverse missingProfile = universe("006976", "MIXED", "A", "NORMAL");
        missingProfile.setCompanyName(null);
        missingProfile.setManagerName(null);
        missingProfile.setEstablishDate(null);
        missingProfile.setFundSize(null);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(missingProfile));
        when(navMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(250L, 250L);
        when(filterMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        FundUniverseDataSourceAdapter adapter = adapter(List.of());
        when(adapter.getFundProfile("006976")).thenReturn(java.util.Optional.of(new MarketFundDTO(
                "006976",
                "鹏华核心优势混合A",
                "混合型-偏股",
                "A",
                null,
                "鹏华基金",
                "黄奕松",
                LocalDate.of(2019, 4, 3),
                new BigDecimal("1.5408"),
                null,
                true,
                "4",
                "NORMAL",
                "TEST"
        )));

        service(List.of(adapter)).rebuildUniverse();

        ArgumentCaptor<ScreenerFundUniverse> universeCaptor = ArgumentCaptor.forClass(ScreenerFundUniverse.class);
        verify(mapper).updateById(universeCaptor.capture());
        assertThat(universeCaptor.getValue().getCompanyName()).isEqualTo("鹏华基金");
        assertThat(universeCaptor.getValue().getManagerName()).isEqualTo("黄奕松");
        assertThat(universeCaptor.getValue().getEstablishDate()).isEqualTo(LocalDate.of(2019, 4, 3));
        assertThat(universeCaptor.getValue().getFundSize()).isEqualByComparingTo("1.5408");

        ArgumentCaptor<ScreenerUniverseFilter> filterCaptor = ArgumentCaptor.forClass(ScreenerUniverseFilter.class);
        verify(filterMapper).insert(filterCaptor.capture());
        assertThat(filterCaptor.getValue().getSizeFilterPassed()).isEqualTo(1);
        verify(navMapper, times(2)).selectCount(any(LambdaQueryWrapper.class));
    }

    private FundUniverseDataSourceAdapter adapter(List<MarketFundDTO> funds) {
        FundUniverseDataSourceAdapter adapter = mock(FundUniverseDataSourceAdapter.class);
        when(adapter.enabled()).thenReturn(true);
        when(adapter.priority()).thenReturn(10);
        when(adapter.sourceName()).thenReturn("TEST");
        when(adapter.listAllFunds()).thenReturn(funds);
        return adapter;
    }

    private FundUniverseServiceImpl service(List<FundUniverseDataSourceAdapter> adapters) {
        return new FundUniverseServiceImpl(adapters, mapper, filterMapper, navMapper);
    }

    private ScreenerFundUniverse universe(String code, String type, String shareClass, String status) {
        ScreenerFundUniverse universe = new ScreenerFundUniverse();
        universe.setFundCode(code);
        universe.setFundName("示例基金" + code);
        universe.setFundType(type);
        universe.setShareClass(shareClass);
        universe.setStatus(status);
        universe.setFundSize(new BigDecimal("12.0000"));
        return universe;
    }

    private MarketFundDTO marketFund(String code, String name, String type) {
        return new MarketFundDTO(
                code,
                name,
                type,
                null,
                null,
                "示例基金公司",
                "基金经理",
                LocalDate.of(2020, 1, 1),
                new BigDecimal("42.5000"),
                null,
                true,
                "HIGH",
                "NORMAL",
                "TEST"
        );
    }
}
