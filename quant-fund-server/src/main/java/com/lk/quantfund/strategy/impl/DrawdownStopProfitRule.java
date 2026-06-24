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
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DrawdownStopProfitRule implements QuantStrategyRule {

    @Override
    public StrategyType strategyType() {
        return StrategyType.DRAWDOWN_STOP_PROFIT;
    }

    @Override
    public boolean supports(StrategyEvaluationContext context) {
        return FundType.ACTIVE_EQUITY.name().equals(context.classifiedFundType())
                || FundType.MIXED.name().equals(context.classifiedFundType());
    }

    @Override
    public List<StrategySignalDraft> evaluate(StrategyEvaluationContext context) {
        BigDecimal profitRate = context.holding().getHoldingProfitRate();
        BigDecimal drawdown = context.currentDrawdownRate();
        if (profitRate == null || drawdown == null || profitRate.compareTo(new BigDecimal("15.0000")) < 0) {
            return List.of();
        }
        if (drawdown.compareTo(new BigDecimal("8.0000")) >= 0) {
            return List.of(signal(context, "建议重度减仓", new BigDecimal("30.0000"), RiskLevel.HIGH, "持有收益率超过 15%，且从近 90 日高点回撤达到 8%"));
        }
        if (drawdown.compareTo(new BigDecimal("5.0000")) >= 0) {
            return List.of(signal(context, "建议中度减仓", new BigDecimal("20.0000"), RiskLevel.HIGH, "持有收益率超过 15%，且从近 90 日高点回撤达到 5%"));
        }
        if (drawdown.compareTo(new BigDecimal("3.0000")) >= 0) {
            return List.of(signal(context, "建议轻度减仓", new BigDecimal("10.0000"), RiskLevel.MEDIUM, "持有收益率超过 15%，且从近 90 日高点回撤达到 3%"));
        }
        return List.of();
    }

    private StrategySignalDraft signal(StrategyEvaluationContext context, String text, BigDecimal ratio, RiskLevel riskLevel, String reason) {
        BigDecimal amount = context.holding().getHoldingAmount().multiply(ratio).divide(new BigDecimal("100.0000"), 4, java.math.RoundingMode.HALF_UP);
        return new StrategySignalDraft(
                SignalType.TAKE_PROFIT,
                StrategyAction.SELL,
                text,
                amount,
                ratio,
                riskLevel,
                new BigDecimal("0.7800"),
                List.of(reason, "该策略使用净值回撤近似收益回撤，最终仍需人工确认")
        );
    }
}

