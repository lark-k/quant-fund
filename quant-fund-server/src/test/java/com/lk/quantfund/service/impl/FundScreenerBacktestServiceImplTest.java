package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerQualityScore;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerQualityScoreMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundScreenerBacktestServiceImplTest {

    private final ScreenerQualityScoreMapper scoreMapper = mock(ScreenerQualityScoreMapper.class);
    private final ScreenerFundNavDailyMapper navMapper = mock(ScreenerFundNavDailyMapper.class);
    private final FundScreenerBacktestServiceImpl service = new FundScreenerBacktestServiceImpl(scoreMapper, navMapper);

    @Test
    void shouldEvaluateHistoricalTopScoresWithForwardNav() {
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                score("000001", "2026-01-02", "90.0000"),
                score("000002", "2026-01-02", "85.0000")
        ));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                List.of(nav("000001", "2026-01-02", "1.0000"), nav("000001", "2026-02-02", "1.1000")),
                List.of(nav("000002", "2026-01-02", "2.0000"), nav("000002", "2026-02-02", "2.1000"))
        );

        var result = service.backtest();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isZero();
        assertThat(result.message()).contains("平均未来收益");
        assertThat(result.message()).contains("7.5000%");
    }

    @Test
    void shouldCountMissingForwardNavAsFailureWithoutWritingTables() {
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                score("000001", "2026-01-02", "90.0000")
        ));
        when(navMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                nav("000001", "2026-01-02", "1.0000")
        ));

        var result = service.backtest();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errorSummaries()).contains("000001: future nav not found");
        verify(scoreMapper, never()).insert(any(ScreenerQualityScore.class));
        verify(scoreMapper, never()).updateById(any(ScreenerQualityScore.class));
        verify(navMapper, never()).insert(any(ScreenerFundNavDaily.class));
        verify(navMapper, never()).updateById(any(ScreenerFundNavDaily.class));
    }

    private ScreenerQualityScore score(String fundCode, String scoreDate, String qualityScore) {
        ScreenerQualityScore score = new ScreenerQualityScore();
        score.setFundCode(fundCode);
        score.setScoreDate(LocalDate.parse(scoreDate));
        score.setQualityScore(new BigDecimal(qualityScore));
        score.setRecommendLevel("STRONG");
        return score;
    }

    private ScreenerFundNavDaily nav(String fundCode, String navDate, String unitNav) {
        ScreenerFundNavDaily nav = new ScreenerFundNavDaily();
        nav.setFundCode(fundCode);
        nav.setNavDate(LocalDate.parse(navDate));
        nav.setUnitNav(new BigDecimal(unitNav));
        return nav;
    }
}
