package com.lk.quantfund.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotProfitStatusResolverTest {

    private final FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
    private final SnapshotProfitStatusResolver resolver = new SnapshotProfitStatusResolver(fundNavDailyMapper);

    @Test
    void shouldConfirmSnapshotWhenOfficialNavExistsForAllSnapshotFunds() {
        LocalDate date = LocalDate.of(2026, 6, 25);
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(nav("510300", date)));

        var statuses = resolver.resolve(
                List.of(snapshot(10L, date)),
                List.of(holding(10L, "510300")),
                date.minusDays(1),
                date
        );

        assertThat(statuses.get(date).code()).isEqualTo("CONFIRMED");
        assertThat(statuses.get(date).text()).contains("正式净值");
    }

    @Test
    void shouldKeepSnapshotSyncedWhenOfficialNavEvidenceIsMissing() {
        LocalDate date = LocalDate.of(2026, 6, 25);
        when(fundNavDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var statuses = resolver.resolve(
                List.of(snapshot(10L, date)),
                List.of(holding(10L, "510300")),
                date.minusDays(1),
                date
        );

        assertThat(statuses.get(date).code()).isEqualTo("SNAPSHOT_SYNCED");
        assertThat(statuses.get(date).text()).contains("待确认");
    }

    private HoldingSnapshot snapshot(Long holdingId, LocalDate date) {
        HoldingSnapshot snapshot = new HoldingSnapshot();
        snapshot.setHoldingId(holdingId);
        snapshot.setSnapshotDate(date);
        snapshot.setDailyProfit(BigDecimal.TEN);
        return snapshot;
    }

    private FundHolding holding(Long id, String fundCode) {
        FundHolding holding = new FundHolding();
        holding.setId(id);
        holding.setFundCode(fundCode);
        return holding;
    }

    private FundNavDaily nav(String fundCode, LocalDate navDate) {
        FundNavDaily nav = new FundNavDaily();
        nav.setFundCode(fundCode);
        nav.setNavDate(navDate);
        nav.setUnitNav(BigDecimal.ONE);
        nav.setSourceName("EAST_MONEY");
        return nav;
    }
}
