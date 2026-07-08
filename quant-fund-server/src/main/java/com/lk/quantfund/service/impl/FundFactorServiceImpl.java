package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.entity.ScreenerFactorSnapshot;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerFundUniverse;
import com.lk.quantfund.entity.ScreenerUniverseFilter;
import com.lk.quantfund.mapper.ScreenerFactorSnapshotMapper;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerFundUniverseMapper;
import com.lk.quantfund.mapper.ScreenerUniverseFilterMapper;
import com.lk.quantfund.service.FundFactorService;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FundFactorServiceImpl implements FundFactorService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private final ScreenerUniverseFilterMapper screenerUniverseFilterMapper;
    private final ScreenerFundNavDailyMapper screenerFundNavDailyMapper;
    private final ScreenerFactorSnapshotMapper screenerFactorSnapshotMapper;
    private final ScreenerFundUniverseMapper screenerFundUniverseMapper;

    public FundFactorServiceImpl(ScreenerUniverseFilterMapper screenerUniverseFilterMapper,
                                 ScreenerFundNavDailyMapper screenerFundNavDailyMapper,
                                 ScreenerFactorSnapshotMapper screenerFactorSnapshotMapper,
                                 ScreenerFundUniverseMapper screenerFundUniverseMapper) {
        this.screenerUniverseFilterMapper = screenerUniverseFilterMapper;
        this.screenerFundNavDailyMapper = screenerFundNavDailyMapper;
        this.screenerFactorSnapshotMapper = screenerFactorSnapshotMapper;
        this.screenerFundUniverseMapper = screenerFundUniverseMapper;
    }

    @Override
    public FundScreenerTaskResultVO refreshFactors() {
        long started = System.currentTimeMillis();
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        List<FactorCandidate> candidates = new ArrayList<>();
        List<ScreenerUniverseFilter> includedFunds = screenerUniverseFilterMapper.selectList(new LambdaQueryWrapper<ScreenerUniverseFilter>()
                .eq(ScreenerUniverseFilter::getIncluded, 1)
                .orderByAsc(ScreenerUniverseFilter::getFundCode));
        for (ScreenerUniverseFilter filter : includedFunds) {
            try {
                List<ScreenerFundNavDaily> navPoints = screenerFundNavDailyMapper.selectList(new LambdaQueryWrapper<ScreenerFundNavDaily>()
                        .eq(ScreenerFundNavDaily::getFundCode, filter.getFundCode())
                        .orderByAsc(ScreenerFundNavDaily::getNavDate));
                if (navPoints.size() < 2) {
                    throw new IllegalStateException("净值样本不足");
                }
                candidates.add(new FactorCandidate(filter.getUniverseType(), calculate(filter.getFundCode(), navPoints)));
                success++;
            } catch (RuntimeException exception) {
                failed++;
                errors.add(filter.getFundCode() + ": " + exception.getMessage());
            }
        }
        applyPeerPercentiles(candidates);
        candidates.forEach(candidate -> upsert(candidate.snapshot()));
        return new FundScreenerTaskResultVO(
                "REFRESH_FACTORS",
                failed == 0 ? "SUCCESS" : success > 0 ? "PARTIAL_SUCCESS" : "FAILED",
                success,
                failed,
                0,
                System.currentTimeMillis() - started,
                errors,
                failed == 0 ? "基金优选因子刷新完成" : "基金优选因子刷新部分失败",
                LocalDateTime.now()
        );
    }

    private ScreenerFactorSnapshot calculate(String fundCode, List<ScreenerFundNavDaily> navPoints) {
        List<ScreenerFundNavDaily> points = navPoints.stream()
                .filter(point -> point.getNavDate() != null && point.getUnitNav() != null && point.getUnitNav().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(ScreenerFundNavDaily::getNavDate))
                .toList();
        ScreenerFundNavDaily latest = points.getLast();
        ScreenerFundUniverse universe = screenerFundUniverseMapper.selectOne(new LambdaQueryWrapper<ScreenerFundUniverse>()
                .eq(ScreenerFundUniverse::getFundCode, fundCode)
                .last("LIMIT 1"));
        ScreenerFactorSnapshot snapshot = new ScreenerFactorSnapshot();
        snapshot.setFundCode(fundCode);
        snapshot.setFactorDate(latest.getNavDate());
        snapshot.setReturn20d(returnByWindow(points, 20));
        snapshot.setReturn60d(returnByWindow(points, 60));
        snapshot.setReturn120d(returnByWindow(points, 120));
        snapshot.setReturn250d(returnByWindow(points, 250));
        snapshot.setAnnualReturn250d(annualReturn(points, 250));
        snapshot.setVolatility60d(volatility(points, 60));
        snapshot.setVolatility120d(volatility(points, 120));
        snapshot.setMaxDrawdown60d(maxDrawdown(points, 60));
        snapshot.setMaxDrawdown120d(maxDrawdown(points, 120));
        snapshot.setPositiveDayRatio60d(positiveDayRatio(points, 60));
        snapshot.setTrendSlope60d(trendSlope(points, 60));
        snapshot.setExcessReturn60d(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        snapshot.setExcessReturn120d(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        snapshot.setBenchmarkCode(benchmarkCode(universe));
        snapshot.setReturnDrawdownRatio120d(returnDrawdownRatio(rankingReturn(snapshot), snapshot.getMaxDrawdown120d()));
        snapshot.setReturnConsistencyScore(returnConsistencyScore(snapshot));
        snapshot.setFundAgeYears(fundAgeYears(universe, latest.getNavDate()));
        snapshot.setFundSize(universe == null ? null : universe.getFundSize());
        snapshot.setNavSampleSize(points.size());
        snapshot.setSourceName("SCREENER");
        LocalDateTime now = LocalDateTime.now();
        snapshot.setCreateTime(now);
        snapshot.setUpdateTime(now);
        snapshot.setDeleted(0);
        return snapshot;
    }

    private void applyPeerPercentiles(List<FactorCandidate> candidates) {
        List<String> universeTypes = candidates.stream()
                .map(FactorCandidate::universeType)
                .map(this::normalizedUniverseType)
                .distinct()
                .toList();
        for (String universeType : universeTypes) {
            List<ScreenerFactorSnapshot> peers = candidates.stream()
                    .filter(candidate -> normalizedUniverseType(candidate.universeType()).equals(universeType))
                    .map(FactorCandidate::snapshot)
                    .sorted(Comparator.comparing(this::rankingReturn, Comparator.nullsFirst(BigDecimal::compareTo)).reversed())
                    .toList();
            for (int index = 0; index < peers.size(); index++) {
                BigDecimal percentile = BigDecimal.valueOf(index + 1L)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(peers.size()), 4, RoundingMode.HALF_UP);
                peers.get(index).setPeerPercentile(percentile);
            }
        }
    }

    private String normalizedUniverseType(String universeType) {
        return StringUtils.hasText(universeType) ? universeType.trim().toUpperCase() : "UNKNOWN";
    }

    private BigDecimal rankingReturn(ScreenerFactorSnapshot snapshot) {
        if (snapshot.getReturn120d() != null) {
            return snapshot.getReturn120d();
        }
        if (snapshot.getReturn60d() != null) {
            return snapshot.getReturn60d();
        }
        return snapshot.getReturn20d();
    }

    private void upsert(ScreenerFactorSnapshot snapshot) {
        ScreenerFactorSnapshot existing = screenerFactorSnapshotMapper.selectOne(new LambdaQueryWrapper<ScreenerFactorSnapshot>()
                .eq(ScreenerFactorSnapshot::getFundCode, snapshot.getFundCode())
                .eq(ScreenerFactorSnapshot::getFactorDate, snapshot.getFactorDate())
                .last("LIMIT 1"));
        if (existing == null) {
            screenerFactorSnapshotMapper.insert(snapshot);
        } else {
            snapshot.setId(existing.getId());
            snapshot.setCreateTime(existing.getCreateTime());
            screenerFactorSnapshotMapper.updateById(snapshot);
        }
    }

    private BigDecimal returnByWindow(List<ScreenerFundNavDaily> points, int days) {
        if (points.size() <= days) {
            return null;
        }
        return rate(points.getLast().getUnitNav(), points.get(points.size() - days - 1).getUnitNav());
    }

    private BigDecimal annualReturn(List<ScreenerFundNavDaily> points, int days) {
        BigDecimal periodReturn = returnByWindow(points, days);
        if (periodReturn == null) {
            return null;
        }
        return periodReturn.multiply(new BigDecimal("250.0000")).divide(new BigDecimal(days), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal maxDrawdown(List<ScreenerFundNavDaily> points, int days) {
        List<ScreenerFundNavDaily> window = tail(points, days + 1);
        BigDecimal peak = window.getFirst().getUnitNav();
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (ScreenerFundNavDaily point : window) {
            if (point.getUnitNav().compareTo(peak) > 0) {
                peak = point.getUnitNav();
            }
            BigDecimal drawdown = point.getUnitNav().subtract(peak).multiply(HUNDRED).divide(peak, 4, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) < 0) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal volatility(List<ScreenerFundNavDaily> points, int days) {
        List<BigDecimal> returns = dailyReturns(tail(points, days + 1));
        if (returns.size() < 2) {
            return null;
        }
        double avg = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = returns.stream().mapToDouble(value -> Math.pow(value.doubleValue() - avg, 2)).sum() / (returns.size() - 1);
        return BigDecimal.valueOf(Math.sqrt(variance) * Math.sqrt(250)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveDayRatio(List<ScreenerFundNavDaily> points, int days) {
        List<BigDecimal> returns = dailyReturns(tail(points, days + 1));
        if (returns.isEmpty()) {
            return null;
        }
        long positive = returns.stream().filter(value -> value.compareTo(BigDecimal.ZERO) > 0).count();
        return BigDecimal.valueOf(positive).multiply(HUNDRED).divide(BigDecimal.valueOf(returns.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal trendSlope(List<ScreenerFundNavDaily> points, int days) {
        List<ScreenerFundNavDaily> window = tail(points, days + 1);
        if (window.size() < 2) {
            return null;
        }
        return rate(window.getLast().getUnitNav(), window.getFirst().getUnitNav())
                .divide(BigDecimal.valueOf(window.size() - 1), 4, RoundingMode.HALF_UP);
    }

    private String benchmarkCode(ScreenerFundUniverse universe) {
        if (universe == null || universe.getFundType() == null) {
            return "000300";
        }
        if ("ACTIVE_EQUITY".equalsIgnoreCase(universe.getFundType())) {
            return "000985";
        }
        if ("INDEX".equalsIgnoreCase(universe.getFundType())
                && universe.getTrackingIndex() != null
                && !universe.getTrackingIndex().isBlank()) {
            return universe.getTrackingIndex().trim();
        }
        return "000300";
    }

    private BigDecimal returnDrawdownRatio(BigDecimal returnValue, BigDecimal maxDrawdown) {
        if (returnValue == null || maxDrawdown == null || maxDrawdown.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return returnValue.divide(maxDrawdown.abs(), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal returnConsistencyScore(ScreenerFactorSnapshot snapshot) {
        BigDecimal[] values = {
                snapshot.getReturn20d(),
                snapshot.getReturn60d(),
                snapshot.getReturn120d(),
                snapshot.getReturn250d()
        };
        int available = 0;
        int positive = 0;
        for (BigDecimal value : values) {
            if (value != null) {
                available++;
                if (value.compareTo(BigDecimal.ZERO) > 0) {
                    positive++;
                }
            }
        }
        if (available == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(positive)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(available), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal fundAgeYears(ScreenerFundUniverse universe, java.time.LocalDate factorDate) {
        if (universe == null || universe.getEstablishDate() == null || factorDate == null
                || universe.getEstablishDate().isAfter(factorDate)) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(universe.getEstablishDate(), factorDate))
                .divide(new BigDecimal("365.2500"), 4, RoundingMode.HALF_UP);
    }

    private List<ScreenerFundNavDaily> tail(List<ScreenerFundNavDaily> points, int size) {
        if (points.size() <= size) {
            return points;
        }
        return points.subList(points.size() - size, points.size());
    }

    private List<BigDecimal> dailyReturns(List<ScreenerFundNavDaily> points) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            returns.add(rate(points.get(index).getUnitNav(), points.get(index - 1).getUnitNav()));
        }
        return returns;
    }

    private BigDecimal rate(BigDecimal latest, BigDecimal base) {
        if (latest == null || base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return latest.subtract(base).multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private record FactorCandidate(String universeType, ScreenerFactorSnapshot snapshot) {
    }
}
