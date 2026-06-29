package com.lk.quantfund.dto.quant;

import java.util.List;

public record QuantAnalyzeRequest(
        String requestId,
        Long userId,
        QuantAccountContextDTO account,
        QuantRiskProfileDTO riskProfile,
        QuantHoldingContextDTO holding,
        List<QuantNavPointDTO> navSeries,
        List<QuantTradeDTO> tradeRecords,
        QuantMarketContextDTO market
) {
}
