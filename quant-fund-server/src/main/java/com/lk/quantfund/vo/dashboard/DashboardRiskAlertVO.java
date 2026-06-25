package com.lk.quantfund.vo.dashboard;

import java.time.LocalDateTime;

public record DashboardRiskAlertVO(
        String id,
        String sourceType,
        String alertType,
        String riskLevel,
        String title,
        String fundCode,
        String fundName,
        String content,
        LocalDateTime alertTime
) {
}
