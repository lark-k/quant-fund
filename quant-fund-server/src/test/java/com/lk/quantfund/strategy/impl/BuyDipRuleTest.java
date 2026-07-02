package com.lk.quantfund.strategy.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.RiskProfile;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.strategy.context.StrategyEvaluationContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuyDipRuleTest {

    private final BuyDipRule rule = new BuyDipRule();

    @Test
    void evaluateShouldCalculateBuyAmountFromCurrentHoldingAmount() {
        PortfolioAccount account = new PortfolioAccount();
        account.setTotalAsset(new BigDecimal("100000.0000"));
        account.setEquityPositionRate(new BigDecimal("45.0000"));

        FundHolding holding = new FundHolding();
        holding.setHoldingAmount(new BigDecimal("2000.0000"));

        RiskProfile riskProfile = new RiskProfile();
        riskProfile.setMaxEquityPositionRate(new BigDecimal("70.0000"));

        StrategyEvaluationContext context = new StrategyEvaluationContext(
                1L,
                account,
                holding,
                riskProfile,
                List.of(),
                BigDecimal.ONE,
                new BigDecimal("1.1200"),
                new BigDecimal("10.0000"),
                "ACTIVE_EQUITY"
        );

        var signals = rule.evaluate(context);

        assertThat(signals).singleElement().satisfies(signal -> {
            assertThat(signal.action()).isEqualTo(StrategyAction.BUY);
            assertThat(signal.suggestRatio()).isEqualByComparingTo("5.0000");
            assertThat(signal.suggestAmount()).isEqualByComparingTo("100.0000");
        });
    }
}
