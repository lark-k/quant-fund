package com.lk.quantfund.scheduler;

import com.lk.quantfund.config.QuantFundProperties;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TradingCalendarService {

    private final QuantFundProperties properties;

    public TradingCalendarService(QuantFundProperties properties) {
        this.properties = properties;
    }

    public boolean isTradingDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        return !configuredHolidays().contains(date.toString());
    }

    public boolean isIntradayEstimateWindow(LocalDateTime dateTime) {
        if (!isTradingDay(dateTime.toLocalDate())) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        return between(time, LocalTime.of(9, 30), LocalTime.of(11, 30))
                || between(time, LocalTime.of(13, 0), LocalTime.of(15, 0));
    }

    public boolean isBeforeIntradayEstimateWindow(LocalDateTime dateTime) {
        return isTradingDay(dateTime.toLocalDate()) && dateTime.toLocalTime().isBefore(LocalTime.of(9, 30));
    }

    public boolean isHongKongTradingWindow(LocalDateTime dateTime) {
        if (!isTradingDay(dateTime.toLocalDate())) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        return between(time, LocalTime.of(9, 30), LocalTime.of(12, 0))
                || between(time, LocalTime.of(13, 0), LocalTime.of(16, 0));
    }

    private boolean between(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    private Set<String> configuredHolidays() {
        String holidays = properties.getScheduler().getHolidays();
        if (!StringUtils.hasText(holidays)) {
            return Set.of();
        }
        return Arrays.stream(holidays.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }
}
