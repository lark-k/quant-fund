package com.lk.quantfund.dto.trade;

import com.lk.quantfund.enums.TradeStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConvertPairTradeRequest(
        @NotNull Long accountId,
        @NotNull Long outHoldingId,
        @NotNull @DecimalMin("0.0000") BigDecimal outTradeAmount,
        @DecimalMin("0.0000") BigDecimal outTradeShare,
        @DecimalMin("0.0000") BigDecimal outTradeNav,
        @DecimalMin("0.0000") BigDecimal outTradeFee,
        Long inHoldingId,
        @NotBlank @Size(max = 32) String inFundCode,
        @NotBlank @Size(max = 128) String inFundName,
        @NotNull @DecimalMin("0.0000") BigDecimal inTradeAmount,
        @DecimalMin("0.0000") BigDecimal inTradeShare,
        @DecimalMin("0.0000") BigDecimal inTradeNav,
        @DecimalMin("0.0000") BigDecimal inTradeFee,
        TradeStatus tradeStatus,
        LocalDateTime tradeTime,
        @Size(max = 512) String remark
) {
}
