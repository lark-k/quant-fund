package com.lk.quantfund.dto.screener;

import java.math.BigDecimal;

public record FundScreenerQueryRequest(
        String fundType,
        String period,
        String riskLevel,
        BigDecimal minScore,
        BigDecimal minFundSize,
        Boolean excludeShareClassC,
        Boolean onlyActiveFund,
        String recommendLevel,
        long pageNo,
        long pageSize,
        String sortBy
) {

    public FundScreenerQueryRequest {
        if (pageNo <= 0) {
            pageNo = 1;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 20;
        }
        if (period == null || period.isBlank()) {
            period = "120d";
        }
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "qualityScore";
        }
        if (excludeShareClassC == null) {
            excludeShareClassC = true;
        }
        if (onlyActiveFund == null) {
            onlyActiveFund = false;
        }
    }
}
