package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.ScreenerBacktestResult;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerQualityScore;
import com.lk.quantfund.mapper.ScreenerBacktestResultMapper;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerQualityScoreMapper;
import com.lk.quantfund.vo.screener.FundScreenerBacktestMetricVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FundScreenerBacktestServiceImplTest {

    private final ScreenerQualityScoreMapper scoreMapper = mock(ScreenerQualityScoreMapper.class);
    private final ScreenerFundNavDailyMapper navMapper = mock(ScreenerFundNavDailyMapper.class);
    private final ScreenerBacktestResultMapper resultMapper = mock(ScreenerBacktestResultMapper.class);
    private final QuantFundProperties properties = new QuantFundProperties();
    private final FundScreenerBacktestServiceImpl service = new FundScreenerBacktestServiceImpl(
            scoreMapper, navMapper, resultMapper, properties
    );

    @Test
    void shouldPersistAllBucketsAtExactForwardHorizons() {
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                score("000001", "92.0000", "WATCH"),
                score("000002", "84.0000", "WATCH"),
                score("000003", "64.0000", "NEUTRAL"),
                score("000004", "42.0000", "AVOID")
        ));
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                navSeries("000001", 120, "1.0000", "1.1200"),
                navSeries("000002", 120, "1.0000", "1.0800"),
                navSeries("000003", 120, "1.0000", "1.0200"),
                navSeries("000004", 120, "1.0000", "0.9400")
        );

        var result = service.runIncremental();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.successCount()).isEqualTo(15);
        assertThat(result.failureCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        ArgumentCaptor<ScreenerBacktestResult> captor = ArgumentCaptor.forClass(ScreenerBacktestResult.class);
        verify(resultMapper, times(15)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(ScreenerBacktestResult::getHorizonDays)
                .containsOnly(20, 60, 120);
        assertThat(captor.getAllValues()).extracting(ScreenerBacktestResult::getBucketName)
                .contains("TOP_5", "TOP_10", "WATCH", "NEUTRAL", "AVOID");
        assertThat(captor.getAllValues())
                .filteredOn(row -> row.getBucketName().equals("WATCH") && row.getHorizonDays() == 120)
                .singleElement().extracting(ScreenerBacktestResult::getSampleCount).isEqualTo(2);
    }

    @Test
    void shouldTreatMissingFutureWindowAsEligibilityMiss() {
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                score("000001", "92.0000", "WATCH"),
                score("000002", "64.0000", "NEUTRAL")
        ));
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                navSeries("000001", 60, "1.0000", "1.0600"),
                navSeries("000002", 19, "1.0000", "1.0100")
        );

        var result = service.runIncremental();

        assertThat(result.failureCount()).isZero();
        assertThat(result.successCount()).isEqualTo(6);
        ArgumentCaptor<ScreenerBacktestResult> captor = ArgumentCaptor.forClass(ScreenerBacktestResult.class);
        verify(resultMapper, times(6)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(ScreenerBacktestResult::getHorizonDays)
                .containsOnly(20, 60);
    }

    @Test
    void shouldSkipExistingCachedUnitWithoutDuplicateInsert() {
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                score("000001", "92.0000", "WATCH"),
                score("000002", "64.0000", "NEUTRAL"),
                score("000003", "42.0000", "AVOID")
        ));
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                cached("2026-01-02", 20, "TOP_5", 5, "3.0000", "55.0000", "1.0000", "-2.0000")
        ));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                navSeries("000001", 120, "1.0000", "1.1000"),
                navSeries("000002", 120, "1.0000", "1.0200"),
                navSeries("000003", 120, "1.0000", "0.9600")
        );

        var result = service.runIncremental();

        assertThat(result.successCount()).isEqualTo(14);
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(resultMapper, times(14)).insert(any(ScreenerBacktestResult.class));
    }

    @Test
    void shouldIgnoreScoresFromOtherModelVersions() {
        ScreenerQualityScore oldModel = score("000000", "99.0000", "WATCH");
        oldModel.setModelVersion("screener-rule-v1");
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                oldModel,
                score("000001", "72.0000", "NEUTRAL")
        ));
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                navSeries("000000", 120, "1.0000", "1.5000"),
                navSeries("000001", 120, "1.0000", "1.0500")
        );

        var result = service.runIncremental();

        assertThat(result.successCount()).isEqualTo(9);
        verify(navMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldContinueOtherObservationsWhenOneNavLookupFails() {
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                score("000001", "92.0000", "WATCH"),
                score("000002", "64.0000", "NEUTRAL")
        ));
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(navMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenThrow(new IllegalStateException("nav database unavailable"))
                .thenReturn(navSeries("000002", 120, "1.0000", "1.0500"))
                .thenThrow(new IllegalStateException("nav database unavailable"))
                .thenThrow(new IllegalStateException("nav database unavailable"));

        var result = service.runIncremental();

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(3);
        assertThat(result.errorSummaries()).anyMatch(message -> message.contains("nav database unavailable"));
    }

    @Test
    void shouldAggregateCachedRowsAndMarkEffectiveStrategy() {
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(validationRows(
                new BigDecimal("6.0000"), new BigDecimal("2.0000"), new BigDecimal("-1.0000"),
                new BigDecimal("2.0000")
        ));

        var validation = service.getValidation();

        assertThat(validation.status()).isEqualTo("EFFECTIVE");
        assertThat(validation.earliestScoreDate()).isEqualTo("2026-01-02");
        assertThat(validation.latestScoreDate()).isEqualTo("2026-01-04");
        assertThat(validation.latestRunDate()).isEqualTo("2026-07-10");
        assertThat(validation.conclusion()).contains("TOP_10").contains("60日");
        assertThat(validation.calibrationAdvice()).isNotEmpty();
        assertThat(validation.policy().strongMinScore()).isEqualByComparingTo("82.0000");
        FundScreenerBacktestMetricVO top10 = metric(validation.metrics(), "TOP_10", 60);
        assertThat(top10.sampleCount()).isEqualTo(30);
        assertThat(top10.scoreDateCount()).isEqualTo(3);
        assertThat(top10.avgForwardReturn()).isEqualTo(6.0);
        assertThat(top10.statisticallySignificant()).isTrue();
    }

    @Test
    void shouldReturnLatestScoreLookbackMetricsEvenWhenForwardRowsAreEmpty() {
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                score("000001", "92.0000", "WATCH"),
                score("000002", "84.0000", "WATCH"),
                score("000003", "64.0000", "NEUTRAL"),
                score("000004", "42.0000", "AVOID")
        ));
        List<ScreenerFundNavDaily> lookbackNavs = new ArrayList<>();
        lookbackNavs.addAll(lookbackNavSeries("000001", "1.0000", "1.1200"));
        lookbackNavs.addAll(lookbackNavSeries("000002", "1.0000", "1.0800"));
        lookbackNavs.addAll(lookbackNavSeries("000003", "1.0000", "1.0200"));
        lookbackNavs.addAll(lookbackNavSeries("000004", "1.0000", "0.9400"));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(lookbackNavs);

        var validation = service.getValidation();

        assertThat(validation.status()).isEqualTo("INSUFFICIENT");
        assertThat(validation.metrics()).isEmpty();
        FundScreenerBacktestMetricVO top10 = metric(validation.lookbackMetrics(), "TOP_10", 120);
        assertThat(top10.sampleCount()).isEqualTo(1);
        assertThat(top10.scoreDateCount()).isEqualTo(1);
        assertThat(top10.avgForwardReturn()).isEqualTo(12.0);
        assertThat(top10.avgExcessReturn()).isGreaterThan(0);
        assertThat(top10.statisticallySignificant()).isFalse();
    }

    @Test
    void shouldReturnInsufficientWhenCachedSamplesAreTooSmall() {
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                cached("2026-01-02", 60, "TOP_10", 10, "5.0000", "60.0000", "2.0000", "-3.0000"),
                cached("2026-01-02", 60, "NEUTRAL", 10, "2.0000", "52.0000", "-1.0000", "-4.0000"),
                cached("2026-01-02", 60, "AVOID", 10, "-1.0000", "40.0000", "-4.0000", "-7.0000")
        ));

        var validation = service.getValidation();

        assertThat(validation.status()).isEqualTo("INSUFFICIENT");
        assertThat(metric(validation.metrics(), "TOP_10", 60).statisticallySignificant()).isFalse();
        assertThat(validation.conclusion()).contains("样本不足");
    }

    @Test
    void shouldDistinguishNeutralAndFailedStrategy() {
        when(resultMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(validationRows(new BigDecimal("3.0000"), new BigDecimal("2.0000"),
                                new BigDecimal("1.0000"), BigDecimal.ZERO))
                .thenReturn(validationRows(new BigDecimal("-1.0000"), new BigDecimal("-2.0000"),
                                new BigDecimal("1.0000"), new BigDecimal("-2.0000")));

        assertThat(service.getValidation().status()).isEqualTo("NEUTRAL");
        assertThat(service.getValidation().status()).isEqualTo("FAILED");
    }

    private List<ScreenerBacktestResult> validationRows(BigDecimal top10Return, BigDecimal neutralReturn,
                                                         BigDecimal avoidReturn, BigDecimal top10Excess) {
        List<ScreenerBacktestResult> rows = new ArrayList<>();
        for (int day = 2; day <= 4; day++) {
            String date = "2026-01-0" + day;
            rows.add(cached(date, 60, "TOP_10", 10, top10Return.toPlainString(), "60.0000",
                    top10Excess.toPlainString(), "-3.0000"));
            rows.add(cached(date, 60, "NEUTRAL", 10, neutralReturn.toPlainString(), "52.0000",
                    "-1.0000", "-4.0000"));
            rows.add(cached(date, 60, "AVOID", 10, avoidReturn.toPlainString(), "42.0000",
                    "-3.0000", "-8.0000"));
        }
        return rows;
    }

    private FundScreenerBacktestMetricVO metric(List<FundScreenerBacktestMetricVO> metrics,
                                                 String bucket, int horizon) {
        return metrics.stream()
                .filter(item -> item.bucketName().equals(bucket) && item.horizonDays() == horizon)
                .findFirst()
                .orElseThrow();
    }

    private ScreenerQualityScore score(String fundCode, String qualityScore, String recommendLevel) {
        ScreenerQualityScore score = new ScreenerQualityScore();
        score.setFundCode(fundCode);
        score.setScoreDate(LocalDate.of(2026, 1, 2));
        score.setQualityScore(new BigDecimal(qualityScore));
        score.setRecommendLevel(recommendLevel);
        score.setModelVersion("screener-rule-v2");
        return score;
    }

    private ScreenerFundNavDaily nav(String fundCode, LocalDate navDate, BigDecimal unitNav) {
        ScreenerFundNavDaily nav = new ScreenerFundNavDaily();
        nav.setFundCode(fundCode);
        nav.setNavDate(navDate);
        nav.setUnitNav(unitNav);
        return nav;
    }

    private List<ScreenerFundNavDaily> navSeries(String fundCode, int futureCount,
                                                  String baseNav, String lastNav) {
        List<ScreenerFundNavDaily> points = new ArrayList<>();
        BigDecimal base = new BigDecimal(baseNav);
        BigDecimal last = new BigDecimal(lastNav);
        points.add(nav(fundCode, LocalDate.of(2026, 1, 2), base));
        for (int index = 1; index <= futureCount; index++) {
            BigDecimal progress = BigDecimal.valueOf(index)
                    .divide(BigDecimal.valueOf(futureCount), 8, java.math.RoundingMode.HALF_UP);
            BigDecimal value = base.add(last.subtract(base).multiply(progress));
            points.add(nav(fundCode, LocalDate.of(2026, 1, 2).plusDays(index), value));
        }
        return points;
    }

    private List<ScreenerFundNavDaily> lookbackNavSeries(String fundCode, String firstNav, String latestNav) {
        List<ScreenerFundNavDaily> points = new ArrayList<>();
        BigDecimal first = new BigDecimal(firstNav);
        BigDecimal latest = new BigDecimal(latestNav);
        LocalDate start = LocalDate.of(2025, 9, 4);
        for (int index = 0; index <= 120; index++) {
            BigDecimal progress = BigDecimal.valueOf(index)
                    .divide(new BigDecimal("120"), 8, java.math.RoundingMode.HALF_UP);
            BigDecimal value = first.add(latest.subtract(first).multiply(progress));
            points.add(nav(fundCode, start.plusDays(index), value));
        }
        return points;
    }

    private ScreenerBacktestResult cached(String scoreDate, int horizon, String bucket, int sampleCount,
                                           String avgReturn, String winRate, String excess, String drawdown) {
        ScreenerBacktestResult row = new ScreenerBacktestResult();
        row.setRunDate(LocalDate.of(2026, 7, 10));
        row.setScoreDate(LocalDate.parse(scoreDate));
        row.setHorizonDays(horizon);
        row.setBucketName(bucket);
        row.setSampleCount(sampleCount);
        row.setAvgForwardReturn(new BigDecimal(avgReturn));
        row.setWinRate(new BigDecimal(winRate));
        row.setAvgExcessReturn(new BigDecimal(excess));
        row.setMaxDrawdown(new BigDecimal(drawdown));
        row.setModelVersion("screener-rule-v2");
        return row;
    }
}
