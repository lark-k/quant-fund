package com.lk.quantfund.dto.quant;

import java.util.List;
import java.util.Map;

public record QuantAnalyzeBatchResponse(
        String requestId,
        List<QuantAnalyzeResponse> results,
        Integer successCount,
        Integer failedCount,
        List<Map<String, Object>> errors
) {
}
