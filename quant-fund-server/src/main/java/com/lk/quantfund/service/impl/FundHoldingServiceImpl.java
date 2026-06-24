package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.dto.holding.CreateHoldingRequest;
import com.lk.quantfund.dto.holding.UpdateHoldingRequest;
import com.lk.quantfund.entity.AiAnalysisReport;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.AiAnalysisReportMapper;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.scheduler.HoldingSnapshotBackfillService;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundHoldingService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FundHoldingServiceImpl implements FundHoldingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final AiAnalysisReportMapper aiAnalysisReportMapper;
    private final PortfolioAccountService portfolioAccountService;
    private final FundQueryService fundQueryService;
    private final FundValuationService fundValuationService;
    private final TradingCalendarService tradingCalendarService;
    private final HoldingSnapshotMapper holdingSnapshotMapper;
    private final HoldingSnapshotBackfillService holdingSnapshotBackfillService;

    public FundHoldingServiceImpl(FundHoldingMapper fundHoldingMapper,
                                  PortfolioAccountMapper portfolioAccountMapper,
                                  AiAnalysisReportMapper aiAnalysisReportMapper,
                                  PortfolioAccountService portfolioAccountService,
                                  FundQueryService fundQueryService,
                                  FundValuationService fundValuationService,
                                  TradingCalendarService tradingCalendarService,
                                  HoldingSnapshotMapper holdingSnapshotMapper,
                                  HoldingSnapshotBackfillService holdingSnapshotBackfillService) {
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.aiAnalysisReportMapper = aiAnalysisReportMapper;
        this.portfolioAccountService = portfolioAccountService;
        this.fundQueryService = fundQueryService;
        this.fundValuationService = fundValuationService;
        this.tradingCalendarService = tradingCalendarService;
        this.holdingSnapshotMapper = holdingSnapshotMapper;
        this.holdingSnapshotBackfillService = holdingSnapshotBackfillService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundHoldingVO create(CreateHoldingRequest request) {
        Long userId = UserContext.getUserId();
        ensureAccountOwned(userId, request.accountId());
        LocalDateTime now = LocalDateTime.now();
        FundHolding holding = new FundHolding();
        holding.setUserId(userId);
        holding.setAccountId(request.accountId());
        applyCreateFields(holding, request);
        refreshMarketData(holding);
        recalculateEntity(holding);
        holding.setCreateTime(now);
        holding.setUpdateTime(now);
        holding.setDeleted(0);
        fundHoldingMapper.insert(holding);
        portfolioAccountService.recalculateOwnedAccount(userId, request.accountId());
        return toVO(holding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundHoldingVO update(Long holdingId, UpdateHoldingRequest request) {
        Long userId = UserContext.getUserId();
        FundHolding holding = loadOwnedHolding(userId, holdingId);
        Long oldAccountId = holding.getAccountId();
        ensureAccountOwned(userId, request.accountId());
        applyUpdateFields(holding, request);
        refreshMarketData(holding);
        recalculateEntity(holding);
        holding.setUpdateTime(LocalDateTime.now());
        fundHoldingMapper.updateById(holding);
        portfolioAccountService.recalculateOwnedAccount(userId, holding.getAccountId());
        if (!oldAccountId.equals(holding.getAccountId())) {
            portfolioAccountService.recalculateOwnedAccount(userId, oldAccountId);
        }
        return toVO(holding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long holdingId) {
        Long userId = UserContext.getUserId();
        FundHolding holding = loadOwnedHolding(userId, holdingId);
        aiAnalysisReportMapper.delete(new LambdaQueryWrapper<AiAnalysisReport>()
                .eq(AiAnalysisReport::getUserId, userId)
                .eq(AiAnalysisReport::getHoldingId, holdingId));
        fundHoldingMapper.deleteById(holdingId);
        portfolioAccountService.recalculateOwnedAccount(userId, holding.getAccountId());
    }

    @Override
    public FundHoldingVO detail(Long holdingId) {
        Long userId = UserContext.getUserId();
        holdingSnapshotBackfillService.ensureRecentSnapshots(userId);
        return toVO(loadOwnedHolding(userId, holdingId));
    }

    @Override
    public List<FundHoldingVO> list(Long accountId, String fundCode) {
        Long userId = UserContext.getUserId();
        holdingSnapshotBackfillService.ensureRecentSnapshots(userId);
        LambdaQueryWrapper<FundHolding> wrapper = new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId);
        if (accountId != null) {
            ensureAccountOwned(userId, accountId);
            wrapper.eq(FundHolding::getAccountId, accountId);
        }
        if (StringUtils.hasText(fundCode)) {
            wrapper.eq(FundHolding::getFundCode, fundCode.trim());
        }
        wrapper.orderByDesc(FundHolding::getHoldingAmount);
        return fundHoldingMapper.selectList(wrapper).stream()
                .map(holding -> toVO(holding, accountTotal(holding.getAccountId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundHoldingVO recalculate(Long holdingId) {
        Long userId = UserContext.getUserId();
        FundHolding holding = loadOwnedHolding(userId, holdingId);
        refreshMarketData(holding);
        recalculateEntity(holding);
        holding.setUpdateTime(LocalDateTime.now());
        fundHoldingMapper.updateById(holding);
        portfolioAccountService.recalculateOwnedAccount(userId, holding.getAccountId());
        return toVO(holding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FundHoldingVO> syncOfficialNav() {
        Long userId = UserContext.getUserId();
        List<FundHolding> holdings = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId)
                .orderByAsc(FundHolding::getFundCode));
        List<Long> touchedAccountIds = new java.util.ArrayList<>();
        for (FundHolding holding : holdings) {
            Optional<OfficialNavContext> officialNav = officialNavContext(holding.getFundCode());
            if (officialNav.isEmpty() || !officialNavPublishedFor(holding, officialNav.get())) {
                continue;
            }
            applyOfficialNav(holding, officialNav.get());
            holding.setUpdateTime(LocalDateTime.now());
            fundHoldingMapper.updateById(holding);
            if (!touchedAccountIds.contains(holding.getAccountId())) {
                touchedAccountIds.add(holding.getAccountId());
            }
        }
        touchedAccountIds.forEach(accountId -> portfolioAccountService.recalculateOwnedAccount(userId, accountId));
        return list(null, null);
    }

    private void applyCreateFields(FundHolding holding, CreateHoldingRequest request) {
        holding.setFundCode(request.fundCode().trim());
        holding.setFundName(request.fundName().trim());
        holding.setFundType(request.fundType().name());
        holding.setActiveFund(toInt(request.activeFund()));
        holding.setHoldingAmount(valueOrZero(request.holdingAmount()));
        holding.setHoldingShare(valueOrZero(request.holdingShare()));
        holding.setHoldingCost(valueOrZero(request.holdingCost()));
        applyUserProfitInput(holding, request.holdingProfit());
        holding.setCurrentEstimateNav(null);
        holding.setLatestOfficialNav(null);
        holding.setHoldingDays(0);
        holding.setSourcePlatform(trimToNull(request.sourcePlatform()));
        holding.setRegularInvestment(toInt(request.regularInvestment()));
        holding.setCoreHolding(toInt(request.coreHolding()));
        holding.setWatchFocus(toInt(request.watchFocus()));
    }

    private void applyUpdateFields(FundHolding holding, UpdateHoldingRequest request) {
        BigDecimal previousCurrentEstimateNav = holding.getCurrentEstimateNav();
        BigDecimal previousLatestOfficialNav = holding.getLatestOfficialNav();
        holding.setAccountId(request.accountId());
        holding.setFundCode(request.fundCode().trim());
        holding.setFundName(request.fundName().trim());
        holding.setFundType(request.fundType().name());
        holding.setActiveFund(toInt(request.activeFund()));
        holding.setHoldingAmount(valueOrZero(request.holdingAmount()));
        holding.setHoldingShare(valueOrZero(request.holdingShare()));
        holding.setHoldingCost(valueOrZero(request.holdingCost()));
        applyUserProfitInput(holding, request.holdingProfit());
        deriveShareFromAmountIfNeeded(holding, previousCurrentEstimateNav, previousLatestOfficialNav);
        holding.setCurrentEstimateNav(null);
        holding.setLatestOfficialNav(null);
        holding.setSourcePlatform(trimToNull(request.sourcePlatform()));
        holding.setRegularInvestment(toInt(request.regularInvestment()));
        holding.setCoreHolding(toInt(request.coreHolding()));
        holding.setWatchFocus(toInt(request.watchFocus()));
    }

    private void recalculateEntity(FundHolding holding) {
        ensureEnoughInput(holding);
        BigDecimal amount = estimateAmount(holding);
        BigDecimal nav = holding.getCurrentEstimateNav() != null ? holding.getCurrentEstimateNav() : holding.getLatestOfficialNav();
        if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0
                && valueOrZero(holding.getHoldingShare()).compareTo(BigDecimal.ZERO) <= 0
                && amount.compareTo(BigDecimal.ZERO) > 0) {
            holding.setHoldingShare(amount.divide(nav, 4, RoundingMode.HALF_UP));
        }
        BigDecimal profit = amount.subtract(valueOrZero(holding.getHoldingCost()));
        holding.setHoldingAmount(amount);
        holding.setHoldingProfit(scale(profit));
        holding.setHoldingProfitRate(rate(profit, holding.getHoldingCost()));
        holding.setDailyProfit(calculateDailyProfit(holding));
    }

    private void applyUserProfitInput(FundHolding holding, BigDecimal holdingProfit) {
        if (holdingProfit == null) {
            return;
        }
        BigDecimal amount = valueOrZero(holding.getHoldingAmount());
        holding.setHoldingCost(maxZero(amount.subtract(holdingProfit)));
    }

    private void deriveShareFromAmountIfNeeded(FundHolding holding, BigDecimal currentEstimateNav, BigDecimal latestOfficialNav) {
        if (valueOrZero(holding.getHoldingShare()).compareTo(BigDecimal.ZERO) > 0
                || valueOrZero(holding.getHoldingAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal nav = currentEstimateNav != null ? currentEstimateNav : latestOfficialNav;
        if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0) {
            holding.setHoldingShare(valueOrZero(holding.getHoldingAmount()).divide(nav, 4, RoundingMode.HALF_UP));
        }
    }

    private void ensureEnoughInput(FundHolding holding) {
        BigDecimal amount = valueOrZero(holding.getHoldingAmount());
        BigDecimal share = valueOrZero(holding.getHoldingShare());
        BigDecimal cost = valueOrZero(holding.getHoldingCost());
        if (amount.compareTo(BigDecimal.ZERO) <= 0 && share.compareTo(BigDecimal.ZERO) <= 0 && cost.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0 && share.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "holding amount or holding share is required");
        }
        if (cost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "holding cost or holding profit is required");
        }
    }

    private BigDecimal estimateAmount(FundHolding holding) {
        BigDecimal nav = holding.getCurrentEstimateNav() != null ? holding.getCurrentEstimateNav() : holding.getLatestOfficialNav();
        if (nav != null && valueOrZero(holding.getHoldingShare()).compareTo(BigDecimal.ZERO) > 0) {
            return scale(valueOrZero(holding.getHoldingShare()).multiply(nav));
        }
        return valueOrZero(holding.getHoldingAmount());
    }

    private void refreshMarketData(FundHolding holding) {
        Optional<OfficialNavContext> officialNav = officialNavContext(holding.getFundCode());
        if (officialNav.isPresent() && officialNavPublishedFor(holding, officialNav.get())) {
            applyOfficialNav(holding, officialNav.get());
            return;
        }
        if (intradayEstimateAllowed(holding)) {
            try {
                FundEstimateDTO estimate = fundQueryService.getIntradayEstimate(holding.getFundCode(), false);
                if (StringUtils.hasText(estimate.fundName())) {
                    holding.setFundName(estimate.fundName());
                }
                holding.setCurrentEstimateNav(estimate.estimateNav());
                BigDecimal latestOfficialNav = deriveOfficialNav(estimate)
                        .or(() -> latestOfficialNav(holding.getFundCode()))
                        .orElse(holding.getLatestOfficialNav());
                holding.setLatestOfficialNav(latestOfficialNav);
                return;
            } catch (RuntimeException ignored) {
                // Estimates can be unavailable before data opens; official NAV is still useful.
            }
        }
        latestOfficialNav(holding.getFundCode()).ifPresent(holding::setLatestOfficialNav);
        holding.setCurrentEstimateNav(null);
    }

    private Optional<BigDecimal> deriveOfficialNav(FundEstimateDTO estimate) {
        if (estimate.estimateNav() == null || estimate.estimateGrowthRate() == null) {
            return Optional.empty();
        }
        BigDecimal factor = BigDecimal.ONE.add(estimate.estimateGrowthRate().divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
        if (factor.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(scale(estimate.estimateNav().divide(factor, 4, RoundingMode.HALF_UP)));
    }

    private Optional<BigDecimal> latestOfficialNav(String fundCode) {
        try {
            return fundQueryService.getHistoricalNav(fundCode, LocalDate.now().minusDays(15), LocalDate.now()).stream()
                    .filter(point -> point.unitNav() != null)
                    .max(Comparator.comparing(FundNavPointDTO::navDate))
                    .map(FundNavPointDTO::unitNav);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<OfficialNavContext> officialNavContext(String fundCode) {
        try {
            List<FundNavPointDTO> points = fundQueryService.getHistoricalNav(fundCode, LocalDate.now().minusDays(15), LocalDate.now()).stream()
                    .filter(point -> point.unitNav() != null)
                    .sorted(Comparator.comparing(FundNavPointDTO::navDate))
                    .toList();
            if (points.isEmpty()) {
                return Optional.empty();
            }
            FundNavPointDTO latest = points.get(points.size() - 1);
            FundNavPointDTO previous = points.size() > 1 ? points.get(points.size() - 2) : null;
            return Optional.of(new OfficialNavContext(
                    latest.navDate(),
                    scale(latest.unitNav()),
                    previous == null ? null : scale(previous.unitNav()),
                    latest.dailyGrowthRate()
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private BigDecimal calculateDailyProfit(FundHolding holding) {
        Optional<OfficialNavContext> officialNav = officialNavContext(holding.getFundCode());
        if (officialNav.isPresent() && officialNavCountsAsToday(officialNav.get())) {
            OfficialNavContext context = officialNav.get();
            BigDecimal previousNav = context.previousNav();
            if (previousNav != null && previousNav.compareTo(BigDecimal.ZERO) > 0) {
                return scale(valueOrZero(holding.getHoldingShare()).multiply(context.todayNav().subtract(previousNav)));
            }
            if (context.dailyGrowthRate() != null) {
                return dailyProfitByRate(holding.getHoldingAmount(),
                        valueOrZero(holding.getHoldingShare()),
                        null,
                        context.dailyGrowthRate());
            }
        }
        if (!intradayEstimateAllowed(holding)) {
            return ZERO;
        }
        FundValuationResult valuation = valuation(holding);
        if (valuation.themeRate() != null) {
            return dailyProfitByRate(holding, valuation.themeRate());
        }
        if (holding.getCurrentEstimateNav() == null || holding.getLatestOfficialNav() == null) {
            return ZERO;
        }
        return scale(holding.getCurrentEstimateNav()
                .subtract(holding.getLatestOfficialNav())
                .multiply(valueOrZero(holding.getHoldingShare())));
    }

    private void applyOfficialNav(FundHolding holding, OfficialNavContext context) {
        BigDecimal previousStoredNav = holding.getLatestOfficialNav();
        holding.setLatestOfficialNav(context.todayNav());
        holding.setCurrentEstimateNav(context.todayNav());
        BigDecimal share = valueOrZero(holding.getHoldingShare());
        if (share.compareTo(BigDecimal.ZERO) <= 0
                && valueOrZero(holding.getHoldingAmount()).compareTo(BigDecimal.ZERO) > 0
                && context.todayNav().compareTo(BigDecimal.ZERO) > 0) {
            share = valueOrZero(holding.getHoldingAmount()).divide(context.todayNav(), 4, RoundingMode.HALF_UP);
            holding.setHoldingShare(share);
        }
        BigDecimal amount = scale(share.multiply(context.todayNav()));
        holding.setHoldingAmount(amount);
        BigDecimal profit = amount.subtract(valueOrZero(holding.getHoldingCost()));
        holding.setHoldingProfit(scale(profit));
        holding.setHoldingProfitRate(rate(profit, holding.getHoldingCost()));
        if (context.previousNav() != null && context.previousNav().compareTo(BigDecimal.ZERO) > 0) {
            holding.setDailyProfit(scale(share.multiply(context.todayNav().subtract(context.previousNav()))));
        } else if (context.dailyGrowthRate() != null) {
            BigDecimal baseNav = previousStoredNav != null && previousStoredNav.compareTo(BigDecimal.ZERO) > 0
                    && previousStoredNav.compareTo(context.todayNav()) != 0 ? previousStoredNav : null;
            holding.setDailyProfit(dailyProfitByRate(amount, share, baseNav, context.dailyGrowthRate()));
        } else {
            holding.setDailyProfit(ZERO);
        }
    }

    private BigDecimal dailyProfitByRate(FundHolding holding, BigDecimal rate) {
        return dailyProfitByRate(valueOrZero(holding.getHoldingAmount()),
                valueOrZero(holding.getHoldingShare()),
                holding.getLatestOfficialNav(),
                rate);
    }

    private BigDecimal dailyProfitByRate(BigDecimal currentAmount, BigDecimal share, BigDecimal baseNav, BigDecimal rate) {
        if (rate == null) {
            return ZERO;
        }
        BigDecimal ratio = rate.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
        if (baseNav != null && baseNav.compareTo(BigDecimal.ZERO) > 0 && share.compareTo(BigDecimal.ZERO) > 0) {
            return scale(share.multiply(baseNav).multiply(ratio));
        }
        BigDecimal factor = BigDecimal.ONE.add(ratio);
        if (factor.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal originalAmount = valueOrZero(currentAmount).divide(factor, 4, RoundingMode.HALF_UP);
            return scale(originalAmount.multiply(ratio));
        }
        return ZERO;
    }

    private BigDecimal yesterdayProfit(FundHolding holding) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        HoldingSnapshot snapshot = holdingSnapshotMapper.selectOne(new LambdaQueryWrapper<HoldingSnapshot>()
                .eq(HoldingSnapshot::getHoldingId, holding.getId())
                .eq(HoldingSnapshot::getSnapshotDate, yesterday)
                .orderByDesc(HoldingSnapshot::getUpdateTime)
                .last("LIMIT 1"));
        if (snapshot != null) {
            return valueOrZero(snapshot.getDailyProfit());
        }
        if (holding.getUpdateTime() != null && holding.getUpdateTime().toLocalDate().equals(yesterday)) {
            return valueOrZero(holding.getDailyProfit());
        }
        return ZERO;
    }

    private int displayHoldingDays(FundHolding holding) {
        int storedDays = holding.getHoldingDays() == null ? 0 : Math.max(holding.getHoldingDays(), 0);
        if (storedDays > 0 && holding.getUpdateTime() != null) {
            long elapsed = ChronoUnit.DAYS.between(holding.getUpdateTime().toLocalDate(), LocalDate.now());
            storedDays += (int) Math.max(elapsed, 0);
        }
        if (holding.getCreateTime() == null) {
            return storedDays;
        }
        long daysFromCreate = ChronoUnit.DAYS.between(holding.getCreateTime().toLocalDate(), LocalDate.now()) + 1;
        return Math.max(storedDays, (int) Math.max(daysFromCreate, 0));
    }

    private BigDecimal currentEstimateGrowthRate(FundHolding holding) {
        Optional<OfficialNavContext> officialNav = officialNavContext(holding.getFundCode());
        if (officialNav.isPresent() && officialNavCountsAsToday(officialNav.get()) && officialNav.get().dailyGrowthRate() != null) {
            return scale(officialNav.get().dailyGrowthRate());
        }
        if (!intradayEstimateAllowed(holding)) {
            return ZERO;
        }
        if (holding.getCurrentEstimateNav() == null || holding.getLatestOfficialNav() == null
                || holding.getLatestOfficialNav().compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return rate(holding.getCurrentEstimateNav().subtract(holding.getLatestOfficialNav()), holding.getLatestOfficialNav());
    }

    private BigDecimal accountTotal(Long accountId) {
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getAccountId, accountId))
                .stream()
                .map(FundHolding::getHoldingAmount)
                .map(this::valueOrZero)
                .reduce(ZERO, BigDecimal::add);
    }

    private void ensureAccountOwned(Long userId, Long accountId) {
        PortfolioAccount account = portfolioAccountMapper.selectOne(new LambdaQueryWrapper<PortfolioAccount>()
                .eq(PortfolioAccount::getId, accountId)
                .eq(PortfolioAccount::getUserId, userId)
                .last("LIMIT 1"));
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "portfolio account not found");
        }
    }

    private FundHolding loadOwnedHolding(Long userId, Long holdingId) {
        FundHolding holding = fundHoldingMapper.selectOne(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getId, holdingId)
                .eq(FundHolding::getUserId, userId)
                .last("LIMIT 1"));
        if (holding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "fund holding not found");
        }
        return holding;
    }

    private FundHoldingVO toVO(FundHolding holding) {
        return toVO(holding, accountTotal(holding.getAccountId()));
    }

    private FundHoldingVO toVO(FundHolding holding, BigDecimal accountTotal) {
        BigDecimal estimateRate = currentEstimateGrowthRate(holding);
        OfficialNavContext officialNav = officialNavContext(holding.getFundCode()).orElse(null);
        boolean officialUpdated = officialNav != null && officialNavCountsAsToday(officialNav);
        boolean intradayAllowed = intradayEstimateAllowed(holding);
        BigDecimal dailyProfit = officialUpdated || intradayAllowed ? valueOrZero(holding.getDailyProfit()) : ZERO;
        BigDecimal displayEstimateRate = officialUpdated || intradayAllowed ? estimateRate : ZERO;
        FundValuationResult valuation = fundValuationService.estimate(
                holding.getFundCode(),
                holding.getFundName(),
                holding.getFundType(),
                displayEstimateRate
        );
        if (!officialUpdated && !intradayAllowed) {
            valuation = new FundValuationResult(
                    valuation.themeName(),
                    ZERO,
                    valuation.sourceName(),
                    valuation.basis(),
                    valuation.marketStatus()
            );
        }
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
                yesterdayProfit(holding),
                rate(valueOrZero(holding.getHoldingAmount()), accountTotal),
                displayEstimateRate,
                officialUpdated,
                officialUpdated ? officialNav.navDate() : null,
                valuation.themeName(),
                valuation.themeRate(),
                valuation.sourceName(),
                valuation.basis(),
                valuation.marketStatus(),
                displayHoldingDays(holding),
                holding.getSourcePlatform(),
                toBool(holding.getRegularInvestment()),
                toBool(holding.getCoreHolding()),
                toBool(holding.getWatchFocus()),
                holding.getUpdateTime(),
                SystemConstants.DISCLAIMER
        );
    }

    private FundValuationResult valuation(FundHolding holding) {
        return fundValuationService.estimate(
                holding.getFundCode(),
                holding.getFundName(),
                holding.getFundType(),
                currentEstimateGrowthRate(holding)
        );
    }

    private boolean officialNavPublishedFor(FundHolding holding, OfficialNavContext context) {
        LocalDate earliestEffectiveDate = delayedOfficialNavFund(holding) ? LocalDate.now().minusDays(3) : LocalDate.now();
        return !context.navDate().isBefore(earliestEffectiveDate);
    }

    private boolean officialNavCountsAsToday(OfficialNavContext context) {
        return context.navDate() != null && context.navDate().equals(LocalDate.now());
    }

    private boolean intradayEstimateAllowed(FundHolding holding) {
        return tradingCalendarService.isIntradayEstimateWindow(LocalDateTime.now()) && !delayedOfficialNavFund(holding);
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

    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal base = valueOrZero(denominator);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return valueOrZero(numerator).multiply(ONE_HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : scale(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? ZERO : scale(value);
    }

    private Integer toInt(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private Boolean toBool(Integer value) {
        return value != null && value == 1;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record OfficialNavContext(LocalDate navDate, BigDecimal todayNav, BigDecimal previousNav,
                                      BigDecimal dailyGrowthRate) {
    }
}
