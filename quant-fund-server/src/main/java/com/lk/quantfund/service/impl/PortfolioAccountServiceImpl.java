package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.portfolio.CreatePortfolioAccountRequest;
import com.lk.quantfund.dto.portfolio.UpdatePortfolioAccountRequest;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.enums.AccountStatus;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.FundType;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import com.lk.quantfund.vo.portfolio.PortfolioAccountVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioAccountServiceImpl implements PortfolioAccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    private final PortfolioAccountMapper portfolioAccountMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final FundNavDailyMapper fundNavDailyMapper;
    private final FundValuationService fundValuationService;

    public PortfolioAccountServiceImpl(PortfolioAccountMapper portfolioAccountMapper,
                                       FundHoldingMapper fundHoldingMapper,
                                       FundNavDailyMapper fundNavDailyMapper,
                                       FundValuationService fundValuationService) {
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.fundValuationService = fundValuationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioAccountVO create(CreatePortfolioAccountRequest request) {
        Long userId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        PortfolioAccount account = new PortfolioAccount();
        account.setUserId(userId);
        account.setAccountName(request.accountName().trim());
        account.setPlatformType(request.platformType().name());
        account.setTotalAsset(ZERO);
        account.setTotalInvestAmount(ZERO);
        account.setCurrentProfit(ZERO);
        account.setCurrentProfitRate(ZERO);
        account.setDailyProfit(ZERO);
        account.setCashPositionRate(ZERO);
        account.setEquityPositionRate(ZERO);
        account.setBondPositionRate(ZERO);
        account.setMaxSingleFundPositionRate(valueOrZero(request.maxSingleFundPositionRate()));
        account.setStatus(AccountStatus.ENABLED.name());
        account.setCreateTime(now);
        account.setUpdateTime(now);
        account.setDeleted(0);
        portfolioAccountMapper.insert(account);
        return toVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioAccountVO update(Long accountId, UpdatePortfolioAccountRequest request) {
        PortfolioAccount account = loadOwnedAccount(UserContext.getUserId(), accountId);
        account.setAccountName(request.accountName().trim());
        account.setPlatformType(request.platformType().name());
        account.setStatus(request.status().name());
        account.setMaxSingleFundPositionRate(valueOrZero(request.maxSingleFundPositionRate()));
        account.setUpdateTime(LocalDateTime.now());
        portfolioAccountMapper.updateById(account);
        return toVO(account);
    }

    @Override
    public PortfolioAccountVO detail(Long accountId) {
        return toVO(loadOwnedAccount(UserContext.getUserId(), accountId));
    }

    @Override
    public List<PortfolioAccountVO> list() {
        Long userId = UserContext.getUserId();
        return portfolioAccountMapper.selectList(new LambdaQueryWrapper<PortfolioAccount>()
                        .eq(PortfolioAccount::getUserId, userId)
                        .orderByDesc(PortfolioAccount::getUpdateTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public PortfolioSummaryVO summary() {
        List<PortfolioAccountVO> accounts = list();
        BigDecimal totalAsset = sumAccounts(accounts, PortfolioAccountVO::totalAsset);
        BigDecimal totalInvest = sumAccounts(accounts, PortfolioAccountVO::totalInvestAmount);
        BigDecimal profit = sumAccounts(accounts, PortfolioAccountVO::currentProfit);
        BigDecimal dailyProfit = sumAccounts(accounts, PortfolioAccountVO::dailyProfit);
        BigDecimal equityAmount = sumWeightedPosition(accounts, PortfolioAccountVO::equityPositionRate);
        BigDecimal bondAmount = sumWeightedPosition(accounts, PortfolioAccountVO::bondPositionRate);
        int holdingCount = fundHoldingMapper.selectCount(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, UserContext.getUserId())).intValue();
        return new PortfolioSummaryVO(
                totalAsset,
                totalInvest,
                profit,
                rate(profit, totalInvest),
                dailyProfit,
                rate(equityAmount, totalAsset),
                rate(bondAmount, totalAsset),
                ZERO,
                holdingCount,
                accounts
        );
    }

    @Override
    public List<FundHoldingVO> holdings(Long accountId) {
        Long userId = UserContext.getUserId();
        loadOwnedAccount(userId, accountId);
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getUserId, userId)
                        .eq(FundHolding::getAccountId, accountId)
                        .orderByDesc(FundHolding::getHoldingAmount))
                .stream()
                .map(this::toHoldingVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioAccountVO recalculate(Long accountId) {
        Long userId = UserContext.getUserId();
        recalculateOwnedAccount(userId, accountId);
        return toVO(loadOwnedAccount(userId, accountId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateOwnedAccount(Long userId, Long accountId) {
        PortfolioAccount account = loadOwnedAccount(userId, accountId);
        List<FundHolding> holdings = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId)
                .eq(FundHolding::getAccountId, accountId));
        BigDecimal totalAsset = sumHoldings(holdings, FundHolding::getHoldingAmount);
        BigDecimal totalInvest = sumHoldings(holdings, FundHolding::getHoldingCost);
        BigDecimal profit = sumHoldings(holdings, FundHolding::getHoldingProfit);
        BigDecimal dailyProfit = sumHoldings(holdings, FundHolding::getDailyProfit);
        BigDecimal equityAmount = holdings.stream()
                .filter(holding -> isEquityType(holding.getFundType()))
                .map(FundHolding::getHoldingAmount)
                .map(this::valueOrZero)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal bondAmount = holdings.stream()
                .filter(holding -> isBondType(holding.getFundType()))
                .map(FundHolding::getHoldingAmount)
                .map(this::valueOrZero)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal maxSingleAmount = holdings.stream()
                .map(FundHolding::getHoldingAmount)
                .map(this::valueOrZero)
                .max(BigDecimal::compareTo)
                .orElse(ZERO);

        account.setTotalAsset(scale(totalAsset));
        account.setTotalInvestAmount(scale(totalInvest));
        account.setCurrentProfit(scale(profit));
        account.setCurrentProfitRate(rate(profit, totalInvest));
        account.setDailyProfit(scale(dailyProfit));
        account.setCashPositionRate(ZERO);
        account.setEquityPositionRate(rate(equityAmount, totalAsset));
        account.setBondPositionRate(rate(bondAmount, totalAsset));
        account.setMaxSingleFundPositionRate(rate(maxSingleAmount, totalAsset));
        account.setUpdateTime(LocalDateTime.now());
        portfolioAccountMapper.updateById(account);
    }

    private PortfolioAccount loadOwnedAccount(Long userId, Long accountId) {
        PortfolioAccount account = portfolioAccountMapper.selectOne(new LambdaQueryWrapper<PortfolioAccount>()
                .eq(PortfolioAccount::getId, accountId)
                .eq(PortfolioAccount::getUserId, userId)
                .last("LIMIT 1"));
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "portfolio account not found");
        }
        return account;
    }

    private PortfolioAccountVO toVO(PortfolioAccount account) {
        return new PortfolioAccountVO(
                account.getId(),
                account.getAccountName(),
                account.getPlatformType(),
                valueOrZero(account.getTotalAsset()),
                valueOrZero(account.getTotalInvestAmount()),
                valueOrZero(account.getCurrentProfit()),
                valueOrZero(account.getCurrentProfitRate()),
                valueOrZero(account.getDailyProfit()),
                valueOrZero(account.getCashPositionRate()),
                valueOrZero(account.getEquityPositionRate()),
                valueOrZero(account.getBondPositionRate()),
                valueOrZero(account.getMaxSingleFundPositionRate()),
                account.getStatus(),
                account.getUpdateTime()
        );
    }

    private FundHoldingVO toHoldingVO(FundHolding holding) {
        BigDecimal accountTotal = accountTotal(holding.getAccountId());
        BigDecimal estimateRate = currentEstimateGrowthRate(holding);
        FundNavDaily officialNav = latestOfficialNav(holding.getFundCode());
        boolean officialUpdated = officialNavUpdated(holding, officialNav);
        BigDecimal dailyProfit = delayedOfficialNavFund(holding) && !officialUpdated ? ZERO : valueOrZero(holding.getDailyProfit());
        FundValuationResult valuation = fundValuationService.estimate(
                holding.getFundCode(), holding.getFundName(), holding.getFundType(), estimateRate);
        return new FundHoldingVO(
                holding.getId(),
                holding.getAccountId(),
                holding.getFundCode(),
                holding.getFundName(),
                holding.getFundType(),
                toBool(holding.getActiveFund()),
                valueOrZero(holding.getHoldingAmount()),
                valueOrZero(holding.getHoldingShare()),
                valueOrZero(holding.getHoldingCost()),
                holding.getCurrentEstimateNav(),
                holding.getLatestOfficialNav(),
                valueOrZero(holding.getHoldingProfit()),
                valueOrZero(holding.getHoldingProfitRate()),
                dailyProfit,
                ZERO,
                rate(valueOrZero(holding.getHoldingAmount()), accountTotal),
                estimateRate,
                officialUpdated,
                officialUpdated ? officialNav.getNavDate() : null,
                valuation.themeName(),
                valuation.themeRate(),
                valuation.sourceName(),
                valuation.basis(),
                valuation.marketStatus(),
                holding.getHoldingDays(),
                holding.getSourcePlatform(),
                toBool(holding.getRegularInvestment()),
                toBool(holding.getCoreHolding()),
                toBool(holding.getWatchFocus()),
                holding.getUpdateTime(),
                SystemConstants.DISCLAIMER
        );
    }

    private BigDecimal accountTotal(Long accountId) {
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getAccountId, accountId))
                .stream()
                .map(FundHolding::getHoldingAmount)
                .map(this::valueOrZero)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal currentEstimateGrowthRate(FundHolding holding) {
        FundNavDaily officialNav = latestOfficialNav(holding.getFundCode());
        if (officialNavUpdated(holding, officialNav) && officialNav.getDailyGrowthRate() != null) {
            return valueOrZero(officialNav.getDailyGrowthRate());
        }
        if (delayedOfficialNavFund(holding)) {
            return ZERO;
        }
        if (holding.getCurrentEstimateNav() == null || holding.getLatestOfficialNav() == null
                || holding.getLatestOfficialNav().compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return rate(holding.getCurrentEstimateNav().subtract(holding.getLatestOfficialNav()), holding.getLatestOfficialNav());
    }

    private boolean officialNavUpdated(FundHolding holding, FundNavDaily nav) {
        if (nav == null) {
            return false;
        }
        return nav.getNavDate() != null && nav.getNavDate().equals(LocalDate.now());
    }

    private FundNavDaily latestOfficialNav(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) {
            return null;
        }
        return fundNavDailyMapper.selectOne(new LambdaQueryWrapper<FundNavDaily>()
                .eq(FundNavDaily::getFundCode, fundCode)
                .orderByDesc(FundNavDaily::getNavDate)
                .last("LIMIT 1"));
    }

    private boolean delayedOfficialNavFund(FundHolding holding) {
        String name = holding.getFundName() == null ? "" : holding.getFundName().toUpperCase();
        String type = holding.getFundType() == null ? "" : holding.getFundType().toUpperCase();
        if (name.contains("恒生") || name.contains("港股") || name.contains("香港") || name.contains("H股")) {
            return false;
        }
        return type.contains("QDII")
                || name.contains("QDII")
                || name.contains("全球")
                || name.contains("海外")
                || name.contains("美国")
                || name.contains("美股")
                || name.contains("纳指")
                || name.contains("纳斯达克")
                || name.contains("标普")
                || name.contains("道琼斯")
                || name.contains("美元")
                || name.contains("人民币");
    }

    private BigDecimal sumAccounts(List<PortfolioAccountVO> accounts,
                                   java.util.function.Function<PortfolioAccountVO, BigDecimal> mapper) {
        return accounts.stream().map(mapper).map(this::valueOrZero).reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal sumWeightedPosition(List<PortfolioAccountVO> accounts,
                                           java.util.function.Function<PortfolioAccountVO, BigDecimal> mapper) {
        return accounts.stream()
                .map(account -> valueOrZero(account.totalAsset()).multiply(valueOrZero(mapper.apply(account))).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal sumHoldings(List<FundHolding> holdings,
                                   java.util.function.Function<FundHolding, BigDecimal> mapper) {
        return holdings.stream().map(mapper).map(this::valueOrZero).reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal base = valueOrZero(denominator);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return valueOrZero(numerator).multiply(ONE_HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private boolean isEquityType(String fundType) {
        return FundType.ACTIVE_EQUITY.name().equals(fundType)
                || FundType.INDEX.name().equals(fundType)
                || FundType.ETF.name().equals(fundType)
                || FundType.ETF_LINK.name().equals(fundType)
                || FundType.INDEX_ENHANCED.name().equals(fundType)
                || FundType.MIXED.name().equals(fundType)
                || FundType.QDII.name().equals(fundType);
    }

    private boolean isBondType(String fundType) {
        return FundType.BOND.name().equals(fundType)
                || FundType.FIXED_INCOME_PLUS.name().equals(fundType)
                || FundType.MONEY_MARKET.name().equals(fundType);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : scale(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private Boolean toBool(Integer value) {
        return value != null && value == 1;
    }
}
