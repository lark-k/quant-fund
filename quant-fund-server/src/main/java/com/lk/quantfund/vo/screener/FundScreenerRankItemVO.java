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
        double returnQualityScore,
        double drawdownControlScore,
        double consistencyScore,
        double investabilityScore,
        Integer rankNo,
        Double rankPercentile,
        String recommendLevel,
        Double return60d,
        Double return120d,
        Double return250d,
        Double maxDrawdown120d,
        Double volatility120d,
        Double peerPercentile,
        Double returnDrawdownRatio120d,
        Double returnConsistencyScore,
        String benchmarkCode,
        String scoreDate,
        List<String> reasons,
        List<String> risks,
        String disclaimer
) {
}
