package com.lk.quantfund.dto.system;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DataSourceConfigRequest(
        @NotBlank
        @Size(max = 64)
        String sourceName,
        @NotBlank
        @Size(max = 512)
        String baseUrl,
        @Min(1000)
        @Max(60000)
        Integer timeoutMs,
        @Min(10)
        @Max(86400)
        Integer refreshIntervalSeconds,
        @Min(1)
        @Max(6000)
        Integer rateLimitPerMinute,
        Boolean enabled,
        @Min(1)
        @Max(9999)
        Integer priority,
        String configJson
) {
}
