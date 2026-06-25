package com.lk.quantfund.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IndexCompareVO(
        String indexCode,
        String indexName,
        BigDecimal indexChangeRate,
        BigDecimal selectedRangeProfitRate,
        BigDecimal excessReturn,
        LocalDateTime updateTime,
        String sourceName,
        String statusText,
        boolean available
) {
}
