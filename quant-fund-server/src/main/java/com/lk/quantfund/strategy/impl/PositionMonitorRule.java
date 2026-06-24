package com.lk.quantfund.strategy.impl;

import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.SignalType;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.enums.StrategyType;
import com.lk.quantfund.strategy.QuantStrategyRule;
import com.lk.quantfund.strategy.context.StrategyEvaluationContext;
import com.lk.quantfund.strategy.context.StrategySignalDraft;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PositionMonitorRule implements QuantStrategyRule {

    @Override
    public StrategyType strategyType() {
        return StrategyType.POSITION_MONITOR;
    }

    @Override
    public boolean supports(StrategyEvaluationContext context) {
        return true;
    }

    @Override
    public List<StrategySignalDraft> evaluate(StrategyEvaluationContext context) {
        List<StrategySignalDraft> signals = new ArrayList<>();
        BigDecimal equityRate = context.account().getEquityPositionRate();
        BigDecimal maxEquityRate = context.riskProfile().getMaxEquityPositionRate();
        if (equityRate != null && maxEquityRate != null && equityRate.compareTo(maxEquityRate) > 0) {
            signals.add(new StrategySignalDraft(
                    SignalType.POSITION_RISK,
                    StrategyAction.WATCH,
                    "权益仓位偏高，暂不建议继续加仓",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    RiskLevel.HIGH,
                    new BigDecimal("0.7600"),
                    List.of("账户权益基金仓位 " + equityRate + "% 已超过风险偏好上限 " + maxEquityRate + "%")
            ));
        }
        BigDecimal totalAsset = context.account().getTotalAsset();
        BigDecimal holdingAmount = context.holding().getHoldingAmount();
        if (totalAsset != null && holdingAmount != null && totalAsset.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal singleRate = holdingAmount.multiply(new BigDecimal("100.0000")).divide(totalAsset, 4, java.math.RoundingMode.HALF_UP);
            BigDecimal maxSingleRate = context.riskProfile().getMaxSingleFundPositionRate();
            if (maxSingleRate != null && singleRate.compareTo(maxSingleRate) > 0) {
                signals.add(new StrategySignalDraft(
                        SignalType.POSITION_RISK,
                        StrategyAction.WATCH,
                        "单只基金仓位偏高，注意集中度风险",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        RiskLevel.HIGH,
                        new BigDecimal("0.8000"),
                        List.of("该基金占账户总资产 " + singleRate + "%，超过单基金上限 " + maxSingleRate + "%")
                ));
            }
        }
        return signals;
    }
}

