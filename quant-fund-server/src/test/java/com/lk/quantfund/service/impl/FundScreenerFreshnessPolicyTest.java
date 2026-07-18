package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.scheduler.TradingCalendarService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FundScreenerFreshnessPolicyTest {

    @Test
    void shouldRequireThePreviousAvailableTradingDate() {
        QuantFundProperties properties = new QuantFundProperties();
        FundScreenerFreshnessPolicy policy = new FundScreenerFreshnessPolicy(new TradingCalendarService(properties));

        assertThat(policy.requiredNavDate(LocalDateTime.of(2026, 7, 18, 12, 0))).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(policy.requiredNavDate(LocalDateTime.of(2026, 7, 20, 12, 0))).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(policy.requiredNavDate(LocalDateTime.of(2026, 7, 20, 22, 0))).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void shouldRespectConfiguredTradingHolidays() {
        QuantFundProperties properties = new QuantFundProperties();
        properties.getScheduler().setHolidays("2026-07-17");
        FundScreenerFreshnessPolicy policy = new FundScreenerFreshnessPolicy(new TradingCalendarService(properties));

        assertThat(policy.requiredNavDate(LocalDateTime.of(2026, 7, 18, 12, 0))).isEqualTo(LocalDate.of(2026, 7, 16));
    }
}
