package com.lk.quantfund.vo.trade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvestmentPlanVO(
        Long id,
        Long accountId,
        String fundCode,
        String fundName,
        String planName,
        String planType,
        BigDecimal amount,
        String frequency,
        LocalDate nextExecuteDate,
        String status,
        LocalDateTime updateTime
) {
}
