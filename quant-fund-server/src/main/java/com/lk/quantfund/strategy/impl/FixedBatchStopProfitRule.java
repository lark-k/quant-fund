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
public class FixedBatchStopProfitRule implements QuantStrategyRule {

    @Override
    public StrategyType strategyType() {
        return StrategyType.FIXED_BATCH_STOP_PROFIT;
    }

    @Override
    public boolean supports(StrategyEvaluationContext context) {
        return FundType.INDEX.name().equals(context.classifiedFundType())
                || FundType.ETF.name().equals(context.classifiedFundType())
                || FundType.ETF_LINK.name().equals(context.classifiedFundType())
                || FundType.INDEX_ENHANCED.name().equals(context.classifiedFundType());
    }

    @Override
    public List<StrategySignalDraft> evaluate(StrategyEvaluationContext context) {
        BigDecimal profitRate = context.holding().getHoldingProfitRate();
        if (profitRate == null || profitRate.compareTo(new BigDecimal("8.0000")) < 0) {
            return List.of();
        }
        BigDecimal ratio = ratio(profitRate);
        BigDecimal amount = context.holding().getHoldingAmount().multiply(ratio).divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
        return List.of(new StrategySignalDraft(
                SignalType.TAKE_PROFIT,
                StrategyAction.SELL,
                "建议指数基金固定分批止盈",
                amount,
                ratio,
                RiskLevel.MEDIUM,
                new BigDecimal("0.7000"),
                List.of("指数/ETF 类持仓收益率达到 " + profitRate + "%", "按固定分批止盈规则给出减仓比例")
        ));
    }

    private BigDecimal ratio(BigDecimal profitRate) {
        if (profitRate.compareTo(new BigDecimal("35.0000")) >= 0) {
            return new BigDecimal("40.0000");
        }
        if (profitRate.compareTo(new BigDecimal("25.0000")) >= 0) {
            return new BigDecimal("30.0000");
        }
        if (profitRate.compareTo(new BigDecimal("15.0000")) >= 0) {
            return new BigDecimal("20.0000");
        }
        return new BigDecimal("10.0000");
    }
}

