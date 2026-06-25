package com.lk.quantfund.vo.system;

public record AiRuntimeConfigVO(
        boolean enabled,
        String provider,
        String model,
        String baseUrl,
        boolean keyPresent,
        boolean mockEnabled,
        boolean ready,
        String diagnosis
) {
}
