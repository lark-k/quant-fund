package com.lk.quantfund.service.analytics;

import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.scheduler.TradingCalendarService;
import java.time.LocalDate;

public final class OfficialNavTiming {

    private OfficialNavTiming() {
    }

    public static LocalDate effectiveDate(FundHolding holding, LocalDate navDate, TradingCalendarService tradingCalendarService) {
        if (navDate == null || !isDelayedOfficialNavFund(holding)) {
            return navDate;
        }
        return tradingCalendarService.nextTradingDay(navDate);
    }

    public static boolean isDelayedOfficialNavFund(FundHolding holding) {
        if (holding == null) {
            return false;
        }
        String text = normalize(holding.getFundName()) + " " + normalize(holding.getFundType());
        if (isHongKongLike(text)) {
            return false;
        }
        return text.contains("美国")
                || text.contains("美股")
                || text.contains("纳指")
                || text.contains("纳斯达克")
                || text.contains("NASDAQ")
                || text.contains("NDX")
                || text.contains("标普")
                || text.contains("S&P")
                || text.contains("SP500")
                || text.contains("SPX")
                || text.contains("道琼斯")
                || text.contains("DOW")
                || text.contains("海外")
                || text.contains("OVERSEAS")
                || text.contains("全球")
                || text.contains("GLOBAL")
                || text.contains("美元")
                || text.contains("USD");
    }

    private static boolean isHongKongLike(String text) {
        return text.contains("恒生")
                || text.contains("港股")
                || text.contains("香港")
                || text.contains("H股")
                || text.contains("HANG SENG")
                || text.contains("HONG KONG")
                || text.contains("HSI")
                || text.contains("HSTECH");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase();
    }
}
