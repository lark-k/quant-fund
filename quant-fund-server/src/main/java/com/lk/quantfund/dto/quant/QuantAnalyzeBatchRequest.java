package com.lk.quantfund.dto.quant;

import java.util.List;

public record QuantAnalyzeBatchRequest(
        String requestId,
        List<QuantAnalyzeRequest> items
) {
}
