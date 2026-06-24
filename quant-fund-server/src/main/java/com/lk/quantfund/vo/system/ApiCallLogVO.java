package com.lk.quantfund.vo.system;

import java.time.LocalDateTime;

public record ApiCallLogVO(
        Long id,
        Long userId,
        String provider,
        String apiName,
        String requestUrl,
        String requestMethod,
        Boolean success,
        Integer statusCode,
        String errorMessage,
        Long costTimeMs,
        Boolean fallbackUsed,
        LocalDateTime callTime
) {
}
