package com.lk.quantfund.vo.screener;

import java.util.List;

public record FundScreenerRankItemVO(
        String fundCode,
        String fundName,
        String fundType,
        String companyName,
        String managerName,
        double qualityScore,
        double returnScore,
        double riskScore,
        double stabilityScore,
        double excessScore,
        double peerScore,
        double liquidityScore,
        double dataScore,
        Integer rankNo,
        Double rankPercentile,
        String recommendLevel,
        Double return60d,
        Double return120d,
        Double return250d,
        Double maxDrawdown120d,
        Double volatility120d,
        Double peerPercentile,
        String scoreDate,
        List<String> reasons,
        List<String> risks,
        String disclaimer
) {
}
