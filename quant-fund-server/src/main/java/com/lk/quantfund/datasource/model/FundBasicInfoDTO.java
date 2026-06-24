package com.lk.quantfund.datasource.model;

public record FundBasicInfoDTO(
        String fundCode,
        String fundName,
        String fundType,
        boolean activeFund,
        String trackingIndex,
        String managerName,
        String companyName,
        String riskLevel,
        String themeTags,
        String sourceName
) {
}

