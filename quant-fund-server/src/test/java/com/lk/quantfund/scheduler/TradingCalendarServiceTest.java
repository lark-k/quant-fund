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
    void hongKongTradingWindowUsesExactTradingHours() {
        TradingCalendarService service = new TradingCalendarService(new QuantFundProperties());

        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 9, 30))).isTrue();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 12, 0))).isTrue();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 12, 1))).isFalse();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 13, 0))).isTrue();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 16, 0))).isTrue();
        assertThat(service.isHongKongTradingWindow(LocalDateTime.of(2026, 6, 25, 16, 1))).isFalse();
    }
}
