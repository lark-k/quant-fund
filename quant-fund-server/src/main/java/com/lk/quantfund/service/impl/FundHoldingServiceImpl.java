package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.dto.holding.ClearHoldingRequest;
import com.lk.quantfund.dto.holding.CreateHoldingRequest;
import com.lk.quantfund.dto.holding.UpdateHoldingRequest;
import com.lk.quantfund.entity.AiAnalysisReport;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.TradeStatus;
import com.lk.quantfund.enums.TradeType;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.AiAnalysisReportMapper;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.scheduler.HoldingSnapshotBackfillService;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundHoldingService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.StrategyService;
import com.lk.quantfund.service.analytics.OfficialNavTiming;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FundHoldingServiceImpl implements FundHoldingService {

    private static final Logger log = LoggerFactory.getLogger(FundHoldingServiceImpl.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final AiAnalysisReportMapper aiAnalysisReportMapper;
    private final PortfolioAccountService portfolioAccountService;
    private final StrategyService strategyService;
    private final FundQueryService fundQueryService;
    private final FundValuationService fundValuationService;
    private final TradingCalendarService tradingCalendarService;
    private final HoldingSnapshotMapper holdingSnapshotMapper;
    private final HoldingSnapshotBackfillService holdingSnapshotBackfillService;
    private final TradeRecordMapper tradeRecordMapper;

    public FundHoldingServiceImpl(FundHoldingMapper fundHoldingMapper,
                                  PortfolioAccountMapper portfolioAccountMapper,
                                  AiAnalysisReportMapper aiAnalysisReportMapper,
                                  PortfolioAccountService portfolioAccountService,
                                  StrategyService strategyService,
                                  FundQueryService fundQueryService,
                                  FundValuationService fundValuationService,
                                  TradingCalendarService tradingCalendarService,
                                  HoldingSnapshotMapper holdingSnapshotMapper,
                                  HoldingSnapshotBackfillService holdingSnapshotBackfillService,
                                  TradeRecordMapper tradeRecordMapper) {
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.aiAnalysisReportMapper = aiAnalysisReportMapper;
        this.portfolioAccountService = portfolioAccountService;
        this.strategyService = strategyService;
        this.fundQueryService = fundQueryService;
        this.fundValuationService = fundValuationService;
        this.tradingCalendarService = tradingCalendarService;
        this.holdingSnapshotMapper = holdingSnapshotMapper;
        this.holdingSnapshotBackfillService = holdingSnapshotBackfillService;
        this.tradeRecordMapper = tradeRecordMapper;
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
        if (request.holdingProfit() != null) {
            deriveShareFromAmountUsingLatestNav(holding);
        }
        recalculateEntity(holding);
        holding.setCreateTime(now);
        holding.setUpdateTime(now);
        holding.setDeleted(0);
        fundHoldingMapper.insert(holding);
        portfolioAccountService.recalculateOwnedAccount(userId, request.accountId());
        refreshStrategySignals(userId, holding.getId());
        return toVO(holding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundHoldingVO update(Long holdingId, UpdateHoldingRequest request) {
        Long userId = UserContext.getUserId();
        FundHolding holding = loadOwnedHolding(userId, holdingId);
        Long oldAccountId = holding.getAccountId();
        String oldFundCode = holding.getFundCode();
        BigDecimal previousCurrentEstimateNav = holding.getCurrentEstimateNav();
        ensureAccountOwned(userId, request.accountId());
        applyUpdateFields(holding, request);
        refreshMarketData(holding, oldFundCode, previousCurrentEstimateNav);
        if (request.holdingProfit() != null) {
            deriveShareFromAmountUsingLatestNav(holding);
        }
        recalculateEntity(holding);
        holding.setUpdateTime(LocalDateTime.now());
        fundHoldingMapper.updateById(holding);
        portfolioAccountService.recalculateOwnedAccount(userId, holding.getAccountId());
        if (!oldAccountId.equals(holding.getAccountId())) {
            portfolioAccountService.recalculateOwnedAccount(userId, oldAccountId);
        }
        refreshStrategySignals(userId, holding.getId());
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
    @Transactional(rollbackFor = Exception.class)
    public FundHoldingVO clear(Long holdingId, ClearHoldingRequest request) {
        Long userId = UserContext.getUserId();
        FundHolding holding = loadOwnedHolding(userId, holdingId);
        BigDecimal clearedAmount = valueOrZero(holding.getHoldingAmount());
        BigDecimal clearedShare = valueOrZero(holding.getHoldingShare());
        BigDecimal clearedNav = clearNav(holding, clearedAmount, clearedShare);
        BigDecimal tradeAmount = clearTradeAmount(request, clearedAmount);
        BigDecimal tradeFee = request == null ? ZERO : valueOrZero(request.tradeFee());
        String remark = clearRemark(request);
        LocalDateTime now = LocalDateTime.now();
        if (clearedAmount.compareTo(BigDecimal.ZERO) > 0 || clearedShare.compareTo(BigDecimal.ZERO) > 0) {
            insertClearTrade(userId, holding, tradeAmount, clearedShare, clearedNav, tradeFee, remark, now);
        }
        holding.setActiveFund(0);
        holding.setHoldingAmount(ZERO);
        holding.setHoldingShare(ZERO);
        holding.setHoldingCost(ZERO);
        holding.setHoldingProfit(ZERO);
        holding.setHoldingProfitRate(ZERO);
        holding.setDailyProfit(ZERO);
        holding.setUpdateTime(now);
        fundHoldingMapper.updateById(holding);
        portfolioAccountService.recalculateOwnedAccount(userId, holding.getAccountId());
        return toVO(holding);
    }

    private void insertClearTrade(Long userId,
                                  FundHolding holding,
                                  BigDecimal tradeAmount,
                                  BigDecimal clearedShare,
                                  BigDecimal clearedNav,
                                  BigDecimal tradeFee,
                                  String remark,
                                  LocalDateTime now) {
        TradeRecord record = new TradeRecord();
        record.setUserId(userId);
        record.setAccountId(holding.getAccountId());
        record.setHoldingId(holding.getId());
        record.setFundCode(holding.getFundCode());
        record.setFundName(holding.getFundName());
        record.setTradeType(TradeType.SELL.name());
        record.setTradeStatus(TradeStatus.COMPLETED.name());
        record.setTradeAmount(tradeAmount);
        record.setTradeShare(clearedShare);
        record.setTradeNav(clearedNav);
        record.setTradeFee(tradeFee);
        record.setTradeTime(now);
        record.setRemark(remark);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(0);
        tradeRecordMapper.insert(record);
    }

    private BigDecimal clearNav(FundHolding holding, BigDecimal amount, BigDecimal share) {
        BigDecimal nav = holding.getCurrentEstimateNav() != null ? holding.getCurrentEstimateNav() : holding.getLatestOfficialNav();
        if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0) {
            return scale(nav);
        }
        if (amount.compareTo(BigDecimal.ZERO) > 0 && share.compareTo(BigDecimal.ZERO) > 0) {
            return amount.divide(share, 4, RoundingMode.HALF_UP);
        }
        return ZERO;
    }

    private BigDecimal clearTradeAmount(ClearHoldingRequest request, BigDecimal fallbackAmount) {
        if (request != null && request.tradeAmount() != null) {
            return valueOrZero(request.tradeAmount());
        }
        return fallbackAmount;
    }

    private String clearRemark(ClearHoldingRequest request) {
        String remark;
        if (request != null && StringUtils.hasText(request.remark())) {
            remark = request.remark().trim();
        } else {
            remark = "清仓自动生成的模拟卖出流水";
        }
        if (remark.contains(SystemConstants.SIMULATED_TRADE_NOTICE)) {
            return remark;
        }
        return remark + "，" + SystemConstants.SIMULATED_TRADE_NOTICE;
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
        refreshMarketDataForEstimateRefresh(holding);
        recalculateEntity(holding);
        holding.setUpdateTime(LocalDateTime.now());
        fundHoldingMapper.updateById(holding);
        portfolioAccountService.recalculateOwnedAccount(userId, holding.getAccountId());
        refreshStrategySignals(userId, holding.getId());
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
            upsertSnapshot(holding, officialNav.get());
            if (!touchedAccountIds.contains(holding.getAccountId())) {
                touchedAccountIds.add(holding.getAccountId());
            }
        }
        touchedAccountIds.forEach(accountId -> portfolioAccountService.recalculateOwnedAccount(userId, accountId));
        holdings.stream()
                .filter(holding -> touchedAccountIds.contains(holding.getAccountId()))
                .forEach(holding -> refreshStrategySignals(userId, holding.getId()));
        return list(null, null);
    }

    private void refreshStrategySignals(Long userId, Long holdingId) {
        try {
            strategyService.analyzeHoldingForUser(userId, holdingId);
        } catch (RuntimeException exception) {
            log.warn("Strategy analysis skipped for holding {}: {}", holdingId, exception.getMessage());
        }
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
        holding.setCurrentEstimateNav(request.currentEstimateNav() == null ? null : valueOrZero(request.currentEstimateNav()));
        holding.setLatestOfficialNav(request.latestOfficialNav() == null ? null : valueOrZero(request.latestOfficialNav()));
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
        if (request.holdingProfit() == null) {
            deriveShareFromAmountIfNeeded(holding, previousCurrentEstimateNav, previousLatestOfficialNav);
        }
        holding.setCurrentEstimateNav(request.currentEstimateNav() == null
                ? previousCurrentEstimateNav
                : valueOrZero(request.currentEstimateNav()));
        holding.setLatestOfficialNav(request.latestOfficialNav() == null
                ? previousLatestOfficialNav
                : valueOrZero(request.latestOfficialNav()));
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
        BigDecimal dailyProfit = calculateDailyProfit(holding);
        BigDecimal profitBaseAmount = shouldApplyIntradayProfitToHoldingProfit(holding) ? amount.add(dailyProfit) : amount;
        BigDecimal profit = profitBaseAmount.subtract(valueOrZero(holding.getHoldingCost()));
        holding.setHoldingAmount(amount);
        holding.setHoldingProfit(scale(profit));
        holding.setHoldingProfitRate(rate(profit, holding.getHoldingCost()));
        holding.setDailyProfit(dailyProfit);
    }

    private boolean shouldApplyIntradayProfitToHoldingProfit(FundHolding holding) {
        Optional<OfficialNavContext> officialNav = officialNavContext(holding.getFundCode());
        return (officialNav.isEmpty() || !officialNavCountsAsToday(holding, officialNav.get()))
                && intradayEstimateAllowed(holding);
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

    private void deriveShareFromAmountUsingLatestNav(FundHolding holding) {
        BigDecimal amount = valueOrZero(holding.getHoldingAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal nav = holding.getLatestOfficialNav();
        BigDecimal cost = valueOrZero(holding.getHoldingCost());
        if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0 && cost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitRate = rate(amount.subtract(cost), cost).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
            BigDecimal factor = BigDecimal.ONE.add(profitRate);
            if (factor.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal costNav = nav.divide(factor, 4, RoundingMode.HALF_UP);
                if (costNav.compareTo(BigDecimal.ZERO) > 0) {
                    holding.setHoldingShare(cost.divide(costNav, 4, RoundingMode.HALF_UP));
                }
            }
        } else if (cost.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少最新正式净值，不能使用盘中估值反推持有份额");
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "持有金额或持有份额不能为空");
        }
        if (cost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "持有成本或持有收益不能为空");
        }
    }

    private BigDecimal estimateAmount(FundHolding holding) {
        if (valueOrZero(holding.getHoldingAmount()).compareTo(BigDecimal.ZERO) > 0) {
            return scale(holding.getHoldingAmount());
        }
        BigDecimal nav = holding.getCurrentEstimateNav() != null ? holding.getCurrentEstimateNav() : holding.getLatestOfficialNav();
        if (nav != null && valueOrZero(holding.getHoldingShare()).compareTo(BigDecimal.ZERO) > 0) {
            return scale(valueOrZero(holding.getHoldingShare()).multiply(nav));
        }
        return valueOrZero(holding.getHoldingAmount());
    }

    private void refreshMarketData(FundHolding holding) {
        refreshMarketData(holding, null, null, false);
    }

    private void refreshMarketData(FundHolding holding, String previousFundCode, BigDecimal previousCurrentEstimateNav) {
        refreshMarketData(holding, previousFundCode, previousCurrentEstimateNav, false);
    }

    private void refreshMarketDataForEstimateRefresh(FundHolding holding) {
        refreshMarketData(holding, null, null, true);
    }

    private void refreshMarketData(FundHolding holding, String previousFundCode, BigDecimal previousCurrentEstimateNav,
                                   boolean allowDisplayWindowFetch) {
        Optional<OfficialNavContext> officialNav = officialNavContext(holding.getFundCode());
        if (officialNav.isPresent() && officialNavPublishedFor(holding, officialNav.get())) {
            applyOfficialNav(holding, officialNav.get());
            return;
        }
        if (intradayEstimateFetchAllowed(holding, allowDisplayWindowFetch)) {
            try {
                FundEstimateDTO estimate = fundQueryService.getIntradayEstimate(holding.getFundCode(), false);
                if (StringUtils.hasText(estimate.fundName())) {
                    holding.setFundName(estimate.fundName());
                }
                holding.setCurrentEstimateNav(estimate.estimateNav());
                return;
            } catch (RuntimeException ignored) {
                // Estimates can be unavailable before data opens; official NAV is still useful.
            }
        }
        latestOfficialNav(holding.getFundCode()).ifPresent(holding::setLatestOfficialNav);
        if (previousFundCode != null
                && previousFundCode.equals(holding.getFundCode())
                && previousCurrentEstimateNav != null
                && holding.getCurrentEstimateNav() == null) {
            holding.setCurrentEstimateNav(previousCurrentEstimateNav);
        }
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
        if (officialNav.isPresent() && officialNavCountsAsToday(holding, officialNav.get())) {
            OfficialNavContext context = officialNav.get();
            BigDecimal previousNav = context.previousNav();
            if (previousNav != null && previousNav.compareTo(BigDecimal.ZERO) > 0) {
                return scale(valueOrZero(holding.getHoldingShare()).multiply(context.todayNav().subtract(previousNav)));
            }
            if (context.dailyGrowthRate() != null) {
                return dailyProfitByRate(displayHoldingAmount(holding),
                        valueOrZero(holding.getHoldingShare()),
                        null,
                        context.dailyGrowthRate());
            }
        }
        if (!intradayEstimateAllowed(holding)) {
            return ZERO;
        }
        if (holding.getCurrentEstimateNav() != null
                && holding.getLatestOfficialNav() != null
                && holding.getLatestOfficialNav().compareTo(BigDecimal.ZERO) > 0
                && holding.getCurrentEstimateNav().compareTo(holding.getLatestOfficialNav()) != 0) {
            return scale(holding.getCurrentEstimateNav()
                    .subtract(holding.getLatestOfficialNav())
                    .multiply(valueOrZero(holding.getHoldingShare())));
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
        BigDecimal share = valueOrZero(holding.getHoldingShare());
        if (share.compareTo(BigDecimal.ZERO) <= 0
                && valueOrZero(holding.getHoldingAmount()).compareTo(BigDecimal.ZERO) > 0
                && context.todayNav().compareTo(BigDecimal.ZERO) > 0) {
            share = valueOrZero(holding.getHoldingAmount()).divide(context.todayNav(), 4, RoundingMode.HALF_UP);
            holding.setHoldingShare(share);
        }
        boolean sameOfficialNavAlreadyApplied = previousStoredNav != null
                && previousStoredNav.compareTo(context.todayNav()) == 0
                && holding.getCurrentEstimateNav() != null
                && holding.getCurrentEstimateNav().compareTo(context.todayNav()) == 0;
        BigDecimal baseAmount = sameOfficialNavAlreadyApplied
                ? officialBaseAmount(holding, share, context.todayNav(), previousStoredNav)
                : officialBaseAmount(holding, share, context.previousNav(), previousStoredNav);
        BigDecimal dailyProfit = ZERO;
        if (!sameOfficialNavAlreadyApplied) {
            if (context.previousNav() != null && context.previousNav().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyRate = rate(context.todayNav().subtract(context.previousNav()), context.previousNav());
                dailyProfit = amountChangeByRate(baseAmount, dailyRate);
            } else if (context.dailyGrowthRate() != null) {
                dailyProfit = amountChangeByRate(baseAmount, context.dailyGrowthRate());
            }
        } else {
            dailyProfit = valueOrZero(holding.getDailyProfit());
        }
        BigDecimal amount = sameOfficialNavAlreadyApplied ? baseAmount : scale(baseAmount.add(dailyProfit));
        holding.setLatestOfficialNav(context.todayNav());
        holding.setCurrentEstimateNav(context.todayNav());
        holding.setHoldingAmount(amount);
        BigDecimal profit = amount.subtract(valueOrZero(holding.getHoldingCost()));
        holding.setHoldingProfit(scale(profit));
        holding.setHoldingProfitRate(rate(profit, holding.getHoldingCost()));
        holding.setDailyProfit(dailyProfit);
    }

    private BigDecimal officialBaseAmount(FundHolding holding, BigDecimal share, BigDecimal previousNav, BigDecimal previousStoredNav) {
        BigDecimal fallbackNav = previousNav != null && previousNav.compareTo(BigDecimal.ZERO) > 0
                ? previousNav
                : previousStoredNav;
        if (fallbackNav != null && fallbackNav.compareTo(BigDecimal.ZERO) > 0 && share.compareTo(BigDecimal.ZERO) > 0) {
            return scale(share.multiply(fallbackNav));
        }
        BigDecimal amount = valueOrZero(holding.getHoldingAmount());
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return scale(amount);
        }
        return ZERO;
    }

    private BigDecimal dailyProfitByRate(FundHolding holding, BigDecimal rate) {
        return dailyProfitByRate(displayHoldingAmount(holding),
                valueOrZero(holding.getHoldingShare()),
                holding.getLatestOfficialNav(),
                rate);
    }

    private BigDecimal dailyProfitByRate(BigDecimal currentAmount, BigDecimal share, BigDecimal baseNav, BigDecimal rate) {
        if (rate == null) {
            return ZERO;
        }
        return amountChangeByRate(currentAmount, rate);
    }

    private BigDecimal amountChangeByRate(BigDecimal baseAmount, BigDecimal changeRate) {
        if (changeRate == null) {
            return ZERO;
        }
        BigDecimal ratio = changeRate.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
        return scale(valueOrZero(baseAmount).multiply(ratio));
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
        if (officialNav.isPresent() && officialNavCountsAsToday(holding, officialNav.get()) && officialNav.get().dailyGrowthRate() != null) {
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
                .map(this::displayHoldingAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    private void ensureAccountOwned(Long userId, Long accountId) {
        PortfolioAccount account = portfolioAccountMapper.selectOne(new LambdaQueryWrapper<PortfolioAccount>()
                .eq(PortfolioAccount::getId, accountId)
                .eq(PortfolioAccount::getUserId, userId)
                .last("LIMIT 1"));
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "组合账户不存在");
        }
    }

    private FundHolding loadOwnedHolding(Long userId, Long holdingId) {
        FundHolding holding = fundHoldingMapper.selectOne(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getId, holdingId)
                .eq(FundHolding::getUserId, userId)
                .last("LIMIT 1"));
        if (holding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "基金持仓不存在");
        }
        return holding;
    }

    private FundHoldingVO toVO(FundHolding holding) {
        return toVO(holding, accountTotal(holding.getAccountId()));
    }

    private FundHoldingVO toVO(FundHolding holding, BigDecimal accountTotal) {
        BigDecimal estimateRate = currentEstimateGrowthRate(holding);
        OfficialNavContext officialNav = officialNavContext(holding.getFundCode()).orElse(null);
        boolean officialUpdated = officialNav != null && officialNavCountsAsToday(holding, officialNav);
        boolean intradayAllowed = intradayEstimateAllowed(holding);
        BigDecimal dailyProfit = officialUpdated || intradayAllowed ? valueOrZero(holding.getDailyProfit()) : ZERO;
        BigDecimal displayEstimateRate = officialUpdated || intradayAllowed ? estimateRate : ZERO;
        FundValuationResult valuation = fundValuationService.estimate(
                holding.getFundCode(),
                holding.getFundName(),
                holding.getFundType(),
                displayEstimateRate
        );
        if (officialUpdated) {
            valuation = officialNavValuation(valuation, displayEstimateRate);
        }
        if (!officialUpdated && !intradayAllowed) {
            valuation = new FundValuationResult(
                    valuation.themeName(),
                    ZERO,
                    valuation.sourceName(),
                    valuation.basis(),
                    valuation.marketStatus()
            );
        }
        BigDecimal holdingAmount = displayHoldingAmount(holding);
        BigDecimal profitBaseAmount = !officialUpdated && intradayAllowed ? holdingAmount.add(dailyProfit) : holdingAmount;
        BigDecimal holdingProfit = profitBaseAmount.subtract(valueOrZero(holding.getHoldingCost()));
        return new FundHoldingVO(
                holding.getId(),
                holding.getAccountId(),
                holding.getFundCode(),
                holding.getFundName(),
                holding.getFundType(),
                toBool(holding.getActiveFund()),
                holdingAmount,
                valueOrZero(holding.getHoldingShare()),
                valueOrZero(holding.getHoldingCost()),
                holding.getCurrentEstimateNav(),
                holding.getLatestOfficialNav(),
                scale(holdingProfit),
                rate(holdingProfit, holding.getHoldingCost()),
                dailyProfit,
                yesterdayProfit(holding),
                rate(holdingAmount, accountTotal),
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

    private BigDecimal displayHoldingAmount(FundHolding holding) {
        return valueOrZero(holding.getHoldingAmount());
    }

    private FundValuationResult officialNavValuation(FundValuationResult valuation, BigDecimal officialRate) {
        return new FundValuationResult(
                valuation.themeName(),
                scale(officialRate),
                "OFFICIAL_NAV",
                "正式净值涨跌率",
                valuation.marketStatus()
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
        LocalDate today = LocalDate.now();
        LocalDate effectiveDate = officialNavEffectiveDate(holding, context.navDate());
        if (delayedOfficialNavFund(holding)) {
            return !effectiveDate.isBefore(today.minusDays(3)) && !effectiveDate.isAfter(today);
        }
        return !context.navDate().isBefore(today);
    }

    private boolean officialNavCountsAsToday(FundHolding holding, OfficialNavContext context) {
        return context.navDate() != null && officialNavEffectiveDate(holding, context.navDate()).equals(LocalDate.now());
    }

    private void upsertSnapshot(FundHolding holding, OfficialNavContext officialNav) {
        PortfolioAccount account = portfolioAccountMapper.selectById(holding.getAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "组合账户不存在");
        }
        LocalDate snapshotDate = officialNavEffectiveDate(holding, officialNav.navDate());
        LocalDateTime now = LocalDateTime.now();
        HoldingSnapshot snapshot = findSnapshot(holding.getId(), snapshotDate);
        if (snapshot == null) {
            snapshot = holdingSnapshotMapper.selectByHoldingAndDateIncludingDeleted(holding.getId(), snapshotDate);
            restoreSnapshotIfDeleted(snapshot);
        }
        HoldingSnapshot legacyDelayedSnapshot = legacyDelayedSnapshot(holding, officialNav.navDate(), snapshotDate);
        boolean insert = snapshot == null;
        if (insert) {
            snapshot = legacyDelayedSnapshot == null ? new HoldingSnapshot() : legacyDelayedSnapshot;
            insert = legacyDelayedSnapshot == null;
            if (insert) {
                snapshot.setCreateTime(now);
                snapshot.setDeleted(0);
            }
        } else if (legacyDelayedSnapshot != null && legacyDelayedSnapshot.getId() != null) {
            holdingSnapshotMapper.deleteById(legacyDelayedSnapshot.getId());
        }
        if (!insert && historicalSnapshotDate(snapshotDate)) {
            return;
        }
        snapshot.setUserId(holding.getUserId());
        snapshot.setAccountId(holding.getAccountId());
        snapshot.setHoldingId(holding.getId());
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setTotalAsset(valueOrZero(account.getTotalAsset()));
        snapshot.setHoldingAmount(valueOrZero(holding.getHoldingAmount()));
        snapshot.setHoldingProfit(valueOrZero(holding.getHoldingProfit()));
        snapshot.setDailyProfit(valueOrZero(holding.getDailyProfit()));
        snapshot.setPositionRate(rate(holding.getHoldingAmount(), account.getTotalAsset()));
        snapshot.setUpdateTime(now);
        if (insert) {
            try {
                holdingSnapshotMapper.insert(snapshot);
            } catch (DuplicateKeyException exception) {
                restoreSnapshotIfDeleted(holdingSnapshotMapper.selectByHoldingAndDateIncludingDeleted(
                        holding.getId(), snapshotDate));
            }
        } else {
            holdingSnapshotMapper.updateById(snapshot);
        }
    }

    private void restoreSnapshotIfDeleted(HoldingSnapshot snapshot) {
        if (snapshot != null && Integer.valueOf(1).equals(snapshot.getDeleted())) {
            holdingSnapshotMapper.restoreById(snapshot.getId());
            snapshot.setDeleted(0);
        }
    }

    private boolean historicalSnapshotDate(LocalDate snapshotDate) {
        return snapshotDate != null && snapshotDate.isBefore(now().toLocalDate());
    }

    private HoldingSnapshot findSnapshot(Long holdingId, LocalDate snapshotDate) {
        return holdingSnapshotMapper.selectOne(new LambdaQueryWrapper<HoldingSnapshot>()
                .eq(HoldingSnapshot::getHoldingId, holdingId)
                .eq(HoldingSnapshot::getSnapshotDate, snapshotDate)
                .last("LIMIT 1"));
    }

    private HoldingSnapshot legacyDelayedSnapshot(FundHolding holding, LocalDate navDate, LocalDate snapshotDate) {
        if (!delayedOfficialNavFund(holding) || navDate == null || navDate.equals(snapshotDate)) {
            return null;
        }
        return findSnapshot(holding.getId(), navDate);
    }

    private LocalDate officialNavEffectiveDate(FundHolding holding, LocalDate navDate) {
        return OfficialNavTiming.effectiveDate(holding, navDate, tradingCalendarService);
    }

    private boolean intradayEstimateAllowed(FundHolding holding) {
        return tradingCalendarService.isIntradayEstimateDisplayWindow(now());
    }

    private boolean intradayEstimateFetchAllowed(FundHolding holding) {
        return intradayEstimateFetchAllowed(holding, false);
    }

    private boolean intradayEstimateFetchAllowed(FundHolding holding, boolean allowDisplayWindowFetch) {
        LocalDateTime current = now();
        boolean estimateWindow = allowDisplayWindowFetch
                ? tradingCalendarService.isIntradayEstimateDisplayWindow(current)
                : tradingCalendarService.isIntradayEstimateWindow(current);
        return estimateWindow;
    }

    protected LocalDateTime now() {
        return LocalDateTime.now();
    }

    private boolean delayedOfficialNavFund(FundHolding holding) {
        return OfficialNavTiming.isDelayedOfficialNavFund(holding);
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
