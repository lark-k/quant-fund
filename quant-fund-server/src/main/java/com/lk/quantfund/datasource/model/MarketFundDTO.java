package com.lk.quantfund.datasource.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketFundDTO(
        String fundCode,
        String fundName,
        String fundType,
        String shareClass,
        String mainFundCode,
        String companyName,
        String managerName,
        LocalDate establishDate,
        BigDecimal fundSize,
        String trackingIndex,
        boolean activeFund,
        String riskLevel,
        String status,
        String sourceName
) {
}
