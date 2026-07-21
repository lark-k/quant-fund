package com.lk.quantfund.scheduler;

import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.service.PortfolioAccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ScheduledFundEstimatePersistenceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountService portfolioAccountService;

    public ScheduledFundEstimatePersistenceService(FundHoldingMapper fundHoldingMapper,
                                                   PortfolioAccountService portfolioAccountService) {
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountService = portfolioAccountService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void apply(List<FundHolding> holdings, FundEstimateDTO estimate) {
        for (FundHolding holding : holdings) {
            applyEstimate(holding, estimate);
            fundHoldingMapper.updateById(holding);
        }
        holdings.stream()
                .map(FundHolding::getAccountId)
                .distinct()
                .forEach(accountId -> holdings.stream()
                        .filter(holding -> accountId.equals(holding.getAccountId()))
                        .map(FundHolding::getUserId)
                        .findFirst()
                        .ifPresent(userId -> portfolioAccountService.recalculateOwnedAccount(userId, accountId)));
    }

    private void applyEstimate(FundHolding holding, FundEstimateDTO estimate) {
        BigDecimal estimateNav = scale(estimate.estimateNav());
        BigDecimal frozenHoldingAmount = scale(holding.getHoldingAmount());
        holding.setCurrentEstimateNav(estimateNav);
        holding.setHoldingAmount(frozenHoldingAmount);
        BigDecimal estimateRate = estimate.estimateGrowthRate();
        if (holding.getLatestOfficialNav() != null
                && holding.getLatestOfficialNav().compareTo(BigDecimal.ZERO) > 0) {
            estimateRate = rate(estimateNav.subtract(holding.getLatestOfficialNav()), holding.getLatestOfficialNav());
        }
        BigDecimal dailyProfit = amountChangeByRate(frozenHoldingAmount, estimateRate);
        holding.setDailyProfit(dailyProfit);
        BigDecimal estimatedProfit = frozenHoldingAmount.add(dailyProfit).subtract(scale(holding.getHoldingCost()));
        holding.setHoldingProfit(scale(estimatedProfit));
        holding.setHoldingProfitRate(rate(holding.getHoldingProfit(), holding.getHoldingCost()));
        holding.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(estimate.fundName())) {
            holding.setFundName(estimate.fundName());
        }
    }

    private BigDecimal amountChangeByRate(BigDecimal amount, BigDecimal changeRate) {
        if (changeRate == null) {
            return ZERO;
        }
        return scale(amount).multiply(changeRate).divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal change, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return scale(change).multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }
}
