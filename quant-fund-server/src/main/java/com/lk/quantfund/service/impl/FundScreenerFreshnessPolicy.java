package com.lk.quantfund.service.impl;

import com.lk.quantfund.scheduler.TradingCalendarService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

@Component
public class FundScreenerFreshnessPolicy {

    private final TradingCalendarService tradingCalendarService;

    public FundScreenerFreshnessPolicy(TradingCalendarService tradingCalendarService) {
        this.tradingCalendarService = tradingCalendarService;
    }

    public LocalDate requiredNavDate() {
        return requiredNavDate(LocalDateTime.now());
    }

    LocalDate requiredNavDate(LocalDateTime now) {
        LocalDate cursor = now.toLocalDate();
        if (!tradingCalendarService.isTradingDay(cursor) || now.toLocalTime().isBefore(LocalTime.of(20, 0))) {
            cursor = cursor.minusDays(1);
        }
        while (!tradingCalendarService.isTradingDay(cursor)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    public boolean isFresh(LocalDate dataDate) {
        return dataDate != null && !dataDate.isBefore(requiredNavDate());
    }
}
