package com.lk.quantfund.vo.screener;

public record FundScreenerBacktestMetricVO(
        String bucketName,
        int horizonDays,
        int sampleCount,
        int scoreDateCount,
        double avgForwardReturn,
        double winRate,
        double avgExcessReturn,
        double maxDrawdown,
        boolean statisticallySignificant
) {
}
