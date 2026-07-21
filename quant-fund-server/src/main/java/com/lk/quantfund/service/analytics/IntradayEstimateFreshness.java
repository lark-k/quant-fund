package com.lk.quantfund.service.analytics;

import com.lk.quantfund.entity.FundEstimateIntraday;
import java.time.LocalDateTime;

public final class IntradayEstimateFreshness {

    private IntradayEstimateFreshness() {
    }

    public static boolean isFresh(FundEstimateIntraday estimate, LocalDateTime now) {
        if (estimate == null || now == null || estimate.getEstimateTime() == null
                || estimate.getEstimateDate() == null
                || !estimate.getEstimateDate().equals(now.toLocalDate())
                || Integer.valueOf(1).equals(estimate.getDelayed())) {
            return false;
        }
        // Providers can stop publishing individual funds before the market closes.
        // Keep the latest valid value for the trading day, matching the holdings view.
        return !estimate.getEstimateTime().isAfter(now.plusMinutes(2));
    }
}
