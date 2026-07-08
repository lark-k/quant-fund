package com.lk.quantfund.vo.screener;

public record FundScreenerScoreBreakdownVO(
        double returnScore,
        double riskScore,
        double stabilityScore,
        double excessScore,
        double peerScore,
        double liquidityScore,
        double dataScore
) {

    public static FundScreenerScoreBreakdownVO empty() {
        return new FundScreenerScoreBreakdownVO(0, 0, 0, 0, 0, 0, 0);
    }
}
