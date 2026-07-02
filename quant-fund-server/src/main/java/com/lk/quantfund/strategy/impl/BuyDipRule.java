package com.lk.quantfund.strategy.impl;

import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.SignalType;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.enums.StrategyType;
import com.lk.quantfund.strategy.QuantStrategyRule;
import com.lk.quantfund.strategy.context.StrategyEvaluationContext;
import com.lk.quantfund.strategy.context.StrategySignalDraft;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BuyDipRule implements QuantStrategyRule {

    @Override
    public StrategyType strategyType() {
        return StrategyType.BUY_DIP;
    }

    @Override
    public boolean supports(StrategyEvaluationContext context) {
        BigDecimal equityRate = context.account().getEquityPositionRate();
        BigDecimal maxEquityRate = context.riskProfile().getMaxEquityPositionRate();
        return equityRate == null || maxEquityRate == null || equityRate.compareTo(maxEquityRate) < 0;
    }

    @Override
    public List<StrategySignalDraft> evaluate(StrategyEvaluationContext context) {
        BigDecimal drawdown = context.currentDrawdownRate();
        if (drawdown == null || drawdown.compareTo(new BigDecimal("5.0000")) < 0) {
            return List.of();
        }
        BigDecimal ratio = drawdown.compareTo(new BigDecimal("15.0000")) >= 0
                ? new BigDecimal("8.0000")
                : drawdown.compareTo(new BigDecimal("10.0000")) >= 0 ? new BigDecimal("5.0000") : new BigDecimal("3.0000");
        BigDecimal base = context.holding().getHoldingAmount() == null ? BigDecimal.ZERO : context.holding().getHoldingAmount();
        BigDecimal amount = base.multiply(ratio).divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
        return List.of(new StrategySignalDraft(
                SignalType.ADD_POSITION,
                StrategyAction.BUY,
                "建议小额低吸观察",
                amount,
                ratio,
                drawdown.compareTo(new BigDecimal("15.0000")) >= 0 ? RiskLevel.HIGH : RiskLevel.MEDIUM,
                new BigDecimal("0.6200"),
                List.of("基金从近 90 日高点回撤 " + drawdown + "%", "账户权益仓位未超过当前风险偏好上限")
        ));
    }
}
