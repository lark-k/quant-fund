package com.lk.quantfund.dto.quant;

import java.time.LocalDateTime;

public record QuantMarketContextDTO(
        Boolean tradingDay,
        Boolean trading,
        String decisionPhase,
        LocalDateTime now,
        LocalDateTime deadline
) {
}
