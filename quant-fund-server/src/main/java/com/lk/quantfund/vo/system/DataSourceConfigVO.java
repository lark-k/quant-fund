package com.lk.quantfund.vo.system;

import java.time.LocalDateTime;

public record DataSourceConfigVO(
        Long id,
        Long userId,
        String sourceName,
        String baseUrl,
        Integer timeoutMs,
        Integer refreshIntervalSeconds,
        Integer rateLimitPerMinute,
        Boolean enabled,
        Integer priority,
        String configJson,
        boolean userOverride,
        LocalDateTime updateTime
) {
}
