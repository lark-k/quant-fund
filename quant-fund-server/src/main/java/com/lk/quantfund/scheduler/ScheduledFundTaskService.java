package com.lk.quantfund.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.service.AiAnalysisService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.StrategyService;
import com.lk.quantfund.service.analytics.OfficialNavTiming;
import com.lk.quantfund.service.valuation.FundValuationResult;
import com.lk.quantfund.service.valuation.FundValuationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ScheduledFundTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledFundTaskService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private final QuantFundProperties properties;
    private final FundQueryService fundQueryService;
    private final AiAnalysisService aiAnalysisService;
    private final StrategyService strategyService;
    private final PortfolioAccountService portfolioAccountService;
    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final HoldingSnapshotMapper holdingSnapshotMapper;
    private final FundValuationService fundValuationService;
    private final TradingCalendarService tradingCalendarService;

    public ScheduledFundTaskService(QuantFundProperties properties,
                                    FundQueryService fundQueryService,
                                    AiAnalysisService aiAnalysisService,
                                    StrategyService strategyService,
                                    PortfolioAccountService portfolioAccountService,
                                    FundHoldingMapper fundHoldingMapper,
                                    PortfolioAccountMapper portfolioAccountMapper,
                                    HoldingSnapshotMapper holdingSnapshotMapper,
                                    FundValuationService fundValuationService,
                                    TradingCalendarService tradingCalendarService) {
        this.properties = properties;
        this.fundQueryService = fundQueryService;
        this.aiAnalysisService = aiAnalysisService;
        this.strategyService = strategyService;
        this.portfolioAccountService = portfolioAccountService;
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.holdingSnapshotMapper = holdingSnapshotMapper;
        this.fundValuationService = fundValuationService;
        this.tradingCalendarService = tradingCalendarService;
    }

    @Transactional(rollbackFor = Exception.class)
    public SchedulerTaskResult refreshIntradayEstimates() {
        SchedulerTaskResult result = new SchedulerTaskResult();
        if (!tradingCalendarService.isIntradayEstimateWindow(LocalDateTime.now())) {
            log.info("Scheduled estimate refresh skipped because current time is outside A-share intraday window");
            return result;
        }
        Map<String, List<FundHolding>> holdingsByFundCode = holdingsByFundCode(loadAllHoldings());
        for (Map.Entry<String, List<FundHolding>> entry : holdingsByFundCode.entrySet()) {
            try {
                FundEstimateDTO estimate = fundQueryService.getIntradayEstimate(entry.getKey(), false);
                for (FundHolding holding : entry.getValue()) {
                    applyEstimate(holding, estimate);
                    fundHoldingMapper.updateById(holding);
                }
                recalculateAccounts(entry.getValue());
                analyzeStrategies(entry.getValue());
                result.success();
            } catch (RuntimeException exception) {
                result.failure(entry.getKey() + ": " + exception.getMessage());
                log.warn("Scheduled estimate refresh failed for {}: {}", entry.getKey(), exception.getMessage());
            } finally {
                randomDelay();
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SchedulerTaskResult syncOfficialNav() {
        SchedulerTaskResult result = new SchedulerTaskResult();
        Map<String, List<FundHolding>> holdingsByFundCode = holdingsByFundCode(loadAllHoldings());
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(10);
        for (Map.Entry<String, List<FundHolding>> entry : holdingsByFundCode.entrySet()) {
            try {
                List<FundNavPointDTO> navPoints = fundQueryService.getHistoricalNav(entry.getKey(), startDate, endDate);
                FundNavPointDTO latest = navPoints.stream()
                        .max(Comparator.comparing(FundNavPointDTO::navDate))
                        .orElseThrow(() -> new IllegalStateException("latest nav not found"));
                FundHolding sampleHolding = entry.getValue().getFirst();
                if (!officialNavPublishedFor(sampleHolding, latest.navDate(), endDate)) {
                    throw new IllegalStateException("effective official nav not published");
                }
                LocalDate snapshotDate = officialNavEffectiveDate(sampleHolding, latest.navDate());
                BigDecimal previousUnitNav = navPoints.stream()
                        .filter(point -> point.navDate().isBefore(latest.navDate()))
                        .max(Comparator.comparing(FundNavPointDTO::navDate))
                        .map(FundNavPointDTO::unitNav)
                        .orElse(null);
                LocalDateTime now = LocalDateTime.now();
                for (FundHolding holding : entry.getValue()) {
                    applyOfficialNav(holding, latest, previousUnitNav);
                    fundHoldingMapper.updateById(holding);
                    upsertSnapshot(holding, latest.navDate(), snapshotDate, now);
                }
                recalculateAccounts(entry.getValue());
                analyzeStrategies(entry.getValue());
                result.success();
            } catch (RuntimeException exception) {
                result.failure(entry.getKey() + ": " + exception.getMessage());
                log.warn("Scheduled official nav sync failed for {}: {}", entry.getKey(), exception.getMessage());
            } finally {
                randomDelay();
            }
        }
        return result;
    }

    public SchedulerTaskResult analyzeFocusHoldings() {
        SchedulerTaskResult result = new SchedulerTaskResult();
        List<FundHolding> holdings = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .and(wrapper -> wrapper.eq(FundHolding::getCoreHolding, 1).or().eq(FundHolding::getWatchFocus, 1))
                .orderByDesc(FundHolding::getHoldingAmount)
                .last("LIMIT " + properties.getScheduler().getAiFocusHoldingLimit()));
        for (FundHolding holding : holdings) {
            try {
                strategyService.analyzeHoldingForUser(holding.getUserId(), holding.getId());
                aiAnalysisService.analyzeHoldingForUser(holding.getUserId(), holding.getId());
                result.success();
            } catch (RuntimeException exception) {
                result.failure(holding.getFundCode() + ": " + exception.getMessage());
                log.warn("Scheduled AI analysis failed for holding {}: {}", holding.getId(), exception.getMessage());
            } finally {
                randomDelay();
            }
        }
        return result;
    }

    private void analyzeStrategies(List<FundHolding> holdings) {
        for (FundHolding holding : holdings) {
            try {
                strategyService.analyzeHoldingForUser(holding.getUserId(), holding.getId());
            } catch (RuntimeException exception) {
                log.warn("Scheduled strategy analysis failed for holding {}: {}", holding.getId(), exception.getMessage());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SchedulerTaskResult createHoldingSnapshots() {
        SchedulerTaskResult result = new SchedulerTaskResult();
        LocalDate snapshotDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        for (FundHolding holding : loadAllHoldings()) {
            try {
                PortfolioAccount account = portfolioAccountMapper.selectById(holding.getAccountId());
                if (account == null) {
                    throw new IllegalStateException("portfolio account not found");
                }
                HoldingSnapshot snapshot = new HoldingSnapshot();
                snapshot.setUserId(holding.getUserId());
                snapshot.setAccountId(holding.getAccountId());
                snapshot.setHoldingId(holding.getId());
                snapshot.setSnapshotDate(snapshotDate);
                snapshot.setTotalAsset(scale(account.getTotalAsset()));
                BigDecimal holdingAmount = effectiveHoldingAmount(holding);
                snapshot.setHoldingAmount(holdingAmount);
                snapshot.setHoldingProfit(effectiveHoldingProfit(holding, holdingAmount));
                snapshot.setDailyProfit(scale(holding.getDailyProfit()));
                snapshot.setPositionRate(positionRate(holdingAmount, account.getTotalAsset()));
                snapshot.setCreateTime(now);
                snapshot.setUpdateTime(now);
                snapshot.setDeleted(0);
                upsertSnapshot(snapshot);
                result.success();
            } catch (RuntimeException exception) {
                result.failure(holding.getFundCode() + ": " + exception.getMessage());
                log.warn("Scheduled snapshot failed for holding {}: {}", holding.getId(), exception.getMessage());
            }
        }
        return result;
    }

    public SchedulerTaskResult createWeeklyReviewCheckpoint() {
        SchedulerTaskResult result = new SchedulerTaskResult();
        int accountCount = portfolioAccountMapper.selectCount(new LambdaQueryWrapper<PortfolioAccount>()).intValue();
        int holdingCount = fundHoldingMapper.selectCount(new LambdaQueryWrapper<FundHolding>()).intValue();
        log.info("Weekly review checkpoint: accountCount={}, holdingCount={}", accountCount, holdingCount);
        result.success();
        return result;
    }

    private List<FundHolding> loadAllHoldings() {
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .orderByAsc(FundHolding::getFundCode));
    }

    private Map<String, List<FundHolding>> holdingsByFundCode(List<FundHolding> holdings) {
        Map<String, List<FundHolding>> groups = new LinkedHashMap<>();
        for (FundHolding holding : holdings) {
            if (!StringUtils.hasText(holding.getFundCode())) {
                continue;
            }
            groups.computeIfAbsent(holding.getFundCode(), ignored -> new java.util.ArrayList<>()).add(holding);
        }
        return groups;
    }

    private void applyEstimate(FundHolding holding, FundEstimateDTO estimate) {
        BigDecimal estimateNav = scale(estimate.estimateNav());
        BigDecimal holdingShare = scale(holding.getHoldingShare());
        BigDecimal frozenHoldingAmount = scale(holding.getHoldingAmount());
        holding.setCurrentEstimateNav(estimateNav);
        holding.setHoldingAmount(frozenHoldingAmount);
        BigDecimal dailyProfit;
        if (holding.getLatestOfficialNav() != null
                && holding.getLatestOfficialNav().compareTo(BigDecimal.ZERO) > 0
                && estimateNav.compareTo(holding.getLatestOfficialNav()) != 0) {
            BigDecimal estimateRate = rate(estimateNav.subtract(holding.getLatestOfficialNav()), holding.getLatestOfficialNav());
            dailyProfit = amountChangeByRate(frozenHoldingAmount, estimateRate);
        } else {
            BigDecimal estimateRate = estimate.estimateGrowthRate() == null ? null : estimate.estimateGrowthRate();
            FundValuationResult valuation = fundValuationService.estimate(
                    holding.getFundCode(), holding.getFundName(), holding.getFundType(), estimateRate);
            dailyProfit = dailyProfitByRate(frozenHoldingAmount, valuation.themeRate(), holdingShare, holding.getLatestOfficialNav());
        }
        holding.setDailyProfit(dailyProfit);
        BigDecimal estimatedProfit = frozenHoldingAmount.add(dailyProfit).subtract(scale(holding.getHoldingCost()));
        holding.setHoldingProfit(scale(estimatedProfit));
        holding.setHoldingProfitRate(rate(holding.getHoldingProfit(), holding.getHoldingCost()));
        holding.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(estimate.fundName())) {
            holding.setFundName(estimate.fundName());
        }
    }

    private BigDecimal frozenHoldingAmount(FundHolding holding, BigDecimal holdingShare, BigDecimal fallbackNav) {
        BigDecimal holdingAmount = scale(holding.getHoldingAmount());
        if (holdingAmount.compareTo(BigDecimal.ZERO) > 0) {
            return holdingAmount;
        }
        if (fallbackNav != null && fallbackNav.compareTo(BigDecimal.ZERO) > 0
                && holdingShare != null && holdingShare.compareTo(BigDecimal.ZERO) > 0) {
            return scale(holdingShare.multiply(fallbackNav));
        }
        return ZERO;
    }

    private BigDecimal effectiveHoldingAmount(FundHolding holding) {
        BigDecimal holdingAmount = scale(holding.getHoldingAmount());
        if (holdingAmount.compareTo(BigDecimal.ZERO) > 0) {
            return holdingAmount;
        }
        BigDecimal holdingShare = scale(holding.getHoldingShare());
        BigDecimal latestOfficialNav = holding.getLatestOfficialNav();
        if (latestOfficialNav != null && latestOfficialNav.compareTo(BigDecimal.ZERO) > 0
                && holdingShare.compareTo(BigDecimal.ZERO) > 0) {
            return scale(holdingShare.multiply(latestOfficialNav));
        }
        return ZERO;
    }

    private BigDecimal effectiveHoldingProfit(FundHolding holding, BigDecimal holdingAmount) {
        return scale(holdingAmount.subtract(scale(holding.getHoldingCost())));
    }

    private void applyOfficialNav(FundHolding holding, FundNavPointDTO navPoint, BigDecimal previousUnitNav) {
        BigDecimal unitNav = scale(navPoint.unitNav());
        BigDecimal previousNav = previousUnitNav == null ? holding.getLatestOfficialNav() : previousUnitNav;
        BigDecimal holdingShare = scale(holding.getHoldingShare());
        boolean sameOfficialNavAlreadyApplied = holding.getLatestOfficialNav() != null
                && holding.getLatestOfficialNav().compareTo(unitNav) == 0
                && holding.getCurrentEstimateNav() != null
                && holding.getCurrentEstimateNav().compareTo(unitNav) == 0;
        BigDecimal baseAmount = frozenHoldingAmount(holding, holdingShare, sameOfficialNavAlreadyApplied ? unitNav : previousNav);
        holding.setLatestOfficialNav(unitNav);
        holding.setCurrentEstimateNav(unitNav);
        if (sameOfficialNavAlreadyApplied) {
            holding.setHoldingAmount(baseAmount);
            holding.setHoldingProfit(baseAmount.subtract(scale(holding.getHoldingCost())).setScale(4, RoundingMode.HALF_UP));
            holding.setHoldingProfitRate(rate(holding.getHoldingProfit(), holding.getHoldingCost()));
        } else if (previousNav != null && previousNav.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dailyRate = rate(unitNav.subtract(previousNav), previousNav);
            BigDecimal dailyProfit = amountChangeByRate(baseAmount, dailyRate);
            BigDecimal holdingAmount = baseAmount.add(dailyProfit).setScale(4, RoundingMode.HALF_UP);
            holding.setHoldingAmount(holdingAmount);
            holding.setHoldingProfit(holdingAmount.subtract(scale(holding.getHoldingCost())).setScale(4, RoundingMode.HALF_UP));
            holding.setHoldingProfitRate(rate(holding.getHoldingProfit(), holding.getHoldingCost()));
            holding.setDailyProfit(dailyProfit);
        } else if (navPoint.dailyGrowthRate() != null) {
            BigDecimal dailyProfit = dailyProfitByRate(baseAmount, navPoint.dailyGrowthRate(), holdingShare, null);
            BigDecimal holdingAmount = baseAmount.add(dailyProfit).setScale(4, RoundingMode.HALF_UP);
            holding.setHoldingAmount(holdingAmount);
            holding.setHoldingProfit(holdingAmount.subtract(scale(holding.getHoldingCost())).setScale(4, RoundingMode.HALF_UP));
            holding.setHoldingProfitRate(rate(holding.getHoldingProfit(), holding.getHoldingCost()));
            holding.setDailyProfit(dailyProfit);
        } else {
            holding.setHoldingAmount(baseAmount);
            holding.setHoldingProfit(baseAmount.subtract(scale(holding.getHoldingCost())).setScale(4, RoundingMode.HALF_UP));
            holding.setHoldingProfitRate(rate(holding.getHoldingProfit(), holding.getHoldingCost()));
            holding.setDailyProfit(ZERO);
        }
        holding.setUpdateTime(LocalDateTime.now());
    }

    private void recalculateAccounts(List<FundHolding> holdings) {
        holdings.stream()
                .map(FundHolding::getAccountId)
                .distinct()
                .forEach(accountId -> {
                    Long userId = holdings.stream()
                            .filter(holding -> accountId.equals(holding.getAccountId()))
                            .map(FundHolding::getUserId)
                            .findFirst()
                            .orElse(null);
                    if (userId != null) {
                        portfolioAccountService.recalculateOwnedAccount(userId, accountId);
                    }
                });
    }

    private BigDecimal dailyProfit(BigDecimal holdingShare, BigDecimal currentNav, BigDecimal previousNav) {
        if (previousNav == null || previousNav.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return holdingShare.multiply(currentNav.subtract(previousNav)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal dailyProfitByRate(BigDecimal holdingAmount, BigDecimal estimateRate,
                                         BigDecimal holdingShare, BigDecimal previousNav) {
        if (estimateRate != null) {
            return amountChangeByRate(holdingAmount, estimateRate);
        }
        return ZERO;
    }

    private BigDecimal amountChangeByRate(BigDecimal baseAmount, BigDecimal changeRate) {
        if (changeRate == null) {
            return ZERO;
        }
        BigDecimal ratio = changeRate.divide(HUNDRED, 8, RoundingMode.HALF_UP);
        return scale(scale(baseAmount).multiply(ratio));
    }

    private BigDecimal positionRate(BigDecimal holdingAmount, BigDecimal totalAsset) {
        if (totalAsset == null || totalAsset.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return scale(holdingAmount).multiply(HUNDRED).divide(totalAsset, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal profit, BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return scale(profit).multiply(HUNDRED).divide(cost, 4, RoundingMode.HALF_UP);
    }

    private boolean officialNavPublishedFor(FundHolding holding, LocalDate navDate, LocalDate today) {
        LocalDate effectiveDate = officialNavEffectiveDate(holding, navDate);
        if (delayedOfficialNavFund(holding)) {
            return !effectiveDate.isBefore(today.minusDays(3)) && !effectiveDate.isAfter(today);
        }
        return !navDate.isBefore(today);
    }

    private void upsertSnapshot(FundHolding holding, LocalDate navDate, LocalDate snapshotDate, LocalDateTime now) {
        PortfolioAccount account = portfolioAccountMapper.selectById(holding.getAccountId());
        if (account == null) {
            throw new IllegalStateException("portfolio account not found");
        }
        HoldingSnapshot snapshot = new HoldingSnapshot();
        snapshot.setUserId(holding.getUserId());
        snapshot.setAccountId(holding.getAccountId());
        snapshot.setHoldingId(holding.getId());
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setTotalAsset(scale(account.getTotalAsset()));
        BigDecimal holdingAmount = effectiveHoldingAmount(holding);
        snapshot.setHoldingAmount(holdingAmount);
        snapshot.setHoldingProfit(effectiveHoldingProfit(holding, holdingAmount));
        snapshot.setDailyProfit(scale(holding.getDailyProfit()));
        snapshot.setPositionRate(positionRate(holdingAmount, account.getTotalAsset()));
        snapshot.setCreateTime(now);
        snapshot.setUpdateTime(now);
        snapshot.setDeleted(0);
        upsertSnapshot(snapshot);
    }

    private void upsertSnapshot(HoldingSnapshot snapshot) {
        upsertSnapshot(snapshot, null);
    }

    private void upsertSnapshot(HoldingSnapshot snapshot, HoldingSnapshot legacyDelayedSnapshot) {
        HoldingSnapshot existing = holdingSnapshotMapper.selectOne(new LambdaQueryWrapper<HoldingSnapshot>()
                .eq(HoldingSnapshot::getHoldingId, snapshot.getHoldingId())
                .eq(HoldingSnapshot::getSnapshotDate, snapshot.getSnapshotDate())
                .last("LIMIT 1"));
        if (existing == null) {
            existing = holdingSnapshotMapper.selectByHoldingAndDateIncludingDeleted(
                    snapshot.getHoldingId(), snapshot.getSnapshotDate());
            restoreSnapshotIfDeleted(existing);
        }
        if (existing == null) {
            if (legacyDelayedSnapshot != null) {
                snapshot.setId(legacyDelayedSnapshot.getId());
                snapshot.setCreateTime(legacyDelayedSnapshot.getCreateTime());
                snapshot.setDeleted(legacyDelayedSnapshot.getDeleted());
                holdingSnapshotMapper.updateById(snapshot);
                return;
            }
            try {
                holdingSnapshotMapper.insert(snapshot);
            } catch (DuplicateKeyException exception) {
                restoreSnapshotIfDeleted(holdingSnapshotMapper.selectByHoldingAndDateIncludingDeleted(
                        snapshot.getHoldingId(), snapshot.getSnapshotDate()));
            }
            return;
        }
        if (legacyDelayedSnapshot != null && legacyDelayedSnapshot.getId() != null) {
            holdingSnapshotMapper.deleteById(legacyDelayedSnapshot.getId());
        }
        if (historicalSnapshotDate(snapshot.getSnapshotDate())) {
            return;
        }
        snapshot.setId(existing.getId());
        snapshot.setCreateTime(existing.getCreateTime());
        snapshot.setDeleted(existing.getDeleted());
        holdingSnapshotMapper.updateById(snapshot);
    }

    private void restoreSnapshotIfDeleted(HoldingSnapshot snapshot) {
        if (snapshot != null && Integer.valueOf(1).equals(snapshot.getDeleted())) {
            holdingSnapshotMapper.restoreById(snapshot.getId());
            snapshot.setDeleted(0);
        }
    }

    private boolean historicalSnapshotDate(LocalDate snapshotDate) {
        return snapshotDate != null && snapshotDate.isBefore(LocalDate.now());
    }

    private LocalDate officialNavEffectiveDate(FundHolding holding, LocalDate navDate) {
        return OfficialNavTiming.effectiveDate(holding, navDate, tradingCalendarService);
    }

    private boolean delayedOfficialNavFund(FundHolding holding) {
        return OfficialNavTiming.isDelayedOfficialNavFund(holding);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private void randomDelay() {
        int maxMillis = properties.getScheduler().getBatchDelayMaxMillis();
        if (maxMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(maxMillis + 1));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
