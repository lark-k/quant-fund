package com.lk.quantfund.vo.dashboard;

import java.time.LocalDateTime;

public record DashboardEstimateStatusVO(
        Integer trackedFundCount,
        Integer refreshedTodayCount,
        Integer delayedCount,
        LocalDateTime latestEstimateTime,
        String statusText
) {
}
