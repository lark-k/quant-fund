package com.lk.quantfund.dto.trade;

import com.lk.quantfund.enums.TradeStatus;
import com.lk.quantfund.enums.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeRecordRequest(
        @NotNull Long accountId,
        Long holdingId,
        @NotBlank @Size(max = 32) String fundCode,
        @NotBlank @Size(max = 128) String fundName,
        TradeType tradeType,
        TradeStatus tradeStatus,
        @NotNull @DecimalMin("0.0000") BigDecimal tradeAmount,
        @DecimalMin("0.0000") BigDecimal tradeShare,
        @DecimalMin("0.0000") BigDecimal tradeNav,
        @DecimalMin("0.0000") BigDecimal tradeFee,
        LocalDateTime tradeTime,
        Long relatedTradeId,
        @Size(max = 512) String remark
) {
}

