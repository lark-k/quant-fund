package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerBacktestResult;
import com.lk.quantfund.entity.ScreenerQualityScore;
import com.lk.quantfund.mapper.ScreenerBacktestResultMapper;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerQualityScoreMapper;
import com.lk.quantfund.service.FundScreenerBacktestService;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FundScreenerBacktestServiceImpl implements FundScreenerBacktestService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");
    private static final int[] HORIZONS = {20, 60, 120};

    private final ScreenerQualityScoreMapper screenerQualityScoreMapper;
    private final ScreenerFundNavDailyMapper screenerFundNavDailyMapper;
    private final ScreenerBacktestResultMapper screenerBacktestResultMapper;
    private Map<String, List<ScreenerFundNavDaily>> navCache = new HashMap<>();

    @Autowired
    public FundScreenerBacktestServiceImpl(ScreenerQualityScoreMapper screenerQualityScoreMapper,
                                           ScreenerFundNavDailyMapper screenerFundNavDailyMapper,
                                           ScreenerBacktestResultMapper screenerBacktestResultMapper) {
        this.screenerQualityScoreMapper = screenerQualityScoreMapper;
        this.screenerFundNavDailyMapper = screenerFundNavDailyMapper;
        this.screenerBacktestResultMapper = screenerBacktestResultMapper;
    }

    public FundScreenerBacktestServiceImpl(ScreenerQualityScoreMapper screenerQualityScoreMapper,
                                           ScreenerFundNavDailyMapper screenerFundNavDailyMapper) {
        this(screenerQualityScoreMapper, screenerFundNavDailyMapper, null);
    }

    @Override
    public FundScreenerTaskResultVO backtest() {
        long started = System.currentTimeMillis();
        int success = 0;
        int failure = 0;
        List<String> errors = new ArrayList<>();
        List<BigDecimal> forwardReturns = new ArrayList<>();
        navCache = new HashMap<>();
        List<ScreenerQualityScore> scores = screenerQualityScoreMapper.selectList(new LambdaQueryWrapper<ScreenerQualityScore>()
                .orderByDesc(ScreenerQualityScore::getScoreDate)
                .orderByDesc(ScreenerQualityScore::getQualityScore)
                .last("LIMIT 200"));
        List<BacktestSummary> summaries = summarize(scores);
        for (ScreenerQualityScore score : scores) {
            try {
                forwardReturns.add(forwardReturn(score));
                success++;
            } catch (RuntimeException exception) {
                failure++;
                errors.add(score.getFundCode() + ": " + exception.getMessage());
            }
        }
        BigDecimal averageForwardReturn = average(forwardReturns);
        return new FundScreenerTaskResultVO(
                "SCREENER_BACKTEST",
                failure == 0 ? "SUCCESS" : success > 0 ? "PARTIAL_SUCCESS" : "FAILED",
                success,
                failure,
                0,
                System.currentTimeMillis() - started,
                errors,
                backtestMessage(success, averageForwardReturn, summaries),
                LocalDateTime.now()
        );
    }

    private List<BacktestSummary> summarize(List<ScreenerQualityScore> scores) {
        Map<LocalDate, List<ScreenerQualityScore>> byDate = scores.stream()
                .filter(score -> score.getScoreDate() != null)
                .collect(Collectors.groupingBy(ScreenerQualityScore::getScoreDate));
        List<BacktestSummary> summaries = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ScreenerQualityScore>> entry : byDate.entrySet()) {
            List<ScreenerQualityScore> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(ScreenerQualityScore::getQualityScore, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                    .toList();
            for (int horizon : HORIZONS) {
                List<BacktestObservation> all = observations(sorted, horizon);
                BigDecimal allAverage = average(all.stream().map(BacktestObservation::forwardReturn).toList());
                for (String bucket : List.of("TOP_5", "TOP_10", "WATCH", "NEUTRAL", "AVOID")) {
                    List<ScreenerQualityScore> bucketScores = bucketScores(sorted, bucket);
                    List<BacktestObservation> observations = observations(bucketScores, horizon);
                    if (observations.isEmpty()) {
                        continue;
                    }
                    BacktestSummary summary = summary(entry.getKey(), horizon, bucket, observations, allAverage);
                    summaries.add(summary);
                    insertSummary(summary);
                }
            }
        }
        return summaries;
    }

    private List<ScreenerQualityScore> bucketScores(List<ScreenerQualityScore> sorted, String bucket) {
        if ("TOP_5".equals(bucket)) {
            return sorted.subList(0, Math.max(1, (int) Math.ceil(sorted.size() * 0.05)));
        }
        if ("TOP_10".equals(bucket)) {
            return sorted.subList(0, Math.max(1, (int) Math.ceil(sorted.size() * 0.10)));
        }
        return sorted.stream()
                .filter(score -> bucket.equals(score.getRecommendLevel()))
                .toList();
    }

    private List<BacktestObservation> observations(List<ScreenerQualityScore> scores, int horizon) {
        List<BacktestObservation> observations = new ArrayList<>();
        for (ScreenerQualityScore score : scores) {
            try {
                observations.add(forwardObservation(score, horizon));
            } catch (RuntimeException ignored) {
                // Individual horizon misses are expected for recent score dates.
            }
        }
        return observations;
    }

    private BacktestObservation forwardObservation(ScreenerQualityScore score, int horizon) {
        List<ScreenerFundNavDaily> navPoints = navPoints(score);
        ScreenerFundNavDaily base = baseNav(score, navPoints);
        List<ScreenerFundNavDaily> futurePoints = navPoints.stream()
                .filter(point -> point.getNavDate() != null && point.getNavDate().isAfter(score.getScoreDate()))
                .filter(point -> point.getUnitNav() != null && point.getUnitNav().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (futurePoints.size() < horizon) {
            throw new IllegalStateException("future nav not found");
        }
        ScreenerFundNavDaily future = futurePoints.get(horizon - 1);
        BigDecimal forwardReturn = rate(future.getUnitNav(), base.getUnitNav());
        BigDecimal maxDrawdown = forwardMaxDrawdown(base.getUnitNav(), futurePoints.subList(0, horizon));
        return new BacktestObservation(forwardReturn, maxDrawdown);
    }

    private BacktestSummary summary(LocalDate scoreDate, int horizon, String bucket, List<BacktestObservation> observations, BigDecimal allAverage) {
        BigDecimal averageReturn = average(observations.stream().map(BacktestObservation::forwardReturn).toList());
        long wins = observations.stream().filter(item -> item.forwardReturn().compareTo(BigDecimal.ZERO) > 0).count();
        BigDecimal winRate = BigDecimal.valueOf(wins).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(observations.size()), 4, RoundingMode.HALF_UP);
        BigDecimal maxDrawdown = observations.stream()
                .map(BacktestObservation::maxDrawdown)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        return new BacktestSummary(scoreDate, horizon, bucket, observations.size(), averageReturn,
                winRate, averageReturn.subtract(allAverage), maxDrawdown);
    }

    private void insertSummary(BacktestSummary summary) {
        if (screenerBacktestResultMapper == null) {
            return;
        }
        ScreenerBacktestResult entity = new ScreenerBacktestResult();
        LocalDateTime now = LocalDateTime.now();
        entity.setRunDate(LocalDate.now());
        entity.setScoreDate(summary.scoreDate());
        entity.setHorizonDays(summary.horizonDays());
        entity.setBucketName(summary.bucketName());
        entity.setSampleCount(summary.sampleCount());
        entity.setAvgForwardReturn(summary.avgForwardReturn());
        entity.setWinRate(summary.winRate());
        entity.setAvgExcessReturn(summary.avgExcessReturn());
        entity.setMaxDrawdown(summary.maxDrawdown());
        entity.setModelVersion("screener-rule-v2");
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
        screenerBacktestResultMapper.insert(entity);
    }

    private String backtestMessage(int success, BigDecimal averageForwardReturn, List<BacktestSummary> summaries) {
        String summaryText = summaries.stream()
                .limit(9)
                .map(item -> item.bucketName() + item.horizonDays() + "\u65e5=" + item.avgForwardReturn().setScale(4, RoundingMode.HALF_UP) + "%")
                .collect(Collectors.joining("; "));
        if (summaryText.isBlank()) {
            summaryText = "\u5206\u5c42\u6837\u672c\u4e0d\u8db3";
        }
        return "\u5df2\u8bc4\u4f30" + success + "\u53ea\u57fa\u91d1\uff0c\u5e73\u5747\u672a\u6765\u6536\u76ca "
                + averageForwardReturn.setScale(4, RoundingMode.HALF_UP) + "%; " + summaryText;
    }

    private BigDecimal forwardReturn(ScreenerQualityScore score) {
        if (score.getScoreDate() == null) {
            throw new IllegalStateException("score date not found");
        }
        List<ScreenerFundNavDaily> navPoints = navPoints(score);
        ScreenerFundNavDaily base = baseNav(score, navPoints);
        ScreenerFundNavDaily future = navPoints.stream()
                .filter(point -> point.getNavDate() != null && point.getNavDate().isAfter(score.getScoreDate()))
                .filter(point -> point.getUnitNav() != null && point.getUnitNav().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(ScreenerFundNavDaily::getNavDate))
                .orElseThrow(() -> new IllegalStateException("future nav not found"));
        return future.getUnitNav().subtract(base.getUnitNav())
                .multiply(HUNDRED)
                .divide(base.getUnitNav(), 4, RoundingMode.HALF_UP);
    }

    private List<ScreenerFundNavDaily> navPoints(ScreenerQualityScore score) {
        return navCache.computeIfAbsent(score.getFundCode(), fundCode ->
                screenerFundNavDailyMapper.selectList(new LambdaQueryWrapper<ScreenerFundNavDaily>()
                        .eq(ScreenerFundNavDaily::getFundCode, fundCode)
                        .orderByAsc(ScreenerFundNavDaily::getNavDate)));
    }

    private ScreenerFundNavDaily baseNav(ScreenerQualityScore score, List<ScreenerFundNavDaily> navPoints) {
        return navPoints.stream()
                .filter(point -> point.getNavDate() != null && !point.getNavDate().isAfter(score.getScoreDate()))
                .filter(point -> point.getUnitNav() != null && point.getUnitNav().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(ScreenerFundNavDaily::getNavDate))
                .orElseThrow(() -> new IllegalStateException("base nav not found"));
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
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private record BacktestObservation(BigDecimal forwardReturn, BigDecimal maxDrawdown) {
    }

    private record BacktestSummary(LocalDate scoreDate, int horizonDays, String bucketName, int sampleCount,
                                   BigDecimal avgForwardReturn, BigDecimal winRate, BigDecimal avgExcessReturn,
                                   BigDecimal maxDrawdown) {
    }
}
