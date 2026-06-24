package com.lk.quantfund.vo.dashboard;

import java.math.BigDecimal;

public record DashboardPositionSliceVO(
        String name,
        BigDecimal rate
) {
}
