package com.lk.quantfund.strategy.impl;

import com.lk.quantfund.enums.FundType;
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
public class DynamicLadderStopProfitRule implements QuantStrategyRule {

    @Override
    public StrategyType strategyType() {
        return StrategyType.DYNAMIC_LADDER_STOP_PROFIT;
    }

    @Override
    public boolean supports(StrategyEvaluationContext context) {
        return FundType.ACTIVE_EQUITY.name().equals(context.classifiedFundType())
                || FundType.MIXED.name().equals(context.classifiedFundType());
    }

    @Override
    public List<StrategySignalDraft> evaluate(StrategyEvaluationContext context) {
        BigDecimal profitRate = context.holding().getHoldingProfitRate();
        if (profitRate == null || profitRate.compareTo(new BigDecimal("10.0000")) < 0) {
            return List.of();
        }
        BigDecimal ratio = ladderRatio(profitRate);
        BigDecimal accountPressure = context.account().getEquityPositionRate() != null ? context.account().getEquityPositionRate() : BigDecimal.ZERO;
        if (accountPressure.compareTo(new BigDecimal("70.0000")) > 0) {
            ratio = ratio.add(new BigDecimal("5.0000"));
        }
        if (context.holding().getCoreHolding() != null && context.holding().getCoreHolding() == 1) {
            ratio = ratio.subtract(new BigDecimal("5.0000")).max(new BigDecimal("5.0000"));
        }
        BigDecimal amount = context.holding().getHoldingAmount().multiply(ratio).divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
        return List.of(new StrategySignalDraft(
                SignalType.TAKE_PROFIT,
                StrategyAction.SELL,
                "建议分批止盈",
                amount,
                ratio,
                accountPressure.compareTo(new BigDecimal("70.0000")) > 0 ? RiskLevel.HIGH : RiskLevel.MEDIUM,
                new BigDecimal("0.7200"),
                List.of("主动型基金持有收益率达到 " + profitRate + "%", "按动态梯度止盈规则给出分批减仓比例")
        ));
    }

    private BigDecimal ladderRatio(BigDecimal profitRate) {
        if (profitRate.compareTo(new BigDecimal("50.0000")) >= 0) {
            return new BigDecimal("40.0000");
        }
        if (profitRate.compareTo(new BigDecimal("30.0000")) >= 0) {
            return new BigDecimal("30.0000");
        }
        if (profitRate.compareTo(new BigDecimal("20.0000")) >= 0) {
            return new BigDecimal("20.0000");
        }
        return new BigDecimal("10.0000");
    }
}

