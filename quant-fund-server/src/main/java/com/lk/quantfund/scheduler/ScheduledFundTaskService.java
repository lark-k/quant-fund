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
    private final PortfolioAccountService portfolioAccountService;
    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final HoldingSnapshotMapper holdingSnapshotMapper;
    private final FundValuationService fundValuationService;

    public ScheduledFundTaskService(QuantFundProperties properties,
                                    FundQueryService fundQueryService,
                                    AiAnalysisService aiAnalysisService,
                                    PortfolioAccountService portfolioAccountService,
                                    FundHoldingMapper fundHoldingMapper,
                                    PortfolioAccountMapper portfolioAccountMapper,
                                    HoldingSnapshotMapper holdingSnapshotMapper,
                                    FundValuationService fundValuationService) {
        this.properties = properties;
        this.fundQueryService = fundQueryService;
        this.aiAnalysisService = aiAnalysisService;
        this.portfolioAccountService = portfolioAccountService;
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.holdingSnapshotMapper = holdingSnapshotMapper;
        this.fundValuationService = fundValuationService;
    }

    @Transactional(rollbackFor = Exception.class)
    public SchedulerTaskResult refreshIntradayEstimates() {
        SchedulerTaskResult result = new SchedulerTaskResult();
        Map<String, List<FundHolding>> holdingsByFundCode = holdingsByFundCode(loadAllHoldings());
        for (Map.Entry<String, List<FundHolding>> entry : holdingsByFundCode.entrySet()) {
            try {
                if (!entry.getValue().isEmpty() && delayedOfficialNavFund(entry.getValue().getFirst())) {
                    continue;
                }
                FundEstimateDTO estimate = fundQueryService.getIntradayEstimate(entry.getKey(), false);
                for (FundHolding holding : entry.getValue()) {
                    applyEstimate(holding, estimate);
                    fundHoldingMapper.updateById(holding);
                }
                recalculateAccounts(entry.getValue());
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
                BigDecimal previousUnitNav = navPoints.stream()
                        .filter(point -> point.navDate().isBefore(latest.navDate()))
                        .max(Comparator.comparing(FundNavPointDTO::navDate))
                        .map(FundNavPointDTO::unitNav)
                        .orElse(null);
                for (FundHolding holding : entry.getValue()) {
                    applyOfficialNav(holding, latest, previousUnitNav);
                    fundHoldingMapper.updateById(holding);
                }
                recalculateAccounts(entry.getValue());
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
                snapshot.setHoldingAmount(scale(holding.getHoldingAmount()));
                snapshot.setHoldingProfit(scale(holding.getHoldingProfit()));
                snapshot.setDailyProfit(scale(holding.getDailyProfit()));
                snapshot.setPositionRate(positionRate(holding.getHoldingAmount(), account.getTotalAsset()));
                snapshot.setCreateTime(now);
                snapshot.setUpdateTime(now);
                snapshot.setDeleted(0);
                holdingSnapshotMapper.insert(snapshot);
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
        BigDecimal holdingAmount = holdingShare.multiply(estimateNav).setScale(4, RoundingMode.HALF_UP);
        holding.setCurrentEstimateNav(estimateNav);
        holding.setHoldingAmount(holdingAmount);
        holding.setHoldingProfit(holdingAmount.subtract(scale(holding.getHoldingCost())).setScale(4, RoundingMode.HALF_UP));
        holding.setHoldingProfitRate(rate(holding.getHoldingProfit(), holding.getHoldingCost()));
        BigDecimal estimateRate = estimate.estimateGrowthRate() == null ? null : estimate.estimateGrowthRate();
        FundValuationResult valuation = fundValuationService.estimate(
                holding.getFundCode(), holding.getFundName(), holding.getFundType(), estimateRate);
        holding.setDailyProfit(dailyProfitByRate(holdingAmount, valuation.themeRate(), holdingShare, holding.getLatestOfficialNav()));
        holding.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(estimate.fundName())) {
            holding.setFundName(estimate.fundName());
        }
    }

    private void applyOfficialNav(FundHolding holding, FundNavPointDTO navPoint, BigDecimal previousUnitNav) {
        BigDecimal unitNav = scale(navPoint.unitNav());
        BigDecimal previousNav = previousUnitNav == null ? holding.getLatestOfficialNav() : previousUnitNav;
        BigDecimal holdingShare = scale(holding.getHoldingShare());
        BigDecimal holdingAmount = holdingShare.multiply(unitNav).setScale(4, RoundingMode.HALF_UP);
        holding.setLatestOfficialNav(unitNav);
        holding.setCurrentEstimateNav(unitNav);
        holding.setHoldingAmount(holdingAmount);
        holding.setHoldingProfit(holdingAmount.subtract(scale(holding.getHoldingCost())).setScale(4, RoundingMode.HALF_UP));
        holding.setHoldingProfitRate(rate(holding.getHoldingProfit(), holding.getHoldingCost()));
        if (previousNav != null && previousNav.compareTo(BigDecimal.ZERO) > 0) {
            holding.setDailyProfit(dailyProfit(holdingShare, unitNav, previousNav));
        } else if (navPoint.dailyGrowthRate() != null) {
            holding.setDailyProfit(dailyProfitByRate(holdingAmount, navPoint.dailyGrowthRate(), holdingShare, null));
        } else {
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
            BigDecimal ratio = estimateRate.divide(HUNDRED, 8, RoundingMode.HALF_UP);
            if (previousNav != null && previousNav.compareTo(BigDecimal.ZERO) > 0
                    && holdingShare != null && holdingShare.compareTo(BigDecimal.ZERO) > 0) {
                return scale(holdingShare.multiply(previousNav).multiply(ratio));
            }
            BigDecimal factor = BigDecimal.ONE.add(ratio);
            if (factor.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal originalAmount = scale(holdingAmount).divide(factor, 4, RoundingMode.HALF_UP);
                return scale(originalAmount.multiply(ratio));
            }
            return ZERO;
        }
        return ZERO;
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
        LocalDate earliestEffectiveDate = delayedOfficialNavFund(holding) ? today.minusDays(3) : today;
        return !navDate.isBefore(earliestEffectiveDate);
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
