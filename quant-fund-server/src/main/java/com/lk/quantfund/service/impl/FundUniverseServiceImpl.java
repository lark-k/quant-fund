package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.datasource.FundUniverseDataSourceAdapter;
import com.lk.quantfund.datasource.model.MarketFundDTO;
import com.lk.quantfund.entity.ScreenerFundUniverse;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerUniverseFilter;
import com.lk.quantfund.mapper.ScreenerFundUniverseMapper;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerUniverseFilterMapper;
import com.lk.quantfund.service.FundUniverseService;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FundUniverseServiceImpl implements FundUniverseService {

    private final List<FundUniverseDataSourceAdapter> adapters;
    private final ScreenerFundUniverseMapper screenerFundUniverseMapper;
    private final ScreenerUniverseFilterMapper screenerUniverseFilterMapper;
    private final ScreenerFundNavDailyMapper screenerFundNavDailyMapper;

    public FundUniverseServiceImpl(List<FundUniverseDataSourceAdapter> adapters,
                                   ScreenerFundUniverseMapper screenerFundUniverseMapper,
                                   ScreenerUniverseFilterMapper screenerUniverseFilterMapper,
                                   ScreenerFundNavDailyMapper screenerFundNavDailyMapper) {
        this.adapters = adapters.stream()
                .sorted(Comparator.comparingInt(FundUniverseDataSourceAdapter::priority))
                .toList();
        this.screenerFundUniverseMapper = screenerFundUniverseMapper;
        this.screenerUniverseFilterMapper = screenerUniverseFilterMapper;
        this.screenerFundNavDailyMapper = screenerFundNavDailyMapper;
    }

    @Override
    public FundScreenerTaskResultVO syncUniverse() {
        long started = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        for (FundUniverseDataSourceAdapter adapter : adapters) {
            if (!adapter.enabled()) {
                continue;
            }
            try {
                List<MarketFundDTO> funds = adapter.listAllFunds();
                if (funds.isEmpty()) {
                    errors.add(adapter.sourceName() + ": empty fund universe");
                    continue;
                }
                int saved = 0;
                for (MarketFundDTO fund : funds) {
                    upsert(fund, adapter.sourceName());
                    saved++;
                }
                return new FundScreenerTaskResultVO(
                        "SYNC_UNIVERSE",
                        "SUCCESS",
                        saved,
                        0,
                        0,
                        System.currentTimeMillis() - started,
                        errors,
                        "基金优选基础库同步完成",
                        LocalDateTime.now()
                );
            } catch (RuntimeException exception) {
                errors.add(adapter.sourceName() + ": " + exception.getMessage());
            }
        }
        return new FundScreenerTaskResultVO(
                "SYNC_UNIVERSE",
                "FAILED",
                0,
                errors.isEmpty() ? 0 : errors.size(),
                0,
                System.currentTimeMillis() - started,
                errors,
                "基金优选基础库同步失败，已保留历史数据",
                LocalDateTime.now()
        );
    }

    @Override
    public FundScreenerTaskResultVO rebuildUniverse() {
        long started = System.currentTimeMillis();
        List<ScreenerFundUniverse> funds = screenerFundUniverseMapper.selectList(new LambdaQueryWrapper<ScreenerFundUniverse>()
                .orderByAsc(ScreenerFundUniverse::getFundCode));
        int included = 0;
        int excluded = 0;
        for (ScreenerFundUniverse fund : funds) {
            ScreenerUniverseFilter filter = buildFilter(fund);
            if (filter.getIncluded() == 1) {
                included++;
            } else {
                excluded++;
            }
            upsertFilter(filter);
        }
        return new FundScreenerTaskResultVO(
                "REBUILD_UNIVERSE",
                "SUCCESS",
                included,
                0,
                excluded,
                System.currentTimeMillis() - started,
                List.of(),
                "基金优选评分池重建完成",
                LocalDateTime.now()
        );
    }

    private ScreenerUniverseFilter buildFilter(ScreenerFundUniverse fund) {
        String universeType = normalizeFundType(fund.getFundType(), fund.getFundName());
        long navDays = screenerFundNavDailyMapper.selectCount(new LambdaQueryWrapper<ScreenerFundNavDaily>()
                .eq(ScreenerFundNavDaily::getFundCode, fund.getFundCode()));
        boolean supported = supportedType(universeType);
        boolean normalStatus = "NORMAL".equalsIgnoreCase(safe(fund.getStatus()));
        boolean duplicatePassed = duplicateFilterPassed(fund.getShareClass());
        boolean minNavPassed = navDays >= 120;
        boolean sizePassed = fund.getFundSize() == null || fund.getFundSize().compareTo(new java.math.BigDecimal("0.5000")) >= 0;
        List<String> reasons = new ArrayList<>();
        if (!supported) {
            reasons.add("暂不支持的基金类型");
        }
        if (!normalStatus) {
            reasons.add("基金状态异常");
        }
        if (!duplicatePassed) {
            reasons.add("重复份额暂不纳入评分池");
        }
        if (!minNavPassed) {
            reasons.add("净值样本不足120天");
        }
        if (!sizePassed) {
            reasons.add("基金规模低于门槛");
        }
        LocalDateTime now = LocalDateTime.now();
        ScreenerUniverseFilter filter = new ScreenerUniverseFilter();
        filter.setFundCode(fund.getFundCode());
        filter.setUniverseType(universeType);
        filter.setIncluded(reasons.isEmpty() ? 1 : 0);
        filter.setExcludeReason(reasons.isEmpty() ? null : String.join("；", reasons));
        filter.setMinNavDaysPassed(minNavPassed ? 1 : 0);
        filter.setSizeFilterPassed(sizePassed ? 1 : 0);
        filter.setDuplicateFilterPassed(duplicatePassed ? 1 : 0);
        filter.setStatusFilterPassed(normalStatus ? 1 : 0);
        filter.setLastRebuildTime(now);
        filter.setCreateTime(now);
        filter.setUpdateTime(now);
        filter.setDeleted(0);
        return filter;
    }

    private void upsertFilter(ScreenerUniverseFilter filter) {
        ScreenerUniverseFilter existing = screenerUniverseFilterMapper.selectOne(new LambdaQueryWrapper<ScreenerUniverseFilter>()
                .eq(ScreenerUniverseFilter::getFundCode, filter.getFundCode())
                .last("LIMIT 1"));
        if (existing != null) {
            filter.setId(existing.getId());
            filter.setCreateTime(existing.getCreateTime());
            screenerUniverseFilterMapper.updateById(filter);
        } else {
            screenerUniverseFilterMapper.insert(filter);
        }
    }

    private boolean supportedType(String universeType) {
        return "ACTIVE_EQUITY".equals(universeType) || "MIXED".equals(universeType) || "INDEX".equals(universeType);
    }

    private boolean duplicateFilterPassed(String shareClass) {
        if (!StringUtils.hasText(shareClass)) {
            return true;
        }
        String normalized = shareClass.trim().toUpperCase();
        return !"C".equals(normalized) && !"E".equals(normalized);
    }

    private void upsert(MarketFundDTO fund, String adapterSourceName) {
        if (!StringUtils.hasText(fund.fundCode()) || !StringUtils.hasText(fund.fundName())) {
            return;
        }
        ScreenerFundUniverse entity = screenerFundUniverseMapper.selectOne(new LambdaQueryWrapper<ScreenerFundUniverse>()
                .eq(ScreenerFundUniverse::getFundCode, fund.fundCode())
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            entity = new ScreenerFundUniverse();
            entity.setFundCode(fund.fundCode());
            entity.setCreateTime(now);
            entity.setDeleted(0);
        }
        entity.setFundName(fund.fundName());
        entity.setFundType(normalizeFundType(fund.fundType(), fund.fundName()));
        entity.setShareClass(shareClass(fund.shareClass(), fund.fundName()));
        entity.setMainFundCode(fund.mainFundCode());
        entity.setCompanyName(fund.companyName());
        entity.setManagerName(fund.managerName());
        entity.setEstablishDate(fund.establishDate());
        entity.setFundSize(fund.fundSize());
        entity.setTrackingIndex(fund.trackingIndex());
        entity.setActiveFund(fund.activeFund() ? 1 : 0);
        entity.setRiskLevel(fund.riskLevel());
        entity.setStatus(StringUtils.hasText(fund.status()) ? fund.status() : "NORMAL");
        entity.setSourceName(StringUtils.hasText(fund.sourceName()) ? fund.sourceName() : adapterSourceName);
        entity.setLastSyncTime(now);
        entity.setUpdateTime(now);
        if (entity.getId() == null) {
            screenerFundUniverseMapper.insert(entity);
        } else {
            screenerFundUniverseMapper.updateById(entity);
        }
    }

    private String normalizeFundType(String fundType, String fundName) {
        String text = (safe(fundType) + safe(fundName)).toUpperCase();
        if (text.contains("QDII")) {
            return "QDII";
        }
        if (text.contains("货币")) {
            return "MONEY";
        }
        if (text.contains("债")) {
            return "BOND";
        }
        if (text.contains("ETF") || text.contains("指数") || text.contains("INDEX")) {
            return "INDEX";
        }
        if (text.contains("混合") || text.contains("MIX")) {
            return "MIXED";
        }
        if (text.contains("股票") || text.contains("权益") || text.contains("EQUITY")) {
            return "ACTIVE_EQUITY";
        }
        return "UNKNOWN";
    }

    private String shareClass(String explicitShareClass, String fundName) {
        if (StringUtils.hasText(explicitShareClass)) {
            return explicitShareClass.trim().toUpperCase();
        }
        String name = safe(fundName).trim().toUpperCase();
        if (name.endsWith("C")) {
            return "C";
        }
        if (name.endsWith("E")) {
            return "E";
        }
        if (name.endsWith("A")) {
            return "A";
        }
        if (name.contains("ETF")) {
            return "ETF";
        }
        if (name.contains("LOF")) {
            return "LOF";
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
