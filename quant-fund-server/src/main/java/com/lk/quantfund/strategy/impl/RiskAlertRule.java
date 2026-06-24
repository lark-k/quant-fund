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
public class RiskAlertRule implements QuantStrategyRule {

    @Override
    public StrategyType strategyType() {
        return StrategyType.RISK_ALERT;
    }

    @Override
    public boolean supports(StrategyEvaluationContext context) {
        return true;
    }

    @Override
    public List<StrategySignalDraft> evaluate(StrategyEvaluationContext context) {
        List<StrategySignalDraft> signals = new ArrayList<>();
        BigDecimal dailyProfit = context.holding().getDailyProfit();
        BigDecimal holdingAmount = context.holding().getHoldingAmount();
        if (dailyProfit != null && holdingAmount != null && holdingAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dailyRate = dailyProfit.multiply(new BigDecimal("100.0000")).divide(holdingAmount, 4, java.math.RoundingMode.HALF_UP);
            if (dailyRate.compareTo(context.riskProfile().getDailyRiseAlertRate()) > 0) {
                signals.add(watch("当日涨幅偏大，不建议追涨", "当日估算收益率约 " + dailyRate + "%，超过上涨提醒阈值"));
            }
            if (dailyRate.abs().compareTo(context.riskProfile().getDailyFallAlertRate()) > 0 && dailyRate.compareTo(BigDecimal.ZERO) < 0) {
                signals.add(watch("当日跌幅偏大，谨慎参考估值", "当日估算收益率约 " + dailyRate + "%，超过下跌提醒阈值"));
            }
        }
        if (context.currentDrawdownRate() != null && context.currentDrawdownRate().compareTo(context.riskProfile().getDrawdownAlertRate()) > 0) {
            signals.add(watch("基金回撤偏大，注意风险", "近 90 日净值回撤约 " + context.currentDrawdownRate() + "%"));
        }
        return signals;
    }

    private StrategySignalDraft watch(String text, String reason) {
        return new StrategySignalDraft(
                SignalType.MARKET_RISK,
                StrategyAction.WATCH,
                text,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RiskLevel.HIGH,
                new BigDecimal("0.7400"),
                List.of(reason, "当日估值只作为参考，晚间正式净值前不是最终净值")
        );
    }
}

