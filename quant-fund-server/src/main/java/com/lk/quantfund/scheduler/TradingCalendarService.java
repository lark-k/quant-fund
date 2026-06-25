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
        return isTradingDay(MarketType.A_SHARE, date);
    }

    public boolean isTradingDay(MarketType market, LocalDate date) {
        if (isWeekend(date)) {
            return false;
        }
        return !configuredHolidays(market).contains(date.toString());
    }

    public boolean isIntradayEstimateWindow(LocalDateTime dateTime) {
        return isTradingWindow(MarketType.A_SHARE, dateTime);
    }

    public boolean isIntradayEstimateDisplayWindow(LocalDateTime dateTime) {
        return isTradingDay(MarketType.A_SHARE, dateTime.toLocalDate())
                && !dateTime.toLocalTime().isBefore(LocalTime.of(9, 30));
    }

    public boolean isTradingWindow(MarketType market, LocalDateTime dateTime) {
        if (!isTradingDay(market, tradingDateForWindow(market, dateTime))) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        return switch (market) {
            case A_SHARE -> between(time, LocalTime.of(9, 30), LocalTime.of(11, 30))
                    || between(time, LocalTime.of(13, 0), LocalTime.of(15, 0));
            case HONG_KONG -> between(time, LocalTime.of(9, 30), LocalTime.of(12, 0))
                    || between(time, LocalTime.of(13, 0), LocalTime.of(16, 0));
            case US -> between(time, LocalTime.of(21, 30), LocalTime.of(23, 59))
                    || between(time, LocalTime.MIDNIGHT, LocalTime.of(4, 0));
        };
    }

    public boolean isBeforeIntradayEstimateWindow(LocalDateTime dateTime) {
        return isTradingDay(MarketType.A_SHARE, dateTime.toLocalDate()) && dateTime.toLocalTime().isBefore(LocalTime.of(9, 30));
    }

    public LocalDate nextTradingDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate cursor = date.plusDays(1);
        while (!isTradingDay(cursor)) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    public boolean isHongKongTradingWindow(LocalDateTime dateTime) {
        return isTradingWindow(MarketType.HONG_KONG, dateTime);
    }

    public String marketSession(MarketType market, LocalDateTime dateTime) {
        if (isTradingWindow(market, dateTime)) {
            return switch (market) {
                case A_SHARE -> "A股交易中";
                case HONG_KONG -> "港股交易中";
                case US -> "美股交易中";
            };
        }
        if (!isTradingDay(market, dateTime.toLocalDate())) {
            return "非交易日";
        }
        LocalTime time = dateTime.toLocalTime();
        return switch (market) {
            case A_SHARE -> time.isBefore(LocalTime.of(9, 30)) ? "未开盘" : "已收盘";
            case HONG_KONG -> time.isBefore(LocalTime.of(9, 30)) ? "未开盘" : "港股已收盘";
            case US -> "海外市场参考/待海外收盘";
        };
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private LocalDate tradingDateForWindow(MarketType market, LocalDateTime dateTime) {
        if (market == MarketType.US && !dateTime.toLocalTime().isAfter(LocalTime.of(4, 0))) {
            return dateTime.toLocalDate().minusDays(1);
        }
        return dateTime.toLocalDate();
    }

    private boolean between(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    private Set<String> configuredHolidays(MarketType market) {
        String holidays = switch (market) {
            case A_SHARE -> properties.getScheduler().getHolidays();
            case HONG_KONG -> firstText(properties.getScheduler().getHongKongHolidays(), properties.getScheduler().getHolidays());
            case US -> properties.getScheduler().getUsHolidays();
        };
        if (!StringUtils.hasText(holidays)) {
            return Set.of();
        }
        return Arrays.stream(holidays.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
