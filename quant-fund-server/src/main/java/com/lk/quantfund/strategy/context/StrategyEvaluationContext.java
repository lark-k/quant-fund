package com.lk.quantfund.strategy.context;

import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.RiskProfile;
import java.math.BigDecimal;
import java.util.List;

public record StrategyEvaluationContext(
        Long userId,
        PortfolioAccount account,
        FundHolding holding,
        RiskProfile riskProfile,
        List<FundNavDaily> recentNavList,
        BigDecimal currentNav,
        BigDecimal ninetyDayHighNav,
        BigDecimal currentDrawdownRate,
        String classifiedFundType
) {
}

