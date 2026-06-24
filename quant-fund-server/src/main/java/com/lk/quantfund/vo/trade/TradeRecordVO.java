package com.lk.quantfund.vo.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeRecordVO(
        Long id,
        Long accountId,
        Long holdingId,
        String fundCode,
        String fundName,
        String tradeType,
        String tradeStatus,
        BigDecimal tradeAmount,
        BigDecimal tradeShare,
        BigDecimal tradeNav,
        BigDecimal tradeFee,
        LocalDateTime tradeTime,
        Long relatedTradeId,
        String remark,
        LocalDateTime updateTime,
        String simulatedTradeNotice,
        String disclaimer
) {
}

