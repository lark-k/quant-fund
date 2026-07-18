package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.entity.ScreenerFactorSnapshot;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerFundUniverse;
import com.lk.quantfund.entity.ScreenerUniverseFilter;
import com.lk.quantfund.mapper.ScreenerFactorSnapshotMapper;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerFundUniverseMapper;
import com.lk.quantfund.mapper.ScreenerUniverseFilterMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FundFactorServiceImplTest {

    private final ScreenerUniverseFilterMapper filterMapper = mock(ScreenerUniverseFilterMapper.class);
    private final ScreenerFundNavDailyMapper navMapper = mock(ScreenerFundNavDailyMapper.class);
    private final ScreenerFactorSnapshotMapper factorMapper = mock(ScreenerFactorSnapshotMapper.class);
    private final ScreenerFundUniverseMapper universeMapper = mock(ScreenerFundUniverseMapper.class);
    private final FundScreenerFreshnessPolicy freshnessPolicy = mock(FundScreenerFreshnessPolicy.class);

    @Test
    void shouldCalculateFactorSnapshotFromHistoricalNavOnly() {
        when(filterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(included("000001")));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(navPoints());
        when(universeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(universe());
        when(factorMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        FundFactorServiceImpl service = service();

        var result = service.refreshFactors();

        ArgumentCaptor<ScreenerFactorSnapshot> captor = ArgumentCaptor.forClass(ScreenerFactorSnapshot.class);
        verify(factorMapper).insert(captor.capture());
        ScreenerFactorSnapshot snapshot = captor.getValue();
        assertThat(snapshot.getFundCode()).isEqualTo("000001");
        assertThat(snapshot.getFactorDate()).isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(snapshot.getReturn20d()).isEqualByComparingTo("40.0000");
        assertThat(snapshot.getReturn250d()).isEqualByComparingTo("40.0000");
        assertThat(snapshot.getMaxDrawdown60d()).isEqualByComparingTo("-10.0000");
        assertThat(snapshot.getNavSampleSize()).isEqualTo(21);
        assertThat(snapshot.getFundSize()).isEqualByComparingTo("25.0000");
        assertThat(snapshot.getBenchmarkCode()).isEqualTo("000300");
        assertThat(snapshot.getReturnDrawdownRatio120d()).isEqualByComparingTo("4.0000");
        assertThat(snapshot.getReturnConsistencyScore()).isEqualByComparingTo("100.0000");
        assertThat(snapshot.getFundAgeYears()).isBetween(new BigDecimal("6.5000"), new BigDecimal("6.6000"));
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void shouldCalculatePeerPercentileWithinSameUniverseType() {
        when(filterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                included("000001", "MIXED"),
                included("000002", "MIXED"),
                included("000003", "MIXED"),
                included("000004", "INDEX")
        ));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                navPoints("000001", "1.0000", "1.3000"),
                navPoints("000002", "1.0000", "1.1000"),
                navPoints("000003", "1.0000", "1.2000"),
                navPoints("000004", "1.0000", "1.0500")
        );
        when(universeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(universe());
        when(factorMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        FundFactorServiceImpl service = service();

        var result = service.refreshFactors();

        ArgumentCaptor<ScreenerFactorSnapshot> captor = ArgumentCaptor.forClass(ScreenerFactorSnapshot.class);
        verify(factorMapper, org.mockito.Mockito.times(4)).insert(captor.capture());
        List<ScreenerFactorSnapshot> snapshots = captor.getAllValues();
        assertThat(peerPercentile(snapshots, "000001")).isEqualByComparingTo("33.3333");
        assertThat(peerPercentile(snapshots, "000003")).isEqualByComparingTo("66.6667");
        assertThat(peerPercentile(snapshots, "000002")).isEqualByComparingTo("100.0000");
        assertThat(peerPercentile(snapshots, "000004")).isEqualByComparingTo("100.0000");
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldUseNaturalTwelveMonthWindowForOneYearReturn() {
        when(filterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(included("000001")));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                navPoint("000001", LocalDate.of(2025, 7, 20), "1.0000"),
                navPoint("000001", LocalDate.of(2025, 7, 22), "1.1000"),
                navPoint("000001", LocalDate.of(2026, 7, 21), "2.0000")
        ));
        when(universeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(universe());
        when(factorMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service().refreshFactors();

        ArgumentCaptor<ScreenerFactorSnapshot> captor = ArgumentCaptor.forClass(ScreenerFactorSnapshot.class);
        verify(factorMapper).insert(captor.capture());
        assertThat(captor.getValue().getReturn250d()).isEqualByComparingTo("81.8182");
        assertThat(captor.getValue().getAnnualReturn250d()).isEqualByComparingTo("81.8182");
    }

    @Test
    void shouldPersistOnlyLatestCommonFactorDateAndSkipOlderFunds() {
        when(filterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                included("000001", "MIXED"),
                included("000002", "MIXED")
        ));
        List<ScreenerFundNavDaily> current = navPoints("000001", "1.0000", "1.3000");
        List<ScreenerFundNavDaily> stale = navPoints("000002", "1.0000", "1.2000");
        stale.removeLast();
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(current, stale);
        when(universeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(universe());
        when(factorMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        var result = service().refreshFactors();

        ArgumentCaptor<ScreenerFactorSnapshot> captor = ArgumentCaptor.forClass(ScreenerFactorSnapshot.class);
        verify(factorMapper).insert(captor.capture());
        assertThat(captor.getValue().getFundCode()).isEqualTo("000001");
        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
    }

    private FundFactorServiceImpl service() {
        when(freshnessPolicy.isFresh(any())).thenReturn(true);
        when(freshnessPolicy.requiredNavDate()).thenReturn(LocalDate.of(2026, 7, 21));
        return new FundFactorServiceImpl(filterMapper, navMapper, factorMapper, universeMapper, freshnessPolicy);
    }

    private ScreenerUniverseFilter included(String fundCode) {
        return included(fundCode, "MIXED");
    }

    private ScreenerUniverseFilter included(String fundCode, String universeType) {
        ScreenerUniverseFilter filter = new ScreenerUniverseFilter();
        filter.setFundCode(fundCode);
        filter.setIncluded(1);
        filter.setUniverseType(universeType);
        return filter;
    }

    private ScreenerFundUniverse universe() {
        ScreenerFundUniverse universe = new ScreenerFundUniverse();
        universe.setFundCode("000001");
        universe.setFundType("MIXED");
        universe.setFundSize(new BigDecimal("25.0000"));
        universe.setEstablishDate(LocalDate.of(2020, 1, 1));
        return universe;
    }

    private List<ScreenerFundNavDaily> navPoints() {
        List<ScreenerFundNavDaily> points = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 7, 1);
        BigDecimal[] navs = {
                new BigDecimal("1.0000"), new BigDecimal("1.0500"), new BigDecimal("1.1000"), new BigDecimal("1.2000"),
                new BigDecimal("1.1500"), new BigDecimal("1.0800"), new BigDecimal("1.1200"), new BigDecimal("1.1800"),
                new BigDecimal("1.2000"), new BigDecimal("1.2200"), new BigDecimal("1.2500"), new BigDecimal("1.2600"),
                new BigDecimal("1.2700"), new BigDecimal("1.2800"), new BigDecimal("1.3000"), new BigDecimal("1.3200"),
                new BigDecimal("1.3300"), new BigDecimal("1.3500"), new BigDecimal("1.3600"), new BigDecimal("1.3800"),
                new BigDecimal("1.4000")
        };
        for (int index = 0; index < navs.length; index++) {
            ScreenerFundNavDaily point = new ScreenerFundNavDaily();
            point.setFundCode("000001");
            point.setNavDate(start.plusDays(index));
            point.setUnitNav(navs[index]);
            point.setAccumulatedNav(navs[index]);
            points.add(point);
        }
        return points;
    }

    private List<ScreenerFundNavDaily> navPoints(String fundCode, String firstNav, String lastNav) {
        List<ScreenerFundNavDaily> points = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 7, 1);
        BigDecimal first = new BigDecimal(firstNav);
        BigDecimal last = new BigDecimal(lastNav);
        for (int index = 0; index < 21; index++) {
            BigDecimal unitNav = index == 0 ? first : last;
            ScreenerFundNavDaily point = new ScreenerFundNavDaily();
            point.setFundCode(fundCode);
            point.setNavDate(start.plusDays(index));
            point.setUnitNav(unitNav);
            point.setAccumulatedNav(unitNav);
            points.add(point);
        }
        return points;
    }

    private ScreenerFundNavDaily navPoint(String fundCode, LocalDate date, String nav) {
        ScreenerFundNavDaily point = new ScreenerFundNavDaily();
        point.setFundCode(fundCode);
        point.setNavDate(date);
        point.setUnitNav(new BigDecimal(nav));
        point.setAccumulatedNav(new BigDecimal(nav));
        return point;
    }

    private BigDecimal peerPercentile(List<ScreenerFactorSnapshot> snapshots, String fundCode) {
        return snapshots.stream()
                .filter(snapshot -> fundCode.equals(snapshot.getFundCode()))
                .findFirst()
                .orElseThrow()
                .getPeerPercentile();
    }
}
