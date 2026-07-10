package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.dto.screener.FundScreenerQueryRequest;
import com.lk.quantfund.entity.ScreenerFactorSnapshot;
import com.lk.quantfund.entity.ScreenerFundUniverse;
import com.lk.quantfund.entity.ScreenerQualityScore;
import com.lk.quantfund.mapper.ScreenerFactorSnapshotMapper;
import com.lk.quantfund.mapper.ScreenerFundUniverseMapper;
import com.lk.quantfund.mapper.ScreenerQualityScoreMapper;
import com.lk.quantfund.vo.screener.FundScreenerRankItemVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FundQualityScoreServiceImplTest {

    private final ScreenerFactorSnapshotMapper factorMapper = mock(ScreenerFactorSnapshotMapper.class);
    private final ScreenerQualityScoreMapper scoreMapper = mock(ScreenerQualityScoreMapper.class);
    private final ScreenerFundUniverseMapper universeMapper = mock(ScreenerFundUniverseMapper.class);
    private final FundQualityScoreServiceImpl service = new FundQualityScoreServiceImpl(
            factorMapper,
            scoreMapper,
            universeMapper,
            new ObjectMapper()
    );

    @Test
    void shouldGenerateQualityScoreWithReasonsAndRisks() {
        when(factorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(factor("000001")));
        when(universeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(universe("000001", "MIXED"));
        when(scoreMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        var result = service.refreshScore();

        ArgumentCaptor<ScreenerQualityScore> captor = ArgumentCaptor.forClass(ScreenerQualityScore.class);
        verify(scoreMapper).insert(captor.capture());
        ScreenerQualityScore score = captor.getValue();
        assertThat(score.getQualityScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100.0000"));
        assertThat(score.getRecommendLevel()).isIn("STRONG", "WATCH", "NEUTRAL", "AVOID");
        assertThat(score.getReasonsJson()).contains("净值样本");
        assertThat(score.getRisksJson()).contains("不构成投资建议");
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldBatchLoadRefreshScoreDependenciesWithoutPerFundSelects() {
        ScreenerQualityScore existing = score("000001");
        existing.setId(10L);
        when(factorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                factor("000001"),
                factor("000002")
        ));
        when(universeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                universe("000001", "MIXED"),
                universe("000002", "INDEX")
        ));
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));

        var result = service.refreshScore();

        assertThat(result.successCount()).isEqualTo(2);
        verify(universeMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(scoreMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldReturnPagedRankAndExplainFromScoreRows() {
        ScreenerQualityScore score = score("000001");
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(score));
        when(universeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(universe("000001", "MIXED"));
        when(factorMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(factor("000001"));

        var page = service.rank(new FundScreenerQueryRequest(null, "120d", null, null, null, true, false, null, 1, 20, "qualityScore"));
        var explain = service.explain("000001");

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().getFirst().fundCode()).isEqualTo("000001");
        assertThat(page.records().getFirst().reasons()).contains("近120日收益表现较好");
        assertThat(explain.fundCode()).isEqualTo("000001");
        assertThat(explain.scoreBreakdown().returnScore()).isEqualTo(82.0);
        assertThat(explain.factors()).containsEntry("return120d", new BigDecimal("16.0000"));
    }

    @Test
    void shouldBatchLoadRankDependenciesWithoutPerFundQueries() {
        ScreenerQualityScore first = score("000001");
        ScreenerQualityScore second = score("000002");
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));
        when(universeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                universe("000001", "MIXED"),
                universe("000002", "MIXED")
        ));
        when(factorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                factor("000001"),
                factor("000002")
        ));

        var page = service.rank(new FundScreenerQueryRequest(null, "120d", null, null, null, true, false, null, 1, 20, "qualityScore"));

        assertThat(page.total()).isEqualTo(2);
        verify(universeMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(factorMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldRankOnlyLatestScorePerFund() {
        ScreenerQualityScore oldScore = score("000001");
        oldScore.setScoreDate(LocalDate.of(2026, 7, 20));
        oldScore.setQualityScore(new BigDecimal("96.0000"));
        ScreenerQualityScore latestScore = score("000001");
        latestScore.setScoreDate(LocalDate.of(2026, 7, 21));
        latestScore.setQualityScore(new BigDecimal("78.0000"));
        ScreenerQualityScore otherFund = score("000002");
        otherFund.setScoreDate(LocalDate.of(2026, 7, 21));
        otherFund.setQualityScore(new BigDecimal("88.0000"));
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(oldScore, latestScore, otherFund));
        when(universeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                universe("000001", "MIXED"),
                universe("000002", "MIXED")
        ));
        when(factorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                factor("000001"),
                factor("000002")
        ));

        var page = service.rank(new FundScreenerQueryRequest(null, "120d", null, null, null, true, false, null, 1, 20, "qualityScore"));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).extracting(FundScreenerRankItemVO::fundCode).containsExactly("000002", "000001");
        assertThat(page.records().get(1).qualityScore()).isEqualTo(78.0);
    }

    @Test
    void shouldGenerateV2ScoreDimensionsAndRankBasedRecommendation() {
        ScreenerFactorSnapshot factor = factor("000001");
        factor.setReturnDrawdownRatio120d(new BigDecimal("2.5000"));
        factor.setReturnConsistencyScore(new BigDecimal("86.0000"));
        factor.setFundAgeYears(new BigDecimal("6.0000"));
        when(factorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(factor));
        when(universeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(universe("000001", "MIXED")));
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var result = service.refreshScore();

        ArgumentCaptor<ScreenerQualityScore> captor = ArgumentCaptor.forClass(ScreenerQualityScore.class);
        verify(scoreMapper).insert(captor.capture());
        ScreenerQualityScore score = captor.getValue();
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(score.getModelVersion()).isEqualTo("screener-rule-v2");
        assertThat(score.getReturnQualityScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100.0000"));
        assertThat(score.getDrawdownControlScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100.0000"));
        assertThat(score.getConsistencyScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100.0000"));
        assertThat(score.getInvestabilityScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100.0000"));
        assertThat(score.getRecommendLevel()).isEqualTo("STRONG");
    }

    @Test
    void shouldUseConfiguredRecommendationThresholds() {
        QuantFundProperties properties = new QuantFundProperties();
        properties.getScreenerStrategy().setStrongMinScore(new BigDecimal("95.0000"));
        FundQualityScoreServiceImpl configuredService = new FundQualityScoreServiceImpl(
                factorMapper,
                scoreMapper,
                universeMapper,
                new ObjectMapper(),
                properties
        );
        when(factorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(factor("000001")));
        when(universeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(universe("000001", "MIXED")));
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        configuredService.refreshScore();

        ArgumentCaptor<ScreenerQualityScore> captor = ArgumentCaptor.forClass(ScreenerQualityScore.class);
        verify(scoreMapper).insert(captor.capture());
        assertThat(captor.getValue().getQualityScore()).isLessThan(new BigDecimal("95.0000"));
        assertThat(captor.getValue().getRecommendLevel()).isEqualTo("WATCH");
    }

    @Test
    void shouldApplyShareClassActiveAndFundSizeRankFilters() {
        ScreenerQualityScore activeA = score("000001");
        ScreenerQualityScore activeC = score("000002");
        ScreenerQualityScore passiveA = score("000003");
        ScreenerQualityScore smallActiveA = score("000004");
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(activeA, activeC, passiveA, smallActiveA));
        when(universeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                universe("000001", "MIXED", "A", 1, "25.0000"),
                universe("000002", "MIXED", "C", 1, "30.0000"),
                universe("000003", "MIXED", "A", 0, "28.0000"),
                universe("000004", "MIXED", "A", 1, "3.0000")
        ));
        when(factorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                factor("000001"),
                factor("000002"),
                factor("000003"),
                factor("000004")
        ));

        var filtered = service.rank(new FundScreenerQueryRequest(
                null, "120d", null, null, new BigDecimal("10.0000"), true, true, null, 1, 20, "qualityScore"
        ));
        var unfiltered = service.rank(new FundScreenerQueryRequest(
                null, "120d", null, null, null, false, false, null, 1, 20, "qualityScore"
        ));

        assertThat(filtered.records()).extracting(FundScreenerRankItemVO::fundCode).containsExactly("000001");
        assertThat(unfiltered.records()).extracting(FundScreenerRankItemVO::fundCode)
                .containsExactly("000001", "000002", "000003", "000004");
    }

    private ScreenerFactorSnapshot factor(String fundCode) {
        ScreenerFactorSnapshot factor = new ScreenerFactorSnapshot();
        factor.setFundCode(fundCode);
        factor.setFactorDate(LocalDate.of(2026, 7, 21));
        factor.setReturn60d(new BigDecimal("8.0000"));
        factor.setReturn120d(new BigDecimal("16.0000"));
        factor.setReturn250d(new BigDecimal("28.0000"));
        factor.setMaxDrawdown120d(new BigDecimal("-9.0000"));
        factor.setVolatility120d(new BigDecimal("18.0000"));
        factor.setPositiveDayRatio60d(new BigDecimal("62.0000"));
        factor.setExcessReturn120d(new BigDecimal("3.0000"));
        factor.setPeerPercentile(new BigDecimal("18.0000"));
        factor.setFundSize(new BigDecimal("25.0000"));
        factor.setNavSampleSize(250);
        factor.setReturnDrawdownRatio120d(new BigDecimal("1.7778"));
        factor.setReturnConsistencyScore(new BigDecimal("80.0000"));
        factor.setFundAgeYears(new BigDecimal("5.0000"));
        factor.setBenchmarkCode("000300");
        return factor;
    }

    private ScreenerFundUniverse universe(String fundCode, String type) {
        return universe(fundCode, type, "A", 1, "25.0000");
    }

    private ScreenerFundUniverse universe(String fundCode, String type, String shareClass, Integer activeFund, String fundSize) {
        ScreenerFundUniverse universe = new ScreenerFundUniverse();
        universe.setFundCode(fundCode);
        universe.setFundName("示例基金A");
        universe.setFundType(type);
        universe.setShareClass(shareClass);
        universe.setActiveFund(activeFund);
        universe.setFundSize(new BigDecimal(fundSize));
        universe.setCompanyName("示例基金公司");
        universe.setManagerName("基金经理");
        return universe;
    }

    private ScreenerQualityScore score(String fundCode) {
        ScreenerQualityScore score = new ScreenerQualityScore();
        score.setFundCode(fundCode);
        score.setScoreDate(LocalDate.of(2026, 7, 21));
        score.setQualityScore(new BigDecimal("86.0000"));
        score.setReturnScore(new BigDecimal("82.0000"));
        score.setRiskScore(new BigDecimal("73.0000"));
        score.setStabilityScore(new BigDecimal("62.0000"));
        score.setExcessScore(new BigDecimal("56.0000"));
        score.setPeerScore(new BigDecimal("82.0000"));
        score.setLiquidityScore(new BigDecimal("50.0000"));
        score.setDataScore(new BigDecimal("100.0000"));
        score.setReturnQualityScore(new BigDecimal("83.0000"));
        score.setDrawdownControlScore(new BigDecimal("74.0000"));
        score.setConsistencyScore(new BigDecimal("70.0000"));
        score.setInvestabilityScore(new BigDecimal("88.0000"));
        score.setRankNo(1);
        score.setRankPercentile(new BigDecimal("1.0000"));
        score.setRecommendLevel("STRONG");
        score.setReasonsJson("[\"近120日收益表现较好\"]");
        score.setRisksJson("[\"基金优选结果仅供参考，不构成投资建议\"]");
        score.setModelVersion("screener-rule-v1");
        return score;
    }
}
