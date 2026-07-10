package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.ScreenerBacktestResult;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerQualityScore;
import com.lk.quantfund.mapper.ScreenerBacktestResultMapper;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerQualityScoreMapper;
import com.lk.quantfund.service.FundScreenerBacktestService;
import com.lk.quantfund.vo.screener.FundScreenerBacktestMetricVO;
import com.lk.quantfund.vo.screener.FundScreenerStrategyPolicyVO;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import com.lk.quantfund.vo.screener.FundScreenerValidationVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class FundScreenerBacktestServiceImpl implements FundScreenerBacktestService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");
    private static final int PRIMARY_HORIZON = 60;
    private static final int[] HORIZONS = {20, 60, 120};
    private static final List<String> BUCKETS = List.of("TOP_5", "TOP_10", "WATCH", "NEUTRAL", "AVOID");
    private static final String MODEL_VERSION = "screener-rule-v2";

    private final ScreenerQualityScoreMapper screenerQualityScoreMapper;
    private final ScreenerFundNavDailyMapper screenerFundNavDailyMapper;
    private final ScreenerBacktestResultMapper screenerBacktestResultMapper;
    private final QuantFundProperties.ScreenerStrategy strategy;

    @Autowired
    public FundScreenerBacktestServiceImpl(ScreenerQualityScoreMapper screenerQualityScoreMapper,
                                           ScreenerFundNavDailyMapper screenerFundNavDailyMapper,
                                           ScreenerBacktestResultMapper screenerBacktestResultMapper,
                                           QuantFundProperties properties) {
        this.screenerQualityScoreMapper = screenerQualityScoreMapper;
        this.screenerFundNavDailyMapper = screenerFundNavDailyMapper;
        this.screenerBacktestResultMapper = screenerBacktestResultMapper;
        this.strategy = properties.getScreenerStrategy();
    }

    FundScreenerBacktestServiceImpl(ScreenerQualityScoreMapper screenerQualityScoreMapper,
                                    ScreenerFundNavDailyMapper screenerFundNavDailyMapper,
                                    ScreenerBacktestResultMapper screenerBacktestResultMapper) {
        this(screenerQualityScoreMapper, screenerFundNavDailyMapper, screenerBacktestResultMapper,
                new QuantFundProperties());
    }

    public FundScreenerBacktestServiceImpl(ScreenerQualityScoreMapper screenerQualityScoreMapper,
                                           ScreenerFundNavDailyMapper screenerFundNavDailyMapper) {
        this(screenerQualityScoreMapper, screenerFundNavDailyMapper, null, new QuantFundProperties());
    }

    @Override
    public FundScreenerTaskResultVO runIncremental() {
        long started = System.currentTimeMillis();
        if (screenerBacktestResultMapper == null) {
            return FundScreenerTaskResultVO.skipped("SCREENER_BACKTEST", "回测结果存储尚未配置");
        }
        RunProgress progress = new RunProgress();
        int inserted = 0;
        int skipped = 0;
        Map<String, List<ScreenerFundNavDaily>> navCache = new HashMap<>();
        Set<String> existingKeys = existingResultKeys();
        List<ScreenerQualityScore> scores = screenerQualityScoreMapper.selectList(
                new LambdaQueryWrapper<ScreenerQualityScore>()
                        .eq(ScreenerQualityScore::getModelVersion, MODEL_VERSION)
                        .orderByAsc(ScreenerQualityScore::getScoreDate)
                        .orderByDesc(ScreenerQualityScore::getQualityScore));
        Map<LocalDate, List<ScreenerQualityScore>> scoresByDate = scores.stream()
                .filter(score -> score.getScoreDate() != null && MODEL_VERSION.equals(score.getModelVersion()))
                .collect(Collectors.groupingBy(ScreenerQualityScore::getScoreDate, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<LocalDate, List<ScreenerQualityScore>> entry : scoresByDate.entrySet()) {
            List<ScreenerQualityScore> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(ScreenerQualityScore::getQualityScore,
                            Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                    .toList();
            for (int horizon : HORIZONS) {
                Map<ScreenerQualityScore, BacktestObservation> eligible = eligibleObservations(
                        sorted, horizon, navCache, progress);
                if (eligible.isEmpty()) {
                    continue;
                }
                BigDecimal allAverage = average(eligible.values().stream()
                        .map(BacktestObservation::forwardReturn).toList());
                for (String bucket : BUCKETS) {
                    List<BacktestObservation> observations = bucketScores(sorted, bucket).stream()
                            .map(eligible::get)
                            .filter(java.util.Objects::nonNull)
                            .toList();
                    if (observations.isEmpty()) {
                        continue;
                    }
                    String key = resultKey(entry.getKey(), horizon, bucket);
                    if (existingKeys.contains(key)) {
                        skipped++;
                        continue;
                    }
                    try {
                        screenerBacktestResultMapper.insert(toEntity(entry.getKey(), horizon, bucket,
                                observations, allAverage));
                        existingKeys.add(key);
                        inserted++;
                    } catch (DuplicateKeyException exception) {
                        existingKeys.add(key);
                        skipped++;
                    } catch (RuntimeException exception) {
                        progress.failure(key, exception);
                    }
                }
            }
        }

        String status = progress.failed == 0 ? "SUCCESS" : inserted > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        String message = String.format(Locale.ROOT, "增量回测完成：新增%d条，跳过%d条，失败%d条",
                inserted, skipped, progress.failed);
        return new FundScreenerTaskResultVO("SCREENER_BACKTEST", status, inserted, progress.failed, skipped,
                System.currentTimeMillis() - started, progress.errors, message, LocalDateTime.now());
    }

    @Override
    public FundScreenerValidationVO getValidation() {
        List<ScreenerBacktestResult> rows = screenerBacktestResultMapper == null
                ? List.of()
                : screenerBacktestResultMapper.selectList(new LambdaQueryWrapper<ScreenerBacktestResult>()
                        .eq(ScreenerBacktestResult::getModelVersion, MODEL_VERSION)
                        .orderByAsc(ScreenerBacktestResult::getScoreDate));
        List<FundScreenerBacktestMetricVO> metrics = aggregate(rows);
        Effectiveness effectiveness = effectiveness(metrics);
        String latestRunDate = rows.stream().map(ScreenerBacktestResult::getRunDate)
                .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).map(LocalDate::toString).orElse(null);
        String earliestScoreDate = rows.stream().map(ScreenerBacktestResult::getScoreDate)
                .filter(java.util.Objects::nonNull).min(LocalDate::compareTo).map(LocalDate::toString).orElse(null);
        String latestScoreDate = rows.stream().map(ScreenerBacktestResult::getScoreDate)
                .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).map(LocalDate::toString).orElse(null);
        return new FundScreenerValidationVO(latestRunDate, earliestScoreDate, latestScoreDate,
                effectiveness.status(), effectiveness.conclusion(), advice(effectiveness.status()),
                policy(), metrics);
    }

    private Set<String> existingResultKeys() {
        return screenerBacktestResultMapper.selectList(new LambdaQueryWrapper<ScreenerBacktestResult>()
                        .eq(ScreenerBacktestResult::getModelVersion, MODEL_VERSION))
                .stream()
                .filter(row -> row.getScoreDate() != null && row.getHorizonDays() != null && row.getBucketName() != null)
                .map(row -> resultKey(row.getScoreDate(), row.getHorizonDays(), row.getBucketName()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Map<ScreenerQualityScore, BacktestObservation> eligibleObservations(
            List<ScreenerQualityScore> scores, int horizon,
            Map<String, List<ScreenerFundNavDaily>> navCache,
            RunProgress progress) {
        Map<ScreenerQualityScore, BacktestObservation> eligible = new HashMap<>();
        for (ScreenerQualityScore score : scores) {
            try {
                BacktestObservation observation = forwardObservation(score, horizon, navCache);
                if (observation != null) {
                    eligible.put(score, observation);
                }
            } catch (RuntimeException exception) {
                progress.failure(score.getScoreDate() + "|" + horizon + "|" + score.getFundCode(), exception);
            }
        }
        return eligible;
    }

    private BacktestObservation forwardObservation(ScreenerQualityScore score, int horizon,
                                                   Map<String, List<ScreenerFundNavDaily>> navCache) {
        if (score.getScoreDate() == null || score.getFundCode() == null) {
            return null;
        }
        List<ScreenerFundNavDaily> navPoints = navCache.computeIfAbsent(score.getFundCode(), fundCode ->
                screenerFundNavDailyMapper.selectList(new LambdaQueryWrapper<ScreenerFundNavDaily>()
                        .eq(ScreenerFundNavDaily::getFundCode, fundCode)
                        .orderByAsc(ScreenerFundNavDaily::getNavDate)));
        ScreenerFundNavDaily base = navPoints.stream()
                .filter(point -> validNav(point) && !point.getNavDate().isAfter(score.getScoreDate()))
                .max(Comparator.comparing(ScreenerFundNavDaily::getNavDate))
                .orElse(null);
        if (base == null) {
            return null;
        }
        List<ScreenerFundNavDaily> future = navPoints.stream()
                .filter(this::validNav)
                .filter(point -> point.getNavDate().isAfter(score.getScoreDate()))
                .sorted(Comparator.comparing(ScreenerFundNavDaily::getNavDate))
                .toList();
        if (future.size() < horizon) {
            return null;
        }
        List<ScreenerFundNavDaily> window = future.subList(0, horizon);
        return new BacktestObservation(rate(window.getLast().getUnitNav(), base.getUnitNav()),
                forwardMaxDrawdown(base.getUnitNav(), window));
    }

    private boolean validNav(ScreenerFundNavDaily point) {
        return point.getNavDate() != null && point.getUnitNav() != null
                && point.getUnitNav().compareTo(BigDecimal.ZERO) > 0;
    }

    private List<ScreenerQualityScore> bucketScores(List<ScreenerQualityScore> sorted, String bucket) {
        if ("TOP_5".equals(bucket)) {
            return sorted.subList(0, Math.min(sorted.size(), Math.max(1, (int) Math.ceil(sorted.size() * 0.05))));
        }
        if ("TOP_10".equals(bucket)) {
            return sorted.subList(0, Math.min(sorted.size(), Math.max(1, (int) Math.ceil(sorted.size() * 0.10))));
        }
        return sorted.stream().filter(score -> bucket.equals(score.getRecommendLevel())).toList();
    }

    private ScreenerBacktestResult toEntity(LocalDate scoreDate, int horizon, String bucket,
                                             List<BacktestObservation> observations, BigDecimal allAverage) {
        BigDecimal averageReturn = average(observations.stream().map(BacktestObservation::forwardReturn).toList());
        long wins = observations.stream().filter(item -> item.forwardReturn().compareTo(BigDecimal.ZERO) > 0).count();
        BigDecimal winRate = BigDecimal.valueOf(wins).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(observations.size()), 4, RoundingMode.HALF_UP);
        BigDecimal maxDrawdown = observations.stream().map(BacktestObservation::maxDrawdown)
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        LocalDateTime now = LocalDateTime.now();
        ScreenerBacktestResult entity = new ScreenerBacktestResult();
        entity.setRunDate(LocalDate.now());
        entity.setScoreDate(scoreDate);
        entity.setHorizonDays(horizon);
        entity.setBucketName(bucket);
        entity.setSampleCount(observations.size());
        entity.setAvgForwardReturn(averageReturn);
        entity.setWinRate(winRate);
        entity.setAvgExcessReturn(averageReturn.subtract(allAverage).setScale(4, RoundingMode.HALF_UP));
        entity.setMaxDrawdown(maxDrawdown);
        entity.setModelVersion(MODEL_VERSION);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
        return entity;
    }

    private List<FundScreenerBacktestMetricVO> aggregate(List<ScreenerBacktestResult> rows) {
        Map<AggregateKey, List<ScreenerBacktestResult>> grouped = rows.stream()
                .filter(row -> row.getHorizonDays() != null && row.getBucketName() != null)
                .collect(Collectors.groupingBy(row -> new AggregateKey(row.getBucketName(), row.getHorizonDays())));
        return grouped.entrySet().stream()
                .map(entry -> aggregate(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt((FundScreenerBacktestMetricVO item) -> BUCKETS.indexOf(item.bucketName()))
                        .thenComparingInt(FundScreenerBacktestMetricVO::horizonDays))
                .toList();
    }

    private FundScreenerBacktestMetricVO aggregate(AggregateKey key, List<ScreenerBacktestResult> rows) {
        int sampleCount = rows.stream().map(ScreenerBacktestResult::getSampleCount)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        int scoreDateCount = (int) rows.stream().map(ScreenerBacktestResult::getScoreDate)
                .filter(java.util.Objects::nonNull).distinct().count();
        BigDecimal averageReturn = weightedAverage(rows, ScreenerBacktestResult::getAvgForwardReturn);
        BigDecimal winRate = weightedAverage(rows, ScreenerBacktestResult::getWinRate);
        BigDecimal excessReturn = weightedAverage(rows, ScreenerBacktestResult::getAvgExcessReturn);
        BigDecimal maxDrawdown = rows.stream().map(ScreenerBacktestResult::getMaxDrawdown)
                .filter(java.util.Objects::nonNull).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        boolean significant = sampleCount >= strategy.getMinValidationSamples()
                && scoreDateCount >= strategy.getMinValidationScoreDates();
        return new FundScreenerBacktestMetricVO(key.bucket(), key.horizon(), sampleCount, scoreDateCount,
                averageReturn.doubleValue(), winRate.doubleValue(), excessReturn.doubleValue(),
                maxDrawdown.doubleValue(), significant);
    }

    private BigDecimal weightedAverage(List<ScreenerBacktestResult> rows,
                                       Function<ScreenerBacktestResult, BigDecimal> valueExtractor) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        int totalWeight = 0;
        for (ScreenerBacktestResult row : rows) {
            int weight = row.getSampleCount() == null ? 0 : row.getSampleCount();
            BigDecimal value = valueExtractor.apply(row);
            if (weight > 0 && value != null) {
                weightedSum = weightedSum.add(value.multiply(BigDecimal.valueOf(weight)));
                totalWeight += weight;
            }
        }
        return totalWeight == 0 ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : weightedSum.divide(BigDecimal.valueOf(totalWeight), 4, RoundingMode.HALF_UP);
    }

    private Effectiveness effectiveness(List<FundScreenerBacktestMetricVO> metrics) {
        FundScreenerBacktestMetricVO top10 = findMetric(metrics, "TOP_10", PRIMARY_HORIZON);
        FundScreenerBacktestMetricVO neutral = findMetric(metrics, "NEUTRAL", PRIMARY_HORIZON);
        FundScreenerBacktestMetricVO avoid = findMetric(metrics, "AVOID", PRIMARY_HORIZON);
        if (!significant(top10) || !significant(neutral) || !significant(avoid)) {
            return new Effectiveness("INSUFFICIENT", "60日回测样本不足，当前不具备统计意义，请继续积累评分日期和未来净值。");
        }
        if (top10.avgExcessReturn() > 0 && top10.avgForwardReturn() > neutral.avgForwardReturn()
                && avoid.avgForwardReturn() <= neutral.avgForwardReturn()) {
            return new Effectiveness("EFFECTIVE", String.format(Locale.ROOT,
                    "TOP_10 在60日维度跑赢全样本 %.2f%%，胜率 %.2f%%，且 AVOID 未跑赢 NEUTRAL，策略当前有效。",
                    top10.avgExcessReturn(), top10.winRate()));
        }
        if (top10.avgExcessReturn() < 0 && avoid.avgForwardReturn() >= top10.avgForwardReturn()) {
            return new Effectiveness("FAILED", String.format(Locale.ROOT,
                    "TOP_10 在60日维度跑输全样本 %.2f%%，且 AVOID 表现不弱于 TOP_10，当前分层失效。",
                    Math.abs(top10.avgExcessReturn())));
        }
        return new Effectiveness("NEUTRAL", String.format(Locale.ROOT,
                "TOP_10 在60日维度超额收益为 %.2f%%，分层差异尚不稳定，策略当前中性。",
                top10.avgExcessReturn()));
    }

    private FundScreenerBacktestMetricVO findMetric(List<FundScreenerBacktestMetricVO> metrics,
                                                     String bucket, int horizon) {
        return metrics.stream().filter(item -> bucket.equals(item.bucketName()) && item.horizonDays() == horizon)
                .findFirst().orElse(null);
    }

    private boolean significant(FundScreenerBacktestMetricVO metric) {
        return metric != null && metric.statisticallySignificant();
    }

    private List<String> advice(String status) {
        return switch (status) {
            case "EFFECTIVE" -> List.of("保持当前推荐阈值，持续观察20/60/120日分层稳定性。", "在引入新评分因子前，先确认多个市场阶段仍保持正超额收益。");
            case "NEUTRAL" -> List.of("暂不放宽推荐阈值，优先检查 WATCH 与 NEUTRAL 的区分度。", "可在复盘后小幅收紧 WATCH 准入，再用新增评分日重新验证。");
            case "FAILED" -> List.of("不要自动放宽或下调阈值，先复核收益质量与回撤控制权重。", "依据评分日证据调整边界后，再运行增量回测确认修复效果。");
            default -> List.of("保持当前阈值不变，继续积累至少30个样本和3个评分日期。", "样本充分前不依据短期结果修改生产推荐等级。");
        };
    }

    private FundScreenerStrategyPolicyVO policy() {
        return new FundScreenerStrategyPolicyVO(strategy.getStrongMinScore(), strategy.getStrongTopPercent(),
                strategy.getWatchMinScore(), strategy.getWatchTopPercent(), strategy.getNeutralMinScore(),
                strategy.getMinValidationSamples(), strategy.getMinValidationScoreDates());
    }

    private String resultKey(LocalDate scoreDate, int horizon, String bucket) {
        return scoreDate + "|" + horizon + "|" + bucket + "|" + MODEL_VERSION;
    }

    private BigDecimal forwardMaxDrawdown(BigDecimal baseNav, List<ScreenerFundNavDaily> points) {
        BigDecimal peak = baseNav;
        BigDecimal maxDrawdown = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        for (ScreenerFundNavDaily point : points) {
            if (point.getUnitNav().compareTo(peak) > 0) {
                peak = point.getUnitNav();
            }
            BigDecimal drawdown = rate(point.getUnitNav(), peak);
            if (drawdown.compareTo(maxDrawdown) < 0) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    private BigDecimal rate(BigDecimal latest, BigDecimal base) {
        return latest.subtract(base).multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private record BacktestObservation(BigDecimal forwardReturn, BigDecimal maxDrawdown) {
    }

    private record AggregateKey(String bucket, int horizon) {
    }

    private record Effectiveness(String status, String conclusion) {
    }

    private static final class RunProgress {
        private int failed;
        private final List<String> errors = new ArrayList<>();

        private void failure(String unit, RuntimeException exception) {
            failed++;
            if (errors.size() < 10) {
                errors.add(unit + ": " + exception.getMessage());
            }
        }
    }
}
