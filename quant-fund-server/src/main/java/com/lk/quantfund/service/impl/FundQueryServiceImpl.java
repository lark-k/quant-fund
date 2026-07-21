package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.constants.RedisKeyConstants;
import com.lk.quantfund.datasource.FundDataSourceAdapter;
import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundPeerRankDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
import com.lk.quantfund.entity.FundEstimateIntraday;
import com.lk.quantfund.entity.FundInfo;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundEstimateIntradayMapper;
import com.lk.quantfund.mapper.FundInfoMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FundQueryServiceImpl implements FundQueryService {

    private static final Logger log = LoggerFactory.getLogger(FundQueryServiceImpl.class);

    private final List<FundDataSourceAdapter> adapters;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final QuantFundProperties properties;
    private final FundInfoMapper fundInfoMapper;
    private final FundNavDailyMapper fundNavDailyMapper;
    private final FundEstimateIntradayMapper fundEstimateIntradayMapper;
    private final TradingCalendarService tradingCalendarService;
    private final MarketDataService marketDataService;

    public FundQueryServiceImpl(List<FundDataSourceAdapter> adapters,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                QuantFundProperties properties,
                                FundInfoMapper fundInfoMapper,
                                FundNavDailyMapper fundNavDailyMapper,
                                FundEstimateIntradayMapper fundEstimateIntradayMapper,
                                TradingCalendarService tradingCalendarService,
                                MarketDataService marketDataService) {
        this.adapters = adapters.stream()
                .sorted(Comparator.comparingInt(FundDataSourceAdapter::priority))
                .toList();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.fundInfoMapper = fundInfoMapper;
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.fundEstimateIntradayMapper = fundEstimateIntradayMapper;
        this.tradingCalendarService = tradingCalendarService;
        this.marketDataService = marketDataService;
    }

    @Override
    public List<FundSearchResultDTO> search(String keyword) {
        return search(keyword, "FUZZY");
    }

    @Override
    public List<FundSearchResultDTO> search(String keyword, String mode) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "基金搜索关键词不能为空");
        }
        String normalizedKeyword = keyword.trim();
        String normalizedMode = normalizeSearchMode(mode);
        String cacheKey = RedisKeyConstants.fundSearchCacheKey(normalizedKeyword, normalizedMode);
        return readCache(cacheKey, new TypeReference<List<FundSearchResultDTO>>() {})
                .orElseGet(() -> {
                    List<FundSearchResultDTO> result = queryAdapters(adapter -> adapter.searchFunds(normalizedKeyword));
                    if ("EXACT".equals(normalizedMode)) {
                        result = result.stream()
                                .filter(item -> exactSearchHit(item, normalizedKeyword))
                                .toList();
                    }
                    writeCache(cacheKey, result, Duration.ofMinutes(10));
                    return result;
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundBasicInfoDTO getBasicInfo(String fundCode) {
        String cacheKey = RedisKeyConstants.fundBasicCacheKey(fundCode);
        FundBasicInfoDTO info = readCache(cacheKey, new TypeReference<FundBasicInfoDTO>() {})
                .orElseGet(() -> {
                    FundBasicInfoDTO result = queryAdaptersOptional(adapter -> adapter.getBasicInfo(fundCode))
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "基金基础信息不存在"));
                    writeCache(cacheKey, result, Duration.ofHours(6));
                    return result;
                });
        if (!StringUtils.hasText(info.managerName()) || !StringUtils.hasText(info.themeTags())) {
            info = queryAdaptersOptional(adapter -> adapter.getBasicInfo(fundCode)).orElse(info);
            writeCache(cacheKey, info, Duration.ofHours(6));
        }
        saveFundInfo(info);
        return info;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FundNavPointDTO> getHistoricalNav(String fundCode, LocalDate startDate, LocalDate endDate, String indexCode) {
        String cacheKey = RedisKeyConstants.fundNavCacheKey(fundCode, String.valueOf(startDate), String.valueOf(endDate));
        boolean includesToday = endDate == null || !endDate.isBefore(LocalDate.now());
        List<FundNavPointDTO> points;
        try {
            points = includesToday
                    ? queryAdapters(adapter -> adapter.getHistoricalNav(fundCode, startDate, endDate))
                    : readCache(cacheKey, new TypeReference<List<FundNavPointDTO>>() {})
                            .orElseGet(() -> {
                                List<FundNavPointDTO> result = queryAdapters(adapter -> adapter.getHistoricalNav(fundCode, startDate, endDate));
                                writeCache(cacheKey, result, Duration.ofHours(12));
                                return result;
                            });
        } catch (RuntimeException exception) {
            log.warn("Historical NAV datasource failed for {}, fallback to local cache: {}", fundCode, exception.getMessage());
            points = cachedNavPoints(fundCode, startDate, endDate);
        }
        points.forEach(this::saveNavPoint);
        return attachIndexReturnRates(points, startDate, endDate, indexCode);
    }

    @Override
    public FundEstimateDTO getIntradayEstimate(String fundCode, boolean manualRefresh) {
        if (!tradingCalendarService.isIntradayEstimateDisplayWindow(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前不在盘中估值时间，暂不刷新盘中估值");
        }
        String cacheKey = RedisKeyConstants.estimateCacheKey(fundCode);
        if (!manualRefresh) {
            Optional<FundEstimateDTO> cached = readCache(cacheKey, new TypeReference<FundEstimateDTO>() {});
            if (cached.isPresent()) {
                return cached.get();
            }
        } else {
            ensureManualRefreshAllowed(fundCode);
        }

        try {
            Optional<FundEstimateDTO> providerEstimate;
            try {
                providerEstimate = queryAdaptersOptional(adapter -> adapter.getIntradayEstimate(fundCode));
            } catch (RuntimeException providerFailure) {
                log.warn("Fund estimate provider failed for {}, trying holdings-based estimate: {}",
                        fundCode, providerFailure.getMessage());
                providerEstimate = Optional.empty();
            }
            FundEstimateDTO estimate = providerEstimate
                    .or(() -> holdingsBasedEstimate(fundCode))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "基金当天估值不存在"));
            writeCache(cacheKey, estimate, Duration.ofMinutes(5));
            saveEstimate(estimate);
            return estimate;
        } catch (RuntimeException exception) {
            Optional<FundEstimateDTO> cached = readCache(cacheKey, new TypeReference<FundEstimateDTO>() {});
            if (cached.isPresent()) {
                FundEstimateDTO delayed = markDelayed(cached.get());
                saveEstimate(delayed);
                return delayed;
            }
            throw exception;
        }
    }

    private Optional<FundEstimateDTO> holdingsBasedEstimate(String fundCode) {
        if (fundNavDailyMapper == null) {
            return Optional.empty();
        }
        List<FundThemeDTO> themes;
        try {
            themes = queryAdapters(adapter -> adapter.getRelatedThemes(fundCode));
        } catch (RuntimeException exception) {
            log.warn("Holdings-based estimate failed for {}: {}", fundCode, exception.getMessage());
            return Optional.empty();
        }
        List<FundThemeDTO> usableThemes = themes.stream()
                .filter(theme -> theme.weight() != null && theme.weight().compareTo(BigDecimal.ZERO) > 0)
                .filter(theme -> theme.estimatedRate() != null)
                .toList();
        BigDecimal totalWeight = usableThemes.stream()
                .map(FundThemeDTO::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal estimateRate = usableThemes.stream()
                .map(theme -> theme.weight().multiply(theme.estimatedRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(totalWeight, 4, RoundingMode.HALF_UP);
        FundNavDaily latestNav = fundNavDailyMapper.selectOne(new LambdaQueryWrapper<FundNavDaily>()
                .eq(FundNavDaily::getFundCode, fundCode)
                .orderByDesc(FundNavDaily::getNavDate)
                .last("LIMIT 1"));
        if (latestNav == null || latestNav.getUnitNav() == null
                || latestNav.getUnitNav().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal estimateNav = latestNav.getUnitNav()
                .multiply(BigDecimal.ONE.add(estimateRate.divide(new BigDecimal("100.0000"), 8, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP);
        FundInfo info = fundInfoMapper == null ? null : fundInfoMapper.selectOne(new LambdaQueryWrapper<FundInfo>()
                .eq(FundInfo::getFundCode, fundCode)
                .last("LIMIT 1"));
        LocalDateTime estimateTime = LocalDateTime.now();
        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(Map.of(
                    "type", "HEAVY_STOCK_WEIGHTED",
                    "coverage", totalWeight,
                    "themes", usableThemes));
        } catch (JsonProcessingException exception) {
            rawPayload = "{}";
        }
        return Optional.of(new FundEstimateDTO(
                fundCode,
                info == null || !StringUtils.hasText(info.getFundName()) ? fundCode : info.getFundName(),
                estimateNav,
                estimateRate,
                estimateTime.toLocalDate(),
                estimateTime,
                "HEAVY_STOCK_WEIGHTED",
                false,
                rawPayload
        ));
    }

    @Override
    public List<FundStockHoldingDTO> getHeavyStocks(String fundCode) {
        return queryAdapters(adapter -> adapter.getHeavyStocks(fundCode));
    }

    @Override
    public List<FundThemeDTO> getRelatedThemes(String fundCode) {
        return queryAdapters(adapter -> adapter.getRelatedThemes(fundCode));
    }

    @Override
    public FundPeerRankDTO getPeerRank(String fundCode) {
        return queryAdaptersOptional(adapter -> adapter.getPeerRank(fundCode)).orElse(null);
    }

    private String normalizeSearchMode(String mode) {
        return "EXACT".equalsIgnoreCase(mode) ? "EXACT" : "FUZZY";
    }

    private boolean exactSearchHit(FundSearchResultDTO item, String keyword) {
        String target = keyword.trim();
        return equalsIgnoreCase(item.fundCode(), target)
                || equalsIgnoreCase(item.fundName(), target)
                || equalsIgnoreCase(item.pinyin(), target);
    }

    private boolean equalsIgnoreCase(String value, String target) {
        return value != null && value.equalsIgnoreCase(target);
    }

    private List<FundNavPointDTO> cachedNavPoints(String fundCode, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<FundNavDaily> wrapper = new LambdaQueryWrapper<FundNavDaily>()
                .eq(FundNavDaily::getFundCode, fundCode);
        if (startDate != null) {
            wrapper.ge(FundNavDaily::getNavDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(FundNavDaily::getNavDate, endDate);
        }
        wrapper.orderByAsc(FundNavDaily::getNavDate);
        return fundNavDailyMapper.selectList(wrapper).stream()
                .map(point -> new FundNavPointDTO(
                        point.getFundCode(),
                        point.getNavDate(),
                        point.getUnitNav(),
                        point.getAccumulatedNav(),
                        point.getDailyGrowthRate(),
                        point.getSourceName()
                ))
                .toList();
    }

    private <T> T queryAdapters(Function<FundDataSourceAdapter, T> query) {
        RuntimeException lastFailure = null;
        for (FundDataSourceAdapter adapter : adapters) {
            if (!adapter.enabled()) {
                continue;
            }
            try {
                T result = query.apply(adapter);
                if (result instanceof List<?> list && list.isEmpty()) {
                    continue;
                }
                if (result != null) {
                    return result;
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("Fund datasource {} failed: {}", adapter.sourceName(), exception.getMessage());
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "没有可用的基金数据源");
    }

    private <T> Optional<T> queryAdaptersOptional(Function<FundDataSourceAdapter, Optional<T>> query) {
        RuntimeException lastFailure = null;
        for (FundDataSourceAdapter adapter : adapters) {
            if (!adapter.enabled()) {
                continue;
            }
            try {
                Optional<T> result = query.apply(adapter);
                if (result.isPresent()) {
                    return result;
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("Fund datasource {} failed: {}", adapter.sourceName(), exception.getMessage());
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return Optional.empty();
    }

    private <T> Optional<T> readCache(String key, TypeReference<T> typeReference) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return StringUtils.hasText(value) ? Optional.of(objectMapper.readValue(value, typeReference)) : Optional.empty();
        } catch (Exception exception) {
            log.warn("Read Redis cache failed for {}: {}", key, exception.getMessage());
            return Optional.empty();
        }
    }

    private void writeCache(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException exception) {
            log.warn("Serialize cache value failed for {}: {}", key, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Write Redis cache failed for {}: {}", key, exception.getMessage());
        }
    }

    private void ensureManualRefreshAllowed(String fundCode) {
        try {
            String key = RedisKeyConstants.manualEstimateRefreshKey(fundCode);
            Boolean allowed = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    "1",
                    Duration.ofSeconds(properties.getFundDataSource().getManualRefreshCooldownSeconds())
            );
            if (!Boolean.TRUE.equals(allowed)) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "同一基金 10 秒内不能重复手动刷新");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Manual refresh cooldown check skipped because Redis failed: {}", exception.getMessage());
        }
    }

    private FundEstimateDTO markDelayed(FundEstimateDTO estimate) {
        return new FundEstimateDTO(
                estimate.fundCode(),
                estimate.fundName(),
                estimate.estimateNav(),
                estimate.estimateGrowthRate(),
                estimate.estimateDate(),
                estimate.estimateTime(),
                estimate.sourceName(),
                true,
                estimate.rawPayload()
        );
    }

    private List<FundNavPointDTO> attachIndexReturnRates(List<FundNavPointDTO> points, LocalDate startDate, LocalDate endDate, String requestedIndexCode) {
        if (points.isEmpty()) {
            return points;
        }
        IndexMatch index = StringUtils.hasText(requestedIndexCode) ? indexMatchByCode(requestedIndexCode) : indexMatch(points.getFirst().fundCode());
        LocalDate actualStart = startDate != null ? startDate : points.stream()
                .map(FundNavPointDTO::navDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now().minusDays(365));
        LocalDate actualEnd = endDate != null ? endDate : points.stream()
                .map(FundNavPointDTO::navDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        List<MarketIndexDailyVO> history;
        try {
            history = marketDataService.historicalIndex(index.code(), actualStart, actualEnd);
        } catch (Exception exception) {
            return points;
        }
        if (history.size() < 2 || history.getFirst().closePrice() == null || history.getFirst().closePrice().compareTo(BigDecimal.ZERO) <= 0) {
            return points;
        }
        BigDecimal baseClose = history.getFirst().closePrice();
        NavigableMap<LocalDate, BigDecimal> indexCloseByDate = new TreeMap<>();
        for (MarketIndexDailyVO point : history) {
            if (point.tradeDate() != null && point.closePrice() != null) {
                indexCloseByDate.put(point.tradeDate(), point.closePrice());
            }
        }
        return points.stream()
                .map(point -> new FundNavPointDTO(
                        point.fundCode(),
                        point.navDate(),
                        point.unitNav(),
                        point.accumulatedNav(),
                        point.dailyGrowthRate(),
                        point.sourceName(),
                        indexReturnRate(indexCloseByDate, point.navDate(), baseClose),
                        index.code(),
                        index.name()
                ))
                .toList();
    }

    private IndexMatch indexMatchByCode(String indexCode) {
        return switch (indexCode.trim()) {
            case "399006" -> new IndexMatch("399006", "创业板指");
            case "000001" -> new IndexMatch("000001", "上证指数");
            case "399001" -> new IndexMatch("399001", "深证成指");
            default -> new IndexMatch("000300", "沪深300");
        };
    }

    private BigDecimal indexReturnRate(NavigableMap<LocalDate, BigDecimal> indexCloseByDate,
                                       LocalDate navDate,
                                       BigDecimal baseClose) {
        if (navDate == null || baseClose == null || baseClose.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        Map.Entry<LocalDate, BigDecimal> matched = indexCloseByDate.floorEntry(navDate);
        if (matched == null || matched.getValue() == null) {
            return null;
        }
        return matched.getValue()
                .subtract(baseClose)
                .multiply(new BigDecimal("100.0000"))
                .divide(baseClose, 4, RoundingMode.HALF_UP);
    }

    private IndexMatch indexMatch(String fundCode) {
        FundInfo info = fundInfoMapper == null || fundCode == null ? null : fundInfoMapper.selectOne(new LambdaQueryWrapper<FundInfo>()
                .eq(FundInfo::getFundCode, fundCode)
                .last("LIMIT 1"));
        String text = "";
        if (info != null) {
            text = safe(info.getTrackingIndex()) + safe(info.getFundName()) + safe(info.getFundType());
        }
        text += safe(fundCode);
        if (text.contains("纳斯达克") || text.contains("纳指") || text.contains("NASDAQ") || text.contains("NDX")) {
            return new IndexMatch("NDX", "纳斯达克");
        }
        if (text.contains("标普500") || text.contains("标普") || text.contains("S&P") || text.contains("SPX")) {
            return new IndexMatch("SPX", "标普500");
        }
        if (text.contains("恒生") || text.contains("港股") || text.contains("香港") || text.contains("H股")) {
            return new IndexMatch("HSI", "恒生指数");
        }
        if (text.contains("中证白酒") || text.contains("白酒") || "161725".equals(fundCode)) {
            return new IndexMatch("399997", "中证白酒");
        }
        if (text.contains("电网设备") || text.contains("特高压") || "025833".equals(fundCode)) {
            return new IndexMatch("931994", "中证电网设备");
        }
        if (text.contains("中证500") || text.contains("500")) {
            return new IndexMatch("000905", "中证500");
        }
        if (text.contains("创业板") || text.contains("创业")) {
            return new IndexMatch("399006", "创业板指");
        }
        if (text.contains("上证") && !text.contains("沪深300")) {
            return new IndexMatch("000001", "上证指数");
        }
        return new IndexMatch("000300", "沪深300");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void saveFundInfo(FundBasicInfoDTO info) {
        FundInfo entity = fundInfoMapper.selectOne(new LambdaQueryWrapper<FundInfo>()
                .eq(FundInfo::getFundCode, info.fundCode())
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            entity = new FundInfo();
            entity.setFundCode(info.fundCode());
            entity.setCreateTime(now);
            entity.setDeleted(0);
        }
        entity.setFundName(info.fundName());
        entity.setFundType(info.fundType());
        entity.setActiveFund(info.activeFund() ? 1 : 0);
        entity.setTrackingIndex(info.trackingIndex());
        entity.setManagerName(info.managerName());
        entity.setCompanyName(info.companyName());
        entity.setRiskLevel(info.riskLevel());
        entity.setThemeTags(info.themeTags());
        entity.setSourceName(info.sourceName());
        entity.setSourceUpdateTime(now);
        entity.setUpdateTime(now);
        if (entity.getId() == null) {
            fundInfoMapper.insert(entity);
        } else {
            fundInfoMapper.updateById(entity);
        }
    }

    private void saveNavPoint(FundNavPointDTO point) {
        FundNavDaily entity = fundNavDailyMapper.selectOne(new LambdaQueryWrapper<FundNavDaily>()
                .eq(FundNavDaily::getFundCode, point.fundCode())
                .eq(FundNavDaily::getNavDate, point.navDate())
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            entity = new FundNavDaily();
            entity.setFundCode(point.fundCode());
            entity.setNavDate(point.navDate());
            entity.setCreateTime(now);
            entity.setDeleted(0);
        }
        entity.setUnitNav(point.unitNav());
        entity.setAccumulatedNav(point.accumulatedNav());
        entity.setDailyGrowthRate(point.dailyGrowthRate());
        entity.setSourceName(point.sourceName());
        entity.setUpdateTime(now);
        if (entity.getId() == null) {
            fundNavDailyMapper.insert(entity);
        } else {
            fundNavDailyMapper.updateById(entity);
        }
    }

    private void saveEstimate(FundEstimateDTO estimate) {
        FundEstimateIntraday entity = new FundEstimateIntraday();
        LocalDateTime now = LocalDateTime.now();
        entity.setFundCode(estimate.fundCode());
        entity.setEstimateDate(estimate.estimateDate());
        entity.setEstimateNav(estimate.estimateNav());
        entity.setEstimateGrowthRate(estimate.estimateGrowthRate());
        entity.setEstimateTime(estimate.estimateTime());
        entity.setSourceName(estimate.sourceName());
        entity.setDelayed(estimate.delayed() ? 1 : 0);
        entity.setRawPayload(estimate.rawPayload());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
        fundEstimateIntradayMapper.insert(entity);
    }

    private record IndexMatch(String code, String name) {
    }
}
