package com.lk.quantfund.strategy;

import com.lk.quantfund.enums.StrategyType;
import com.lk.quantfund.strategy.context.StrategyEvaluationContext;
import com.lk.quantfund.strategy.context.StrategySignalDraft;
import java.util.List;

public interface QuantStrategyRule {

    StrategyType strategyType();

    boolean supports(StrategyEvaluationContext context);

    List<StrategySignalDraft> evaluate(StrategyEvaluationContext context);
}

