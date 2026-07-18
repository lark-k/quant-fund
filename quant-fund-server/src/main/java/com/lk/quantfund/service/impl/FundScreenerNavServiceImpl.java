package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.datasource.FundDataSourceAdapter;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerFundUniverse;
import com.lk.quantfund.entity.ScreenerUniverseFilter;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerFundUniverseMapper;
import com.lk.quantfund.mapper.ScreenerUniverseFilterMapper;
import com.lk.quantfund.service.FundScreenerNavService;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class FundScreenerNavServiceImpl implements FundScreenerNavService {

    private static final int NAV_SYNC_PARALLELISM = 6;

    private final List<FundDataSourceAdapter> adapters;
    private final ScreenerUniverseFilterMapper screenerUniverseFilterMapper;
    private final ScreenerFundUniverseMapper screenerFundUniverseMapper;
    private final ScreenerFundNavDailyMapper screenerFundNavDailyMapper;
    private final FundScreenerFreshnessPolicy freshnessPolicy;

    public FundScreenerNavServiceImpl(List<FundDataSourceAdapter> adapters,
                                      ScreenerUniverseFilterMapper screenerUniverseFilterMapper,
                                      ScreenerFundUniverseMapper screenerFundUniverseMapper,
                                      ScreenerFundNavDailyMapper screenerFundNavDailyMapper,
                                      FundScreenerFreshnessPolicy freshnessPolicy) {
        this.adapters = adapters.stream()
                .sorted(Comparator.comparingInt(FundDataSourceAdapter::priority))
                .toList();
        this.screenerUniverseFilterMapper = screenerUniverseFilterMapper;
        this.screenerFundUniverseMapper = screenerFundUniverseMapper;
        this.screenerFundNavDailyMapper = screenerFundNavDailyMapper;
        this.freshnessPolicy = freshnessPolicy;
    }

    @Override
    public FundScreenerTaskResultVO syncNav() {
        long started = System.currentTimeMillis();
        AtomicInteger savedCounter = new AtomicInteger();
        AtomicInteger failedCounter = new AtomicInteger();
        AtomicInteger skippedCounter = new AtomicInteger();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        List<String> fundCodes = navSyncFundCodes();
        if (fundCodes.isEmpty()) {
            skippedCounter.incrementAndGet();
        } else {
            syncNavInParallel(fundCodes, savedCounter, failedCounter, skippedCounter, errors);
        }
        int saved = savedCounter.get();
        int failed = failedCounter.get();
        int skipped = skippedCounter.get();
        String status = failed > 0
                ? saved > 0 ? "PARTIAL_SUCCESS" : "FAILED"
                : saved > 0 ? "SUCCESS" : "SKIPPED";
        return new FundScreenerTaskResultVO(
                "SYNC_NAV",
                status,
                saved,
                failed,
                skipped,
                System.currentTimeMillis() - started,
                errors,
                "基金优选净值同步：新增" + saved + "条，失败" + failed + "只，已是最新" + skipped
                        + "只，最低新鲜日期" + freshnessPolicy.requiredNavDate(),
                LocalDateTime.now()
        );
    }

    private void syncNavInParallel(List<String> fundCodes,
                                   AtomicInteger saved,
                                   AtomicInteger failed,
                                   AtomicInteger skipped,
                                   List<String> errors) {
        ExecutorService executor = Executors.newFixedThreadPool(NAV_SYNC_PARALLELISM);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (String fundCode : fundCodes) {
                futures.add(executor.submit(() -> syncOneFund(fundCode, saved, failed, skipped, errors)));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failed.incrementAndGet();
                    addError(errors, "sync interrupted");
                    break;
                } catch (ExecutionException exception) {
                    failed.incrementAndGet();
                    addError(errors, "sync worker failed: " + exception.getCause().getMessage());
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void syncOneFund(String fundCode,
                             AtomicInteger saved,
                             AtomicInteger failed,
                             AtomicInteger skipped,
                             List<String> errors) {
        try {
            LocalDate latestStoredDate = latestStoredNavDate(fundCode);
            LocalDate startDate = latestStoredDate == null ? LocalDate.now().minusDays(420) : latestStoredDate.plusDays(1);
            List<FundNavPointDTO> points = fetchNav(fundCode, startDate, LocalDate.now());
            if (points.isEmpty()) {
                if (freshnessPolicy.isFresh(latestStoredDate)) {
                    skipped.incrementAndGet();
                } else {
                    failed.incrementAndGet();
                    addError(errors, staleMessage(fundCode, latestStoredDate));
                }
                return;
            }
            upsert(points);
            saved.addAndGet(points.size());
            LocalDate latestSyncedDate = points.stream()
                    .map(FundNavPointDTO::navDate)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(latestStoredDate);
            if (!freshnessPolicy.isFresh(latestSyncedDate)) {
                failed.incrementAndGet();
                addError(errors, staleMessage(fundCode, latestSyncedDate));
            }
        } catch (RuntimeException exception) {
            failed.incrementAndGet();
            addError(errors, fundCode + ": " + exception.getMessage());
        }
    }

    private List<String> navSyncFundCodes() {
        List<ScreenerUniverseFilter> includedFunds = screenerUniverseFilterMapper.selectList(new LambdaQueryWrapper<ScreenerUniverseFilter>()
                .eq(ScreenerUniverseFilter::getIncluded, 1)
                .orderByAsc(ScreenerUniverseFilter::getFundCode));
        if (!includedFunds.isEmpty()) {
            return includedFunds.stream().map(ScreenerUniverseFilter::getFundCode).toList();
        }
        return screenerFundUniverseMapper.selectList(new LambdaQueryWrapper<ScreenerFundUniverse>()
                        .in(ScreenerFundUniverse::getFundType, List.of("ACTIVE_EQUITY", "MIXED", "INDEX"))
                        .eq(ScreenerFundUniverse::getStatus, "NORMAL")
                        .orderByAsc(ScreenerFundUniverse::getFundCode))
                .stream()
                .filter(fund -> supportedType(fund.getFundType()))
                .filter(fund -> "NORMAL".equalsIgnoreCase(fund.getStatus()))
                .filter(fund -> shareClassAllowed(fund.getShareClass()))
                .map(ScreenerFundUniverse::getFundCode)
                .toList();
    }

    private boolean supportedType(String fundType) {
        return "ACTIVE_EQUITY".equals(fundType) || "MIXED".equals(fundType) || "INDEX".equals(fundType);
    }

    private boolean shareClassAllowed(String shareClass) {
        if (shareClass == null || shareClass.isBlank()) {
            return true;
        }
        String normalized = shareClass.trim().toUpperCase();
        return !"C".equals(normalized) && !"E".equals(normalized);
    }

    private List<FundNavPointDTO> fetchNav(String fundCode, LocalDate startDate, LocalDate endDate) {
        RuntimeException lastFailure = null;
        for (FundDataSourceAdapter adapter : adapters) {
            if (!adapter.enabled()) {
                continue;
            }
            try {
                List<FundNavPointDTO> points = adapter.getHistoricalNav(fundCode, startDate, endDate);
                if (!points.isEmpty()) {
                    return points;
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return List.of();
    }

    private LocalDate latestStoredNavDate(String fundCode) {
        List<ScreenerFundNavDaily> latest = screenerFundNavDailyMapper.selectList(new LambdaQueryWrapper<ScreenerFundNavDaily>()
                .eq(ScreenerFundNavDaily::getFundCode, fundCode)
                .orderByDesc(ScreenerFundNavDaily::getNavDate)
                .last("LIMIT 1"));
        return latest.isEmpty() ? null : latest.getFirst().getNavDate();
    }

    private String staleMessage(String fundCode, LocalDate latestDate) {
        return fundCode + ": 最新净值日期" + (latestDate == null ? "缺失" : latestDate)
                + "，早于要求日期" + freshnessPolicy.requiredNavDate();
    }

    private void addError(List<String> errors, String message) {
        synchronized (errors) {
            if (errors.size() < 20) {
                errors.add(message);
            }
        }
    }

    private void upsert(List<FundNavPointDTO> points) {
        if (points.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ScreenerFundNavDaily> entities = points.stream()
                .map(point -> {
                    ScreenerFundNavDaily entity = new ScreenerFundNavDaily();
                    entity.setFundCode(point.fundCode());
                    entity.setNavDate(point.navDate());
                    entity.setCreateTime(now);
                    entity.setDeleted(0);
                    entity.setUnitNav(point.unitNav());
                    entity.setAccumulatedNav(point.accumulatedNav());
                    entity.setDailyGrowthRate(point.dailyGrowthRate());
                    entity.setSourceName(point.sourceName());
                    entity.setUpdateTime(now);
                    return entity;
                })
                .toList();
        screenerFundNavDailyMapper.upsertBatch(entities);
    }
}
