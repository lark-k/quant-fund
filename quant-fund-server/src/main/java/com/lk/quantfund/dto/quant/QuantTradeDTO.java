package com.lk.quantfund.dto.quant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuantTradeDTO(
        String tradeType,
        BigDecimal tradeAmount,
        BigDecimal tradeShare,
        BigDecimal tradeNav,
        LocalDateTime tradeTime
) {
}
