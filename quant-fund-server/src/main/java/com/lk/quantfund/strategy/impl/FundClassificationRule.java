package com.lk.quantfund.strategy.impl;

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
public class FundClassificationRule implements QuantStrategyRule {

    @Override
    public StrategyType strategyType() {
        return StrategyType.FUND_CLASSIFICATION;
    }

    @Override
    public boolean supports(StrategyEvaluationContext context) {
        return true;
    }

    @Override
    public List<StrategySignalDraft> evaluate(StrategyEvaluationContext context) {
        return List.of(new StrategySignalDraft(
                SignalType.CLASSIFICATION,
                StrategyAction.HOLD,
                "基金分类已更新",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RiskLevel.LOW,
                new BigDecimal("0.6500"),
                List.of("当前基金分类为 " + context.classifiedFundType(), "分类结果可人工修正，后续策略按该分类选择规则")
        ));
    }
}

