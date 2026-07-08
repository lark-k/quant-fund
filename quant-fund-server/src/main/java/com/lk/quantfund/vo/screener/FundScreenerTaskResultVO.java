package com.lk.quantfund.vo.screener;

import java.time.LocalDateTime;
import java.util.List;

public record FundScreenerTaskResultVO(
        String taskName,
        String status,
        int successCount,
        int failureCount,
        int skippedCount,
        long costTimeMs,
        List<String> errorSummaries,
        String message,
        LocalDateTime finishTime
) {

    public static FundScreenerTaskResultVO skipped(String taskName, String message) {
        return new FundScreenerTaskResultVO(
                taskName,
                "SKIPPED",
                0,
                0,
                1,
                0,
                List.of(),
                message,
                LocalDateTime.now()
        );
    }
}
