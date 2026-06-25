package com.lk.quantfund.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HoldingSnapshotBackfillServiceTest {

    @Test
    void ensureRecentSnapshotsBackfillsConfiguredTradingDaysAndSkipsHoliday() {
        QuantFundProperties properties = new QuantFundProperties();
        properties.getScheduler().setHolidays("2026-06-19");
        properties.getScheduler().setSnapshotBackfillTradingDays(5);
        TradingCalendarService calendarService = new TradingCalendarService(properties);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        HoldingSnapshotMapper snapshotMapper = mock(HoldingSnapshotMapper.class);
        HoldingSnapshotBackfillService service = new HoldingSnapshotBackfillService(
                holdingMapper, accountMapper, snapshotMapper, calendarService, properties);
        FundHolding holding = holding(LocalDate.of(2026, 6, 1));
        PortfolioAccount account = account();
        List<LocalDate> insertedDates = new ArrayList<>();
        when(holdingMapper.selectList(any())).thenReturn(List.of(holding));
        when(accountMapper.selectById(10L)).thenReturn(account);
        when(snapshotMapper.selectCount(any())).thenReturn(0L);
        when(snapshotMapper.insert(any(HoldingSnapshot.class))).thenAnswer(invocation -> {
            insertedDates.add(invocation.<HoldingSnapshot>getArgument(0).getSnapshotDate());
            return 1;
        });

        service.ensureRecentSnapshots(1L, LocalDate.of(2026, 6, 25));

        assertThat(insertedDates).containsExactly(
                LocalDate.of(2026, 6, 24),
                LocalDate.of(2026, 6, 23),
                LocalDate.of(2026, 6, 22),
                LocalDate.of(2026, 6, 18),
                LocalDate.of(2026, 6, 17)
        );
        assertThat(insertedDates).doesNotContain(LocalDate.of(2026, 6, 19));
    }

    @Test
    void ensureSnapshotKeepsHistoricalSnapshotWhenAlreadyExists() {
        QuantFundProperties properties = new QuantFundProperties();
        TradingCalendarService calendarService = new TradingCalendarService(properties);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        HoldingSnapshotMapper snapshotMapper = mock(HoldingSnapshotMapper.class);
        HoldingSnapshotBackfillService service = new HoldingSnapshotBackfillService(
                holdingMapper, accountMapper, snapshotMapper, calendarService, properties);
        when(holdingMapper.selectList(any())).thenReturn(List.of(holding(LocalDate.of(2026, 6, 1))));
        when(accountMapper.selectById(10L)).thenReturn(account());
        HoldingSnapshot existing = new HoldingSnapshot();
        existing.setId(9L);
        when(snapshotMapper.selectOne(any())).thenReturn(existing);

        service.ensureSnapshot(1L, LocalDate.now().minusDays(1));

        verify(snapshotMapper, never()).insert(any(HoldingSnapshot.class));
        verify(snapshotMapper, never()).updateById(any(HoldingSnapshot.class));
    }

    @Test
    void ensureSnapshotCopiesHoldingAndAccountValues() {
        QuantFundProperties properties = new QuantFundProperties();
        TradingCalendarService calendarService = new TradingCalendarService(properties);
        FundHoldingMapper holdingMapper = mock(FundHoldingMapper.class);
        PortfolioAccountMapper accountMapper = mock(PortfolioAccountMapper.class);
        HoldingSnapshotMapper snapshotMapper = mock(HoldingSnapshotMapper.class);
        HoldingSnapshotBackfillService service = new HoldingSnapshotBackfillService(
                holdingMapper, accountMapper, snapshotMapper, calendarService, properties);
        when(holdingMapper.selectList(any())).thenReturn(List.of(holding(LocalDate.of(2026, 6, 1))));
        when(accountMapper.selectById(10L)).thenReturn(account());
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        ArgumentCaptor<HoldingSnapshot> captor = ArgumentCaptor.forClass(HoldingSnapshot.class);

        service.ensureSnapshot(1L, LocalDate.now().minusDays(1));

        verify(snapshotMapper).insert(captor.capture());
        HoldingSnapshot snapshot = captor.getValue();
        assertThat(snapshot.getUserId()).isEqualTo(1L);
        assertThat(snapshot.getAccountId()).isEqualTo(10L);
        assertThat(snapshot.getHoldingId()).isEqualTo(100L);
        assertThat(snapshot.getTotalAsset()).isEqualByComparingTo("10000.0000");
        assertThat(snapshot.getHoldingAmount()).isEqualByComparingTo("2500.0000");
        assertThat(snapshot.getPositionRate()).isEqualByComparingTo("25.0000");
    }

    private FundHolding holding(LocalDate createDate) {
        FundHolding holding = new FundHolding();
        holding.setId(100L);
        holding.setUserId(1L);
        holding.setAccountId(10L);
        holding.setFundCode("000001");
        holding.setHoldingAmount(new BigDecimal("2500"));
        holding.setHoldingProfit(new BigDecimal("120.5"));
        holding.setDailyProfit(new BigDecimal("8.25"));
        holding.setCreateTime(createDate.atStartOfDay());
        return holding;
    }

    private PortfolioAccount account() {
        PortfolioAccount account = new PortfolioAccount();
        account.setId(10L);
        account.setTotalAsset(new BigDecimal("10000"));
        return account;
    }
}
