package com.lk.quantfund.vo.system;

import java.time.LocalDateTime;

public record OperationLogVO(
        Long id,
        Long userId,
        String module,
        String action,
        String bizType,
        String requestMethod,
        String requestUri,
        String requestParams,
        String responseResult,
        String ip,
        String userAgent,
        Boolean success,
        String errorMessage,
        Long costTimeMs,
        LocalDateTime createTime
) {
}
