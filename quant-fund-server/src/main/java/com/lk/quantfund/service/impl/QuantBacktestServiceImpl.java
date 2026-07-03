package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.BatchResponse;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.EngineBatchRequest;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.EngineFund;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.NavRefreshItem;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.NavRefreshResponse;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.Options;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.Result;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.RunRequest;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.StrategyParams;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.TrainingLabelConfig;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.TrainingSampleExportRequest;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.TrainingSampleExportResponse;
import com.lk.quantfund.dto.quant.QuantNavPointDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.QuantBacktestResult;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.QuantBacktestResultMapper;
import com.lk.quantfund.quant.QuantEngineClient;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.service.QuantBacktestService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class QuantBacktestServiceImpl implements QuantBacktestService {

    private static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("10000.0000");
    private static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.0015");
    private static final TrainingLabelConfig DEFAULT_TRAINING_LABEL_CONFIG = new TrainingLabelConfig(
            20,
            new BigDecimal("2.0000"),
            new BigDecimal("-8.0000")
    );
    private static final Map<String, String> COMMON_MARKET_INDICES = new LinkedHashMap<>();

    static {
        COMMON_MARKET_INDICES.put("marketSh000001ReturnRate", "000001");
        COMMON_MARKET_INDICES.put("marketSz399001ReturnRate", "399001");
        COMMON_MARKET_INDICES.put("marketCyb399006ReturnRate", "399006");
        COMMON_MARKET_INDICES.put("marketHs300ReturnRate", "000300");
        COMMON_MARKET_INDICES.put("marketZz500ReturnRate", "000905");
    }

    private final QuantEngineClient quantEngineClient;
    private final QuantFundProperties properties;
    private final ObjectMapper objectMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final FundNavDailyMapper fundNavDailyMapper;
    private final QuantBacktestResultMapper quantBacktestResultMapper;
    private final FundQueryService fundQueryService;
    private final MarketDataService marketDataService;

    public QuantBacktestServiceImpl(QuantEngineClient quantEngineClient,
                                    QuantFundProperties properties,
                                    ObjectMapper objectMapper,
                                    PortfolioAccountMapper portfolioAccountMapper,
                                    FundHoldingMapper fundHoldingMapper,
                                    FundNavDailyMapper fundNavDailyMapper,
                                    QuantBacktestResultMapper quantBacktestResultMapper,
                                    FundQueryService fundQueryService,
                                    MarketDataService marketDataService) {
        this.quantEngineClient = quantEngineClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.quantBacktestResultMapper = quantBacktestResultMapper;
        this.fundQueryService = fundQueryService;
        this.marketDataService = marketDataService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchResponse run(RunRequest request) {
        if (!properties.getQuantEngine().isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "quant engine is disabled");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "startDate must be before endDate");
        }
        Long userId = UserContext.getUserId();
        List<FundHolding> holdings = loadHoldings(userId, request);
        if (holdings.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "no owned holdings available for backtest");
        }
        List<String> fundCodes = resolveFundCodes(request, holdings);
        if (fundCodes.size() > properties.getQuantEngine().getMaxBacktestFunds()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fund count exceeds max backtest funds");
        }

        Map<String, FundHolding> metadata = holdings.stream()
                .filter(item -> fundCodes.contains(item.getFundCode()))
                .collect(Collectors.toMap(FundHolding::getFundCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        StrategyParams strategyParams = defaultParams(request.strategyParams());
        Map<String, List<QuantNavPointDTO>> navByFund = loadNavSeries(fundCodes, request, strategyParams, metadata);
        List<EngineFund> funds = fundCodes.stream()
                .map(code -> toEngineFund(code, metadata.get(code), navByFund.getOrDefault(code, List.of())))
                .toList();
        EngineBatchRequest engineRequest = new EngineBatchRequest(
                "backtest-" + LocalDateTime.now(),
                "QuantRuleEngine",
                request.startDate(),
                request.endDate(),
                defaultDecimal(request.initialCash(), DEFAULT_INITIAL_CASH),
                defaultDecimal(request.feeRate(), DEFAULT_FEE_RATE),
                funds,
                strategyParams,
                defaultOptions(request.options())
        );
        BatchResponse response = quantEngineClient.runBacktestBatch(engineRequest);
        saveResults(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NavRefreshResponse refreshNavCache(RunRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "startDate must be before endDate");
        }
        Long userId = UserContext.getUserId();
        List<FundHolding> holdings = loadHoldings(userId, request);
        if (holdings.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "no owned holdings available for backtest nav refresh");
        }
        List<String> fundCodes = resolveFundCodes(request, holdings);
        if (fundCodes.size() > properties.getQuantEngine().getMaxBacktestFunds()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fund count exceeds max backtest funds");
        }

        StrategyParams strategyParams = defaultParams(request.strategyParams());
        LocalDate requestedStartDate = request.startDate().minusDays(Math.max(Math.max(strategyParams.warmupDays(), 0), 180));
        LocalDate requestedEndDate = request.endDate();
        Map<String, FundHolding> metadata = holdings.stream()
                .filter(item -> fundCodes.contains(item.getFundCode()))
                .collect(Collectors.toMap(FundHolding::getFundCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<NavRefreshItem> results = fundCodes.stream()
                .map(code -> refreshOneFundNav(code, metadata.get(code), requestedStartDate, requestedEndDate))
                .toList();
        int successCount = (int) results.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        return new NavRefreshResponse(
                fundCodes.size(),
                successCount,
                fundCodes.size() - successCount,
                requestedStartDate,
                requestedEndDate,
                results
        );
    }

    @Override
    public TrainingSampleExportResponse exportTrainingSamples(RunRequest request) {
        if (!properties.getQuantEngine().isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "quant engine is disabled");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "startDate must be before endDate");
        }
        Long userId = UserContext.getUserId();
        List<FundHolding> holdings = loadHoldings(userId, request);
        if (holdings.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "no owned holdings available for ML sample export");
        }
        List<String> fundCodes = resolveFundCodes(request, holdings);
        if (fundCodes.size() > properties.getQuantEngine().getMaxBacktestFunds()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fund count exceeds max backtest funds");
        }

        Map<String, FundHolding> metadata = holdings.stream()
                .filter(item -> fundCodes.contains(item.getFundCode()))
                .collect(Collectors.toMap(FundHolding::getFundCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        StrategyParams strategyParams = defaultParams(request.strategyParams());
        Map<String, List<QuantNavPointDTO>> navByFund = loadNavSeries(fundCodes, request, strategyParams, metadata);
        List<EngineFund> funds = fundCodes.stream()
                .map(code -> toEngineFund(code, metadata.get(code), navByFund.getOrDefault(code, List.of())))
                .toList();
        TrainingSampleExportRequest engineRequest = new TrainingSampleExportRequest(
                "ml-training-samples-" + LocalDateTime.now(),
                "QuantRuleEngine",
                request.startDate(),
                request.endDate(),
                defaultDecimal(request.initialCash(), DEFAULT_INITIAL_CASH),
                defaultDecimal(request.feeRate(), DEFAULT_FEE_RATE),
                funds,
                strategyParams,
                defaultOptions(request.options()),
                DEFAULT_TRAINING_LABEL_CONFIG
        );
        TrainingSampleExportResponse response = quantEngineClient.exportTrainingSamples(engineRequest);
        if (response == null || !StringUtils.hasText(response.csvContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ML training sample export returned empty content");
        }
        return response;
    }

    private NavRefreshItem refreshOneFundNav(String fundCode, FundHolding holding, LocalDate requestedStartDate, LocalDate requestedEndDate) {
        String fundName = holding == null ? fundCode : firstText(holding.getFundName(), fundCode);
        try {
            List<FundNavPointDTO> points = fundQueryService.getHistoricalNav(fundCode, requestedStartDate, requestedEndDate);
            LocalDate firstDate = points.stream()
                    .map(FundNavPointDTO::navDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate lastDate = points.stream()
                    .map(FundNavPointDTO::navDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            String status = points.isEmpty() ? "EMPTY" : "SUCCESS";
            String message = points.isEmpty() ? "未获取到历史净值" : "历史净值已缓存";
            return new NavRefreshItem(fundCode, fundName, requestedStartDate, requestedEndDate, points.size(), firstDate, lastDate, status, message);
        } catch (RuntimeException exception) {
            return new NavRefreshItem(fundCode, fundName, requestedStartDate, requestedEndDate, 0, null, null, "FAILED", exception.getMessage());
        }
    }

    private List<FundHolding> loadHoldings(Long userId, RunRequest request) {
        LambdaQueryWrapper<FundHolding> wrapper = new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId)
                .orderByDesc(FundHolding::getHoldingAmount);
        if (request.accountId() != null) {
            PortfolioAccount account = portfolioAccountMapper.selectOne(new LambdaQueryWrapper<PortfolioAccount>()
                    .eq(PortfolioAccount::getId, request.accountId())
                    .eq(PortfolioAccount::getUserId, userId)
                    .last("LIMIT 1"));
            if (account == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "portfolio account not found");
            }
            wrapper.eq(FundHolding::getAccountId, request.accountId());
        }
        List<FundHolding> rows = fundHoldingMapper.selectList(wrapper);
        if (request.fundCodes() == null || request.fundCodes().isEmpty()) {
            return rows;
        }
        LinkedHashSet<String> requestedCodes = request.fundCodes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return rows.stream()
                .filter(item -> requestedCodes.contains(item.getFundCode()))
                .toList();
    }

    private List<String> resolveFundCodes(RunRequest request, List<FundHolding> holdings) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        holdings.stream().map(FundHolding::getFundCode).forEach(codes::add);
        return new ArrayList<>(codes);
    }

    private Map<String, List<QuantNavPointDTO>> loadNavSeries(
            List<String> fundCodes,
            RunRequest request,
            StrategyParams strategyParams,
            Map<String, FundHolding> metadata) {
        if (fundCodes.isEmpty()) {
            return Map.of();
        }
        int warmupDays = Math.max(strategyParams.warmupDays() == null ? 0 : Math.max(strategyParams.warmupDays(), 0), 180);
        LocalDate seriesStartDate = request.startDate().minusDays(warmupDays);
        List<FundNavDaily> rows = fundNavDailyMapper.selectList(new LambdaQueryWrapper<FundNavDaily>()
                .in(FundNavDaily::getFundCode, fundCodes)
                .ge(FundNavDaily::getNavDate, seriesStartDate)
                .le(FundNavDaily::getNavDate, request.endDate())
                .orderByAsc(FundNavDaily::getFundCode)
                .orderByAsc(FundNavDaily::getNavDate));
        Map<String, NavigableMap<LocalDate, BigDecimal>> marketReturns = marketReturnSeries(seriesStartDate, request.endDate());
        Map<String, IndexMatch> trackingIndexByFund = trackingIndexByFund(fundCodes, metadata);
        Map<String, NavigableMap<LocalDate, BigDecimal>> trackingReturnsByFund = trackingIndexByFund.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> marketReturnSeries(entry.getValue().code(), seriesStartDate, request.endDate()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return rows.stream()
                .collect(Collectors.groupingBy(
                        FundNavDaily::getFundCode,
                        LinkedHashMap::new,
                        Collectors.mapping(item -> {
                            IndexMatch tracking = trackingIndexByFund.get(item.getFundCode());
                            return new QuantNavPointDTO(
                                    item.getNavDate(),
                                    item.getUnitNav(),
                                    item.getAccumulatedNav(),
                                    item.getDailyGrowthRate(),
                                    marketReturn(trackingReturnsByFund.get(item.getFundCode()), item.getNavDate()),
                                    tracking == null ? null : tracking.code(),
                                    tracking == null ? null : tracking.name(),
                                    marketReturn(marketReturns.get("marketSh000001ReturnRate"), item.getNavDate()),
                                    marketReturn(marketReturns.get("marketSz399001ReturnRate"), item.getNavDate()),
                                    marketReturn(marketReturns.get("marketCyb399006ReturnRate"), item.getNavDate()),
                                    marketReturn(marketReturns.get("marketHs300ReturnRate"), item.getNavDate()),
                                    marketReturn(marketReturns.get("marketZz500ReturnRate"), item.getNavDate())
                            );
                        }, Collectors.toList())
                ));
    }

    private Map<String, NavigableMap<LocalDate, BigDecimal>> marketReturnSeries(LocalDate startDate, LocalDate endDate) {
        Map<String, NavigableMap<LocalDate, BigDecimal>> result = new LinkedHashMap<>();
        COMMON_MARKET_INDICES.forEach((fieldName, indexCode) -> {
            try {
                List<MarketIndexDailyVO> history = marketDataService.historicalIndex(indexCode, startDate, endDate);
                result.put(fieldName, cumulativeReturnByDate(history));
            } catch (RuntimeException exception) {
                result.put(fieldName, new TreeMap<>());
            }
        });
        return result;
    }

    private NavigableMap<LocalDate, BigDecimal> marketReturnSeries(String indexCode, LocalDate startDate, LocalDate endDate) {
        try {
            return cumulativeReturnByDate(marketDataService.historicalIndex(indexCode, startDate, endDate));
        } catch (RuntimeException exception) {
            return new TreeMap<>();
        }
    }

    private NavigableMap<LocalDate, BigDecimal> cumulativeReturnByDate(List<MarketIndexDailyVO> history) {
        NavigableMap<LocalDate, BigDecimal> result = new TreeMap<>();
        if (history == null || history.isEmpty()) {
            return result;
        }
        BigDecimal baseClose = history.stream()
                .filter(item -> item.closePrice() != null && item.closePrice().compareTo(BigDecimal.ZERO) > 0)
                .map(MarketIndexDailyVO::closePrice)
                .findFirst()
                .orElse(null);
        if (baseClose == null) {
            return result;
        }
        history.stream()
                .filter(item -> item.tradeDate() != null && item.closePrice() != null && item.closePrice().compareTo(BigDecimal.ZERO) > 0)
                .forEach(item -> result.put(item.tradeDate(), item.closePrice()
                        .subtract(baseClose)
                        .multiply(new BigDecimal("100.0000"))
                        .divide(baseClose, 4, RoundingMode.HALF_UP)));
        return result;
    }

    private BigDecimal marketReturn(NavigableMap<LocalDate, BigDecimal> returns, LocalDate date) {
        if (returns == null || date == null) {
            return null;
        }
        Map.Entry<LocalDate, BigDecimal> entry = returns.floorEntry(date);
        return entry == null ? null : entry.getValue();
    }

    private Map<String, IndexMatch> trackingIndexByFund(List<String> fundCodes, Map<String, FundHolding> metadata) {
        Map<String, IndexMatch> result = new LinkedHashMap<>();
        for (String fundCode : fundCodes) {
            result.put(fundCode, trackingIndex(fundCode, metadata.get(fundCode)));
        }
        return result;
    }

    private IndexMatch trackingIndex(String fundCode, FundHolding holding) {
        String text = safe(fundCode);
        if (holding != null) {
            text += safe(holding.getFundName()) + safe(holding.getFundType());
        }
        if ("025833".equals(fundCode) || text.contains("电网") || text.contains("特高压")) {
            return new IndexMatch("931994", "中证电网设备");
        }
        if ("013403".equals(fundCode) || text.contains("恒生科技")) {
            return new IndexMatch("HSTECH", "恒生科技");
        }
        if ("161725".equals(fundCode) || text.contains("白酒")) {
            return new IndexMatch("399997", "中证白酒");
        }
        if (text.contains("中证500") || text.contains("500")) {
            return new IndexMatch("000905", "中证500");
        }
        if (text.contains("创业板")) {
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

    private record IndexMatch(String code, String name) {
    }

    private EngineFund toEngineFund(String fundCode, FundHolding holding, List<QuantNavPointDTO> navSeries) {
        if (holding == null) {
            return new EngineFund(fundCode, fundCode, "UNKNOWN", navSeries);
        }
        return new EngineFund(
                fundCode,
                firstText(holding.getFundName(), fundCode),
                firstText(holding.getFundType(), "UNKNOWN"),
                navSeries
        );
    }

    private StrategyParams defaultParams(StrategyParams params) {
        if (params == null) {
            params = new StrategyParams(null, null, null, null, null, null, null, null, null, null, null);
        }
        QuantFundProperties.QuantEngine quantEngine = properties.getQuantEngine();
        return new StrategyParams(
                defaultDecimal(params.buyThreshold(), quantEngine.getBuyThreshold()),
                defaultDecimal(params.sellThreshold(), quantEngine.getSellThreshold()),
                defaultDecimal(params.maxSinglePositionRate(), quantEngine.getMaxSinglePositionRate()),
                defaultDecimal(params.buyStepRatio(), quantEngine.getBuyStepRatio()),
                defaultDecimal(params.sellStepRatio(), quantEngine.getSellStepRatio()),
                defaultDecimal(params.takeProfitRate(), quantEngine.getTakeProfitRate()),
                defaultDecimal(params.stopLossRate(), quantEngine.getStopLossRate()),
                params.minNavSamples() == null ? quantEngine.getMinNavSamples() : params.minNavSamples(),
                params.warmupDays() == null ? quantEngine.getWarmupDays() : params.warmupDays(),
                defaultDecimal(params.trendHoldReturn20d(), quantEngine.getTrendHoldReturn20d()),
                defaultDecimal(params.trendHoldMa20Deviation(), quantEngine.getTrendHoldMa20Deviation())
        );
    }

    private Options defaultOptions(Options options) {
        int workers = options == null || options.workers() == null ? 6 : options.workers();
        workers = Math.max(1, Math.min(workers, 12));
        return new Options(
                workers,
                options == null || options.saveEquityCurve() == null || options.saveEquityCurve(),
                options == null || options.saveTrades() == null || options.saveTrades(),
                options != null && Boolean.TRUE.equals(options.enableMl())
        );
    }

    private void saveResults(BatchResponse response) {
        if (response == null || response.results() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        response.results().stream()
                .sorted(Comparator.comparing(Result::fundCode))
                .forEach(result -> {
                    QuantBacktestResult entity = new QuantBacktestResult();
                    entity.setStrategyName(firstText(result.strategyName(), firstText(response.strategyName(), "QuantRuleEngine")));
                    entity.setModelVersion(firstText(result.modelVersion(), firstText(response.modelVersion(), properties.getQuantEngine().getModelVersion())));
                    entity.setFundCode(result.fundCode());
                    entity.setFundName(result.fundName());
                    entity.setFundType(result.fundType());
                    entity.setStartDate(result.startDate());
                    entity.setEndDate(result.endDate());
                    entity.setBenchmarkCode("BUY_HOLD");
                    entity.setTotalReturnRate(scale(result.totalReturnRate()));
                    entity.setAnnualReturnRate(scale(result.annualReturnRate()));
                    entity.setMaxDrawdownRate(scale(result.maxDrawdownRate()));
                    entity.setWinRate(scale(result.winRate()));
                    entity.setSharpeRatio(scale(result.sharpeRatio()));
                    entity.setCalmarRatio(scale(result.calmarRatio()));
                    entity.setTradeCount(result.tradeCount() == null ? 0 : result.tradeCount());
                    entity.setResultJson(writeJson(result));
                    entity.setCreateTime(now);
                    entity.setUpdateTime(now);
                    entity.setDeleted(0);
                    quantBacktestResultMapper.insert(entity);
                });
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private BigDecimal defaultDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
