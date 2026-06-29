package com.lk.quantfund.ai.model;

import com.lk.quantfund.vo.strategy.StrategySignalVO;
import com.lk.quantfund.vo.quant.QuantSignalVO;
import java.math.BigDecimal;
import java.util.List;

public record AiAnalysisContext(
        Long accountId,
        Long holdingId,
        String fundCode,
        String fundName,
        String fundType,
        Boolean activeFund,
        BigDecimal currentEstimateNav,
        BigDecimal latestOfficialNav,
        BigDecimal holdingAmount,
        BigDecimal holdingShare,
        BigDecimal holdingCost,
        BigDecimal holdingProfit,
        BigDecimal holdingProfitRate,
        BigDecimal dailyProfit,
        Integer holdingDays,
        BigDecimal accountTotalAsset,
        BigDecimal accountEquityPositionRate,
        BigDecimal accountSingleFundPositionRate,
        BigDecimal maxEquityPositionRate,
        BigDecimal maxSingleFundPositionRate,
        BigDecimal dailyRiseAlertRate,
        BigDecimal drawdownAlertRate,
        String userRiskLevel,
        QuantSignalVO latestQuantSignal,
        List<StrategySignalVO> strategySignals
) {
}
