package com.lk.quantfund.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.lk.quantfund.config.QuantFundProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TradingCalendarServiceTest {

    @Test
    void weekendIsNotTradingDay() {
        QuantFundProperties properties = new QuantFundProperties();
        TradingCalendarService service = new TradingCalendarService(properties);

        assertThat(service.isTradingDay(LocalDate.of(2026, 6, 27))).isFalse();
    }

    @Test
    void configuredHolidayIsNotTradingDay() {
        QuantFundProperties properties = new QuantFundProperties();
        properties.getScheduler().setHolidays("2026-06-24, 2026-10-01");
        TradingCalendarService service = new TradingCalendarService(properties);

        assertThat(service.isTradingDay(LocalDate.of(2026, 6, 24))).isFalse();
    }

    @Test
    void regularWeekdayIsTradingDay() {
        QuantFundProperties properties = new QuantFundProperties();
        TradingCalendarService service = new TradingCalendarService(properties);

        assertThat(service.isTradingDay(LocalDate.of(2026, 6, 25))).isTrue();
    }

    @Test
    void aShareIntradayWindowUsesExactTradingHours() {
        TradingCalendarService service = new TradingCalendarService(new QuantFundProperties());

        assertThat(service.isIntradayEstimateWindow(LocalDateTime.of(2026, 6, 25, 9, 29))).isFalse();
        assertThat(service.isIntradayEstimateWindow(LocalDateTime.of(2026, 6, 25, 9, 30))).isTrue();
        assertThat(service.isIntradayEstimateWindow(LocalDateTime.of(2026, 6, 25, 11, 30))).isTrue();
        assertThat(service.isIntradayEstimateWindow(LocalDateTime.of(2026, 6, 25, 11, 31))).isFalse();
        assertThat(service.isIntradayEstimateWindow(LocalDateTime.of(2026, 6, 25, 13, 0))).isTrue();
        assertThat(service.isIntradayEstimateWindow(LocalDateTime.of(2026, 6, 25, 15, 0))).isTrue();
        assertThat(service.isIntradayEstimateWindow(LocalDateTime.of(2026, 6, 25, 15, 1))).isFalse();
    }

    @Test
    void intradayEstimateDisplayWindowKeepsTodayEstimateDuringLunchBreakAndAfterClose() {
        TradingCalendarService service = new TradingCalendarService(new QuantFundProperties());

        assertThat(service.isIntradayEstimateDisplayWindow(LocalDateTime.of(2026, 6, 25, 9, 29))).isFalse();
        assertThat(service.isIntradayEstimateDisplayWindow(LocalDateTime.of(2026, 6, 25, 9, 30))).isTrue();
        assertThat(service.isIntradayEstimateDisplayWindow(LocalDateTime.of(2026, 6, 25, 11, 31))).isTrue();
        assertThat(service.isIntradayEstimateDisplayWindow(LocalDateTime.of(2026, 6, 25, 15, 1))).isTrue();
        assertThat(service.isIntradayEstimateDisplayWindow(LocalDateTime.of(2026, 6, 27, 11, 31))).isFalse();
    }

    @Test
    void hongKongTradingWindowUsesExactTradingHours() {
        TradingCalendarService service = new TradingCalendarService(new QuantFundProperties());

        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 9, 30))).isTrue();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 12, 0))).isTrue();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 12, 1))).isFalse();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 13, 0))).isTrue();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 16, 0))).isTrue();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 16, 1))).isFalse();
    }

    @Test
    void marketSpecificHolidayListsAreIndependent() {
        QuantFundProperties properties = new QuantFundProperties();
        properties.getScheduler().setHolidays("2026-06-19");
        properties.getScheduler().setHongKongHolidays("2026-07-01");
        properties.getScheduler().setUsHolidays("2026-07-03");
        TradingCalendarService service = new TradingCalendarService(properties);

        assertThat(service.isTradingDay(MarketType.A_SHARE, LocalDate.of(2026, 6, 19))).isFalse();
        assertThat(service.isTradingDay(MarketType.HONG_KONG, LocalDate.of(2026, 6, 19))).isTrue();
        assertThat(service.isTradingDay(MarketType.HONG_KONG, LocalDate.of(2026, 7, 1))).isFalse();
        assertThat(service.isTradingDay(MarketType.US, LocalDate.of(2026, 7, 3))).isFalse();
    }

    @Test
    void marketSessionReturnsReadableStatus() {
        TradingCalendarService service = new TradingCalendarService(new QuantFundProperties());

        assertThat(service.marketSession(MarketType.A_SHARE, LocalDateTime.of(2026, 6, 25, 10, 0))).isEqualTo("A股交易中");
        assertThat(service.marketSession(MarketType.HONG_KONG, LocalDateTime.of(2026, 6, 25, 15, 30))).isEqualTo("港股交易中");
        assertThat(service.marketSession(MarketType.US, LocalDateTime.of(2026, 6, 25, 22, 0))).isEqualTo("美股交易中");
        assertThat(service.marketSession(MarketType.US, LocalDateTime.of(2026, 6, 27, 1, 0))).isEqualTo("美股交易中");
        assertThat(service.marketSession(MarketType.US, LocalDateTime.of(2026, 6, 25, 12, 0))).isEqualTo("海外市场参考/待海外收盘");
    }
}
