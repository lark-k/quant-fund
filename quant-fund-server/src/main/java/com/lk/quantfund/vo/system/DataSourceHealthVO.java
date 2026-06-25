package com.lk.quantfund.vo.system;

import java.time.LocalDateTime;

public record DataSourceHealthVO(
        String provider,
        String apiName,
        Boolean healthy,
        Boolean delayed,
        LocalDateTime lastCallTime,
        LocalDateTime lastSuccessTime,
        LocalDateTime lastFailureTime,
        String lastFailureReason,
        Long lastCostTimeMs,
        String statusText
) {
}
