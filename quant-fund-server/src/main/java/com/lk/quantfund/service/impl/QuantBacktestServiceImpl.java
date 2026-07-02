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
import com.lk.quantfund.service.QuantBacktestService;
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

    private final QuantEngineClient quantEngineClient;
    private final QuantFundProperties properties;
    private final ObjectMapper objectMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final FundNavDailyMapper fundNavDailyMapper;
    private final QuantBacktestResultMapper quantBacktestResultMapper;
    private final FundQueryService fundQueryService;

    public QuantBacktestServiceImpl(QuantEngineClient quantEngineClient,
                                    QuantFundProperties properties,
                                    ObjectMapper objectMapper,
                                    PortfolioAccountMapper portfolioAccountMapper,
                                    FundHoldingMapper fundHoldingMapper,
                                    FundNavDailyMapper fundNavDailyMapper,
                                    QuantBacktestResultMapper quantBacktestResultMapper,
                                    FundQueryService fundQueryService) {
        this.quantEngineClient = quantEngineClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.quantBacktestResultMapper = quantBacktestResultMapper;
        this.fundQueryService = fundQueryService;
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
        Map<String, List<QuantNavPointDTO>> navByFund = loadNavSeries(fundCodes, request, strategyParams);
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
        LocalDate requestedStartDate = request.startDate().minusDays(Math.max(strategyParams.warmupDays(), 0));
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
        Map<String, List<QuantNavPointDTO>> navByFund = loadNavSeries(fundCodes, request, strategyParams);
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

    private Map<String, List<QuantNavPointDTO>> loadNavSeries(List<String> fundCodes, RunRequest request, StrategyParams strategyParams) {
        if (fundCodes.isEmpty()) {
            return Map.of();
        }
        int warmupDays = strategyParams.warmupDays() == null ? 0 : Math.max(strategyParams.warmupDays(), 0);
        List<FundNavDaily> rows = fundNavDailyMapper.selectList(new LambdaQueryWrapper<FundNavDaily>()
                .in(FundNavDaily::getFundCode, fundCodes)
                .ge(FundNavDaily::getNavDate, request.startDate().minusDays(warmupDays))
                .le(FundNavDaily::getNavDate, request.endDate())
                .orderByAsc(FundNavDaily::getFundCode)
                .orderByAsc(FundNavDaily::getNavDate));
        return rows.stream()
                .collect(Collectors.groupingBy(
                        FundNavDaily::getFundCode,
                        LinkedHashMap::new,
                        Collectors.mapping(item -> new QuantNavPointDTO(
                                item.getNavDate(),
                                item.getUnitNav(),
                                item.getAccumulatedNav(),
                                item.getDailyGrowthRate()
                        ), Collectors.toList())
                ));
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
