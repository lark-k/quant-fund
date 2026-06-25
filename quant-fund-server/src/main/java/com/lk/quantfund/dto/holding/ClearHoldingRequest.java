package com.lk.quantfund.dto.holding;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record ClearHoldingRequest(
        @DecimalMin(value = "0.00", message = "确认金额不能为负数")
        BigDecimal tradeAmount,

        @DecimalMin(value = "0.00", message = "手续费不能为负数")
        BigDecimal tradeFee,

        String remark
) {
}
