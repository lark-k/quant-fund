package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.quant.QuantAccountContextDTO;
import com.lk.quantfund.dto.quant.QuantAnalyzeRequest;
import com.lk.quantfund.dto.quant.QuantAnalyzeResponse;
import com.lk.quantfund.dto.quant.QuantHoldingContextDTO;
import com.lk.quantfund.dto.quant.QuantMarketContextDTO;
import com.lk.quantfund.dto.quant.QuantNavPointDTO;
import com.lk.quantfund.dto.quant.QuantRiskProfileDTO;
import com.lk.quantfund.dto.quant.QuantScoreDTO;
import com.lk.quantfund.dto.quant.QuantStrategyParamsDTO;
import com.lk.quantfund.dto.quant.QuantStrategyStateDTO;
import com.lk.quantfund.dto.quant.QuantTradeDTO;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.QuantSignal;
import com.lk.quantfund.entity.RiskProfile;
import com.lk.quantfund.entity.StrategySignal;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.SignalType;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.QuantSignalMapper;
import com.lk.quantfund.mapper.RiskProfileMapper;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.quant.QuantEngineClient;
import com.lk.quantfund.quant.QuantEngineException;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.service.QuantAnalysisService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import com.lk.quantfund.vo.quant.QuantEngineHealthVO;
import com.lk.quantfund.vo.quant.QuantSignalVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class QuantAnalysisServiceImpl implements QuantAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(QuantAnalysisServiceImpl.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final Duration INTRADAY_ESTIMATE_MAX_AGE = Duration.ofMinutes(5);
    private static final Duration INTRADAY_ESTIMATE_MAX_CLOCK_SKEW = Duration.ofMinutes(1);
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
    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final RiskProfileMapper riskProfileMapper;
    private final FundNavDailyMapper fundNavDailyMapper;
    private final TradeRecordMapper tradeRecordMapper;
    private final QuantSignalMapper quantSignalMapper;
    private final StrategySignalMapper strategySignalMapper;
    private final TradingCalendarService tradingCalendarService;
    private final FundQueryService fundQueryService;
    private final MarketDataService marketDataService;

    public QuantAnalysisServiceImpl(QuantEngineClient quantEngineClient,
                                    QuantFundProperties properties,
                                    ObjectMapper objectMapper,
                                    FundHoldingMapper fundHoldingMapper,
                                    PortfolioAccountMapper portfolioAccountMapper,
                                    RiskProfileMapper riskProfileMapper,
                                    FundNavDailyMapper fundNavDailyMapper,
                                    TradeRecordMapper tradeRecordMapper,
                                    QuantSignalMapper quantSignalMapper,
                                    StrategySignalMapper strategySignalMapper,
                                    TradingCalendarService tradingCalendarService,
                                    FundQueryService fundQueryService,
                                    MarketDataService marketDataService) {
        this.quantEngineClient = quantEngineClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.riskProfileMapper = riskProfileMapper;
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.tradeRecordMapper = tradeRecordMapper;
        this.quantSignalMapper = quantSignalMapper;
        this.strategySignalMapper = strategySignalMapper;
        this.tradingCalendarService = tradingCalendarService;
        this.fundQueryService = fundQueryService;
        this.marketDataService = marketDataService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuantSignalVO analyzeHolding(Long holdingId) {
        return analyzeHoldingForUser(UserContext.getUserId(), holdingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuantSignalVO analyzeHoldingForUser(Long userId, Long holdingId) {
        FundHolding holding = loadOwnedHolding(userId, holdingId);
        IntradayEstimateContext intradayEstimate = refreshIntradayEstimateBeforeQuant(holding);
        PortfolioAccount account = loadOwnedAccount(userId, holding.getAccountId());
        RiskProfile riskProfile = loadOrCreateRiskProfile(userId);
        QuantAnalyzeRequest request = buildRequest(userId, account, holding, riskProfile, intradayEstimate);
        QuantAnalyzeResponse response = analyzeWithFallback(request, account, holding, riskProfile);
        return toVO(saveSignal(userId, account, holding, request, response, fallbackUsed(response)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<QuantSignalVO> analyzeAccount(Long accountId) {
        return analyzeAccountForUser(UserContext.getUserId(), accountId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<QuantSignalVO> analyzeAccountForUser(Long userId, Long accountId) {
        PortfolioAccount account = loadOwnedAccount(userId, accountId);
        RiskProfile riskProfile = loadOrCreateRiskProfile(userId);
        List<FundHolding> holdings = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId)
                .eq(FundHolding::getAccountId, accountId)
                .orderByDesc(FundHolding::getHoldingAmount));
        if (holdings.isEmpty()) {
            return List.of();
        }
        int maxBatch = properties.getQuantEngine().getMaxBatchHoldings();
        if (holdings.size() > maxBatch) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "holding count exceeds quant engine max batch size");
        }
        Map<Long, IntradayEstimateContext> intradayEstimates = new LinkedHashMap<>();
        holdings.forEach(holding -> intradayEstimates.put(holding.getId(), refreshIntradayEstimateBeforeQuant(holding)));
        List<QuantAnalyzeRequest> requests = holdings.stream()
                .map(holding -> buildRequest(userId, account, holding, riskProfile, intradayEstimates.get(holding.getId())))
                .toList();
        List<QuantAnalyzeResponse> responses;
        boolean fallback = false;
        if (properties.getQuantEngine().isEnabled()) {
            try {
                responses = quantEngineClient.analyzeBatch(requests);
            } catch (QuantEngineException exception) {
                if (!properties.getQuantEngine().isFallbackToJavaRules()) {
                    throw exception;
                }
                log.warn("Quant engine batch unavailable, using Java fallback: {}", exception.getMessage());
                responses = fallbackBatch(requests, account, holdings, riskProfile);
                fallback = true;
            }
        } else if (properties.getQuantEngine().isFallbackToJavaRules()) {
            responses = fallbackBatch(requests, account, holdings, riskProfile);
            fallback = true;
        } else {
            throw new QuantEngineException("Quant engine is disabled and Java fallback is disabled");
        }
        List<QuantSignalVO> saved = new ArrayList<>();
        for (QuantAnalyzeRequest request : requests) {
            FundHolding holding = holdings.stream()
                    .filter(item -> item.getId().equals(request.holding().holdingId()))
                    .findFirst()
                    .orElseThrow();
            QuantAnalyzeResponse response = responses.stream()
                    .filter(item -> request.requestId().equals(item.requestId()))
                    .findFirst()
                    .orElseGet(() -> fallbackResponse(request, account, holding, riskProfile));
            saved.add(toVO(saveSignal(userId, account, holding, request, response, fallback || fallbackUsed(response))));
        }
        return saved;
    }

    @Override
    public List<QuantSignalVO> latestSignals(Long accountId, Long holdingId, String fundCode, StrategyAction action) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<QuantSignal> wrapper = new LambdaQueryWrapper<QuantSignal>()
                .eq(QuantSignal::getUserId, userId);
        if (accountId != null) {
            loadOwnedAccount(userId, accountId);
            wrapper.eq(QuantSignal::getAccountId, accountId);
        }
        if (holdingId != null) {
            loadOwnedHolding(userId, holdingId);
            wrapper.eq(QuantSignal::getHoldingId, holdingId);
        }
        if (StringUtils.hasText(fundCode)) {
            wrapper.eq(QuantSignal::getFundCode, fundCode.trim());
        }
        if (action != null) {
            wrapper.eq(QuantSignal::getAction, action.name());
        }
        wrapper.orderByDesc(QuantSignal::getSignalTime).orderByDesc(QuantSignal::getId);
        Map<Long, QuantSignal> latestByHolding = new LinkedHashMap<>();
        quantSignalMapper.selectList(wrapper).forEach(signal ->
                latestByHolding.putIfAbsent(signal.getHoldingId(), signal));
        return latestByHolding.values().stream().map(this::toVO).toList();
    }

    @Override
    public QuantEngineHealthVO health() {
        if (!properties.getQuantEngine().isEnabled()) {
            return new QuantEngineHealthVO("DISABLED", "quant-engine", properties.getQuantEngine().getModelVersion(), false);
        }
        try {
            return quantEngineClient.health();
        } catch (QuantEngineException exception) {
            return new QuantEngineHealthVO("DOWN", "quant-engine", properties.getQuantEngine().getModelVersion(), true);
        }
    }

    private QuantAnalyzeRequest buildRequest(Long userId,
                                             PortfolioAccount account,
                                             FundHolding holding,
                                             RiskProfile riskProfile,
                                             IntradayEstimateContext intradayEstimate) {
        LocalDateTime now = LocalDateTime.now();
        String phase = decisionPhase(now.toLocalTime());
        LocalDateTime deadline = parseDeadline(now.toLocalDate());
        NavSeriesContext navContext = navSeries(holding, intradayEstimate);
        IntradayEstimateContext effectiveEstimate = navContext.intradayEstimateUsed()
                ? intradayEstimate
                : IntradayEstimateContext.unavailable();
        return new QuantAnalyzeRequest(
                "qf-" + now.toLocalDate() + "-" + holding.getId() + "-" + UUID.randomUUID(),
                userId,
                new QuantAccountContextDTO(
                        account.getId(),
                        valueOrZero(account.getTotalAsset()),
                        valueOrZero(account.getTotalInvestAmount()),
                        valueOrZero(account.getCurrentProfit()),
                        valueOrZero(account.getCurrentProfitRate()),
                        valueOrZero(account.getDailyProfit()),
                        valueOrZero(account.getEquityPositionRate()),
                        valueOrZero(account.getMaxSingleFundPositionRate())
                ),
                new QuantRiskProfileDTO(
                        firstText(riskProfile.getRiskLevel(), RiskLevel.MEDIUM.name()),
                        valueOrDefault(riskProfile.getMaxEquityPositionRate(), new BigDecimal("70.0000")),
                        valueOrDefault(riskProfile.getMaxSingleFundPositionRate(), new BigDecimal("25.0000")),
                        valueOrDefault(riskProfile.getDrawdownAlertRate(), new BigDecimal("8.0000")),
                        valueOrDefault(riskProfile.getDailyRiseAlertRate(), new BigDecimal("2.0000")),
                        valueOrDefault(riskProfile.getDailyFallAlertRate(), new BigDecimal("2.0000"))
                ),
                new QuantHoldingContextDTO(
                        holding.getId(),
                        holding.getFundCode(),
                        firstText(holding.getFundName(), holding.getFundCode()),
                        firstText(holding.getFundType(), "UNKNOWN"),
                        holding.getActiveFund() != null && holding.getActiveFund() == 1,
                        valueOrZero(holding.getHoldingAmount()),
                        valueOrZero(holding.getHoldingShare()),
                        valueOrZero(holding.getHoldingCost()),
                        valueOrZero(holding.getHoldingProfit()),
                        valueOrZero(holding.getHoldingProfitRate()),
                        valueOrZero(holding.getDailyProfit()),
                        positionRate(account, holding),
                        effectiveEstimate.estimateNav(),
                        holding.getLatestOfficialNav(),
                        effectiveEstimate.growthRate(),
                        null,
                        effectiveEstimate.growthRate(),
                        null,
                        holding.getHoldingDays() == null ? 0 : holding.getHoldingDays(),
                        holding.getCoreHolding() != null && holding.getCoreHolding() == 1,
                        holding.getWatchFocus() != null && holding.getWatchFocus() == 1
                ),
                navContext.points(),
                tradeRecords(userId, holding.getId()),
                defaultStrategyParams(),
                strategyState(userId, holding.getId(), now.toLocalDate()),
                new QuantMarketContextDTO(
                        tradingCalendarService.isTradingDay(now.toLocalDate()),
                        tradingCalendarService.isIntradayEstimateWindow(now),
                        phase,
                        now,
                        deadline
                )
        );
    }

    private QuantAnalyzeResponse analyzeWithFallback(QuantAnalyzeRequest request,
                                                     PortfolioAccount account,
                                                     FundHolding holding,
                                                     RiskProfile riskProfile) {
        if (!properties.getQuantEngine().isEnabled()) {
            if (!properties.getQuantEngine().isFallbackToJavaRules()) {
                throw new QuantEngineException("Quant engine is disabled and Java fallback is disabled");
            }
            return fallbackResponse(request, account, holding, riskProfile);
        }
        try {
            return quantEngineClient.analyze(request);
        } catch (QuantEngineException exception) {
            if (!properties.getQuantEngine().isFallbackToJavaRules()) {
                throw exception;
            }
            log.warn("Quant engine unavailable for holding {}, using Java fallback: {}", holding.getId(), exception.getMessage());
            return fallbackResponse(request, account, holding, riskProfile);
        }
    }

    private List<QuantAnalyzeResponse> fallbackBatch(List<QuantAnalyzeRequest> requests,
                                                    PortfolioAccount account,
                                                    List<FundHolding> holdings,
                                                    RiskProfile riskProfile) {
        return requests.stream()
                .map(request -> {
                    FundHolding holding = holdings.stream()
                            .filter(item -> item.getId().equals(request.holding().holdingId()))
                            .findFirst()
                            .orElseThrow();
                    return fallbackResponse(request, account, holding, riskProfile);
                })
                .toList();
    }

    private QuantAnalyzeResponse fallbackResponse(QuantAnalyzeRequest request,
                                                 PortfolioAccount account,
                                                 FundHolding holding,
                                                 RiskProfile riskProfile) {
        BigDecimal positionRate = positionRate(account, holding);
        QuantStrategyParamsDTO strategyParams = defaultStrategyParams();
        BigDecimal maxSingle = strategyParams.maxSinglePositionRate();
        BigDecimal profitRate = valueOrZero(holding.getHoldingProfitRate());
        String action = "HOLD";
        String actionText = "建议持有观察";
        BigDecimal suggestRatio = ZERO;
        BigDecimal confidence = new BigDecimal("0.5000");
        List<String> reasons = new ArrayList<>();
        if (positionRate.compareTo(maxSingle) > 0) {
            action = "SELL";
            actionText = "建议减仓";
            suggestRatio = new BigDecimal("5.0000");
            confidence = new BigDecimal("0.5500");
            reasons.add("单基金仓位超过风险配置上限，Java fallback 建议先降低仓位");
        } else if (profitRate.compareTo(new BigDecimal("25.0000")) > 0) {
            action = "SELL";
            actionText = "建议分批止盈";
            suggestRatio = new BigDecimal("10.0000");
            confidence = new BigDecimal("0.6000");
            reasons.add("持仓收益率超过 25%，Java fallback 建议分批止盈");
        } else {
            reasons.add("Python 量化引擎不可用，Java fallback 未触发减仓条件");
        }
        BigDecimal suggestAmount = "SELL".equals(action)
                ? valueOrZero(holding.getHoldingAmount()).multiply(suggestRatio).divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP)
                : ZERO;
        QuantScoreDTO score = new QuantScoreDTO(new BigDecimal("50.0000"), ZERO, ZERO, ZERO, ZERO, ZERO);
        Map<String, Object> metrics = Map.of(
                "positionRate", positionRate,
                "holdingProfitRate", profitRate,
                "fallbackUsed", true
        );
        return new QuantAnalyzeResponse(
                request.requestId(),
                holding.getFundCode(),
                holding.getId(),
                action,
                actionText,
                suggestAmount,
                suggestRatio,
                confidence,
                firstText(riskProfile.getRiskLevel(), RiskLevel.MEDIUM.name()),
                score,
                metrics,
                reasons,
                List.of("Java fallback 仅保证系统可用，建议启动 Python quant-engine 获取完整评分"),
                "JavaFallbackRule",
                "fallback-v1.0.0",
                request.market().deadline() == null ? null : request.market().deadline().toString(),
                SystemConstants.DISCLAIMER
        );
    }

    private QuantStrategyParamsDTO defaultStrategyParams() {
        QuantFundProperties.QuantEngine quantEngine = properties.getQuantEngine();
        return new QuantStrategyParamsDTO(
                quantEngine.getBuyThreshold(),
                quantEngine.getSellThreshold(),
                quantEngine.getMaxSinglePositionRate(),
                quantEngine.getBuyStepRatio(),
                quantEngine.getSellStepRatio(),
                quantEngine.getTakeProfitRate(),
                quantEngine.getStopLossRate(),
                quantEngine.getMinNavSamples(),
                quantEngine.getWarmupDays(),
                quantEngine.getTrendHoldReturn20d(),
                quantEngine.getTrendHoldMa20Deviation()
        );
    }

    private QuantSignal saveSignal(Long userId,
                                   PortfolioAccount account,
                                   FundHolding holding,
                                   QuantAnalyzeRequest request,
                                   QuantAnalyzeResponse response,
                                   boolean fallbackUsed) {
        LocalDateTime now = LocalDateTime.now();
        QuantSignal signal = quantSignalMapper.selectOne(new LambdaQueryWrapper<QuantSignal>()
                .eq(QuantSignal::getUserId, userId)
                .eq(QuantSignal::getHoldingId, holding.getId())
                .eq(QuantSignal::getTradeDate, now.toLocalDate())
                .eq(QuantSignal::getDecisionPhase, request.market().decisionPhase())
                .eq(QuantSignal::getModelVersion, response.modelVersion())
                .last("LIMIT 1"));
        if (signal == null) {
            signal = new QuantSignal();
            signal.setUserId(userId);
            signal.setAccountId(account.getId());
            signal.setHoldingId(holding.getId());
            signal.setFundCode(holding.getFundCode());
            signal.setFundName(firstText(holding.getFundName(), holding.getFundCode()));
            signal.setTradeDate(now.toLocalDate());
            signal.setDecisionPhase(request.market().decisionPhase());
            signal.setCreateTime(now);
            signal.setDeleted(0);
        }
        QuantScoreDTO score = response.score() == null
                ? new QuantScoreDTO(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO)
                : response.score();
        signal.setAction(response.action());
        signal.setActionText(response.actionText());
        BigDecimal suggestRatio = scale(response.suggestRatio());
        signal.setSuggestAmount(normalizeSuggestAmount(response.action(), response.suggestAmount(), suggestRatio, account, holding));
        signal.setSuggestRatio(suggestRatio);
        signal.setRiskLevel(firstText(response.riskLevel(), RiskLevel.MEDIUM.name()));
        signal.setConfidence(scale(response.confidence()));
        signal.setTotalScore(scale(score.totalScore()));
        signal.setTrendScore(scale(score.trendScore()));
        signal.setOpportunityScore(scale(score.opportunityScore()));
        signal.setRiskScore(scale(score.riskScore()));
        signal.setPositionScore(scale(score.positionScore()));
        signal.setMomentumScore(scale(score.momentumScore()));
        signal.setMetricsJson(writeJson(response.metrics()));
        signal.setReasonsJson(writeJson(response.reasons()));
        signal.setRisksJson(writeJson(response.risks()));
        signal.setModelName(firstText(response.modelName(), "QuantRuleEngine"));
        signal.setModelVersion(firstText(response.modelVersion(), properties.getQuantEngine().getModelVersion()));
        signal.setRequestPayload(writeJson(request));
        signal.setResponsePayload(writeJson(response));
        signal.setFallbackUsed(fallbackUsed ? 1 : 0);
        signal.setDeadline(parseDateTime(response.deadline()));
        signal.setSignalTime(now);
        signal.setUpdateTime(now);
        if (signal.getId() == null) {
            quantSignalMapper.insert(signal);
        } else {
            quantSignalMapper.updateById(signal);
        }
        syncStrategySignal(userId, account, holding, signal);
        return signal;
    }

    private void syncStrategySignal(Long userId, PortfolioAccount account, FundHolding holding, QuantSignal quantSignal) {
        LocalDateTime now = LocalDateTime.now();
        StrategySignal signal = strategySignalMapper.selectOne(new LambdaQueryWrapper<StrategySignal>()
                .eq(StrategySignal::getUserId, userId)
                .eq(StrategySignal::getHoldingId, holding.getId())
                .eq(StrategySignal::getSignalType, SignalType.QUANT_MODEL.name())
                .ge(StrategySignal::getSignalTime, now.toLocalDate().atStartOfDay())
                .orderByDesc(StrategySignal::getSignalTime)
                .last("LIMIT 1"));
        if (signal == null) {
            signal = new StrategySignal();
            signal.setUserId(userId);
            signal.setAccountId(account.getId());
            signal.setHoldingId(holding.getId());
            signal.setFundCode(holding.getFundCode());
            signal.setSignalType(SignalType.QUANT_MODEL.name());
            signal.setCreateTime(now);
            signal.setDeleted(0);
        }
        signal.setAction(quantSignal.getAction());
        signal.setActionText(quantSignal.getActionText());
        signal.setSuggestAmount(scale(quantSignal.getSuggestAmount()));
        signal.setSuggestRatio(scale(quantSignal.getSuggestRatio()));
        signal.setRiskLevel(quantSignal.getRiskLevel());
        signal.setConfidence(scale(quantSignal.getConfidence()));
        signal.setReasonJson(quantSignal.getReasonsJson());
        signal.setSignalTime(now);
        signal.setUpdateTime(now);
        if (signal.getId() == null) {
            strategySignalMapper.insert(signal);
        } else {
            strategySignalMapper.updateById(signal);
        }
    }

    private NavSeriesContext navSeries(FundHolding holding, IntradayEstimateContext intradayEstimate) {
        List<FundNavDaily> rows = fundNavDailyMapper.selectList(new LambdaQueryWrapper<FundNavDaily>()
                .eq(FundNavDaily::getFundCode, holding.getFundCode())
                .orderByDesc(FundNavDaily::getNavDate)
                .last("LIMIT 260"));
        List<FundNavDaily> sorted = rows.stream()
                .sorted(Comparator.comparing(FundNavDaily::getNavDate))
                .toList();
        if (sorted.isEmpty()) {
            if (!intradayEstimate.available()) {
                return new NavSeriesContext(List.of(), false);
            }
            return new NavSeriesContext(List.of(intradayNavPoint(intradayEstimate)), true);
        }
        LocalDate startDate = sorted.get(0).getNavDate();
        LocalDate latestOfficialDate = sorted.get(sorted.size() - 1).getNavDate();
        boolean useIntradayEstimate = intradayEstimate.available() && latestOfficialDate.isBefore(LocalDate.now());
        LocalDate endDate = useIntradayEstimate ? LocalDate.now() : latestOfficialDate;
        Map<String, NavigableMap<LocalDate, BigDecimal>> marketReturns = marketReturnSeries(startDate, endDate);
        IndexMatch tracking = trackingIndex(holding);
        NavigableMap<LocalDate, BigDecimal> trackingReturns = marketReturnSeries(tracking.code(), startDate, endDate);
        List<QuantNavPointDTO> points = new ArrayList<>(sorted.stream()
                .map(item -> new QuantNavPointDTO(
                        item.getNavDate(),
                        item.getUnitNav(),
                        item.getAccumulatedNav(),
                        item.getDailyGrowthRate(),
                        marketReturn(trackingReturns, item.getNavDate()),
                        tracking.code(),
                        tracking.name(),
                        marketReturn(marketReturns.get("marketSh000001ReturnRate"), item.getNavDate()),
                        marketReturn(marketReturns.get("marketSz399001ReturnRate"), item.getNavDate()),
                        marketReturn(marketReturns.get("marketCyb399006ReturnRate"), item.getNavDate()),
                        marketReturn(marketReturns.get("marketHs300ReturnRate"), item.getNavDate()),
                        marketReturn(marketReturns.get("marketZz500ReturnRate"), item.getNavDate())
                ))
                .toList());
        if (useIntradayEstimate) {
            points.add(intradayNavPoint(intradayEstimate));
        }
        return new NavSeriesContext(List.copyOf(points), useIntradayEstimate);
    }

    private QuantNavPointDTO intradayNavPoint(IntradayEstimateContext estimate) {
        return new QuantNavPointDTO(
                estimate.estimateTime().toLocalDate(),
                estimate.estimateNav(),
                null,
                estimate.growthRate(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                estimate.estimateTime(),
                estimate.sourceName()
        );
    }

    private Map<String, NavigableMap<LocalDate, BigDecimal>> marketReturnSeries(LocalDate startDate, LocalDate endDate) {
        Map<String, NavigableMap<LocalDate, BigDecimal>> result = new LinkedHashMap<>();
        COMMON_MARKET_INDICES.forEach((fieldName, indexCode) -> result.put(fieldName, marketReturnSeries(indexCode, startDate, endDate)));
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

    private IndexMatch trackingIndex(FundHolding holding) {
        String fundCode = holding == null ? "" : safe(holding.getFundCode());
        String text = fundCode;
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

    private List<QuantTradeDTO> tradeRecords(Long userId, Long holdingId) {
        LocalDateTime since = LocalDateTime.now().minusDays(180);
        List<TradeRecord> rows = tradeRecordMapper.selectList(new LambdaQueryWrapper<TradeRecord>()
                .eq(TradeRecord::getUserId, userId)
                .eq(TradeRecord::getHoldingId, holdingId)
                .ge(TradeRecord::getTradeTime, since)
                .orderByDesc(TradeRecord::getTradeTime)
                .last("LIMIT 50"));
        return rows.stream()
                .sorted(Comparator.comparing(TradeRecord::getTradeTime))
                .map(item -> new QuantTradeDTO(
                        item.getTradeType(),
                        valueOrZero(item.getTradeAmount()),
                        valueOrZero(item.getTradeShare()),
                        valueOrZero(item.getTradeNav()),
                        item.getTradeTime()
                ))
                .toList();
    }

    private QuantStrategyStateDTO strategyState(Long userId, Long holdingId, LocalDate today) {
        List<QuantSignal> rows = quantSignalMapper.selectList(new LambdaQueryWrapper<QuantSignal>()
                .eq(QuantSignal::getUserId, userId)
                .eq(QuantSignal::getHoldingId, holdingId)
                .lt(QuantSignal::getTradeDate, today)
                .ge(QuantSignal::getTradeDate, today.minusDays(120))
                .eq(QuantSignal::getDeleted, 0)
                .orderByDesc(QuantSignal::getTradeDate)
                .orderByDesc(QuantSignal::getSignalTime));
        List<TradeRecord> completedReductions = tradeRecordMapper.selectList(new LambdaQueryWrapper<TradeRecord>()
                .eq(TradeRecord::getUserId, userId)
                .eq(TradeRecord::getHoldingId, holdingId)
                .eq(TradeRecord::getTradeStatus, "COMPLETED")
                .in(TradeRecord::getTradeType, List.of("SELL", "CONVERT_OUT"))
                .ge(TradeRecord::getTradeTime, today.minusDays(120).atStartOfDay())
                .orderByDesc(TradeRecord::getTradeTime));
        Map<LocalDate, QuantSignal> latestByDate = new LinkedHashMap<>();
        rows.forEach(row -> latestByDate.putIfAbsent(row.getTradeDate(), row));
        QuantStrategyStateDTO latestState = null;
        ConfirmedExtremeState confirmedExtreme = null;
        boolean extremeResetReached = false;
        for (QuantSignal signal : latestByDate.values()) {
            Map<String, Object> metrics = readMap(signal.getMetricsJson());
            if (metrics.containsKey("weakTrendCooldownDaysAfter")) {
                if (latestState == null) {
                    latestState = strategyStateFromMetrics(metrics);
                }
                int suggestedStage = metrics.containsKey("extremeRiskStageAfter")
                        ? integerValue(metrics.get("extremeRiskStageAfter"))
                        : integerValue(metrics.get("extremeRiskSellCountAfter"));
                if (confirmedExtreme == null && !extremeResetReached) {
                    if (suggestedStage <= 0) {
                        extremeResetReached = true;
                    } else if ("SELL".equals(signal.getAction())
                            && "extreme_risk_exit".equals(stringValue(metrics.get("decisionReason")))
                            && hasCompletedReductionAfter(signal, completedReductions)) {
                        confirmedExtreme = new ConfirmedExtremeState(
                                suggestedStage,
                                stringValue(metrics.get("lastExtremeRiskDateAfter")),
                                decimalValue(metrics.get("lastExtremeDrawdownAfter"))
                        );
                    }
                }
            }
            // Treat a legacy 15% defense recommendation as an already handled event.
            if (latestState == null && "SELL".equals(signal.getAction()) && signal.getSuggestRatio() != null
                    && signal.getSuggestRatio().compareTo(new BigDecimal("15.0000")) == 0) {
                String signalDate = signal.getTradeDate() == null ? null : signal.getTradeDate().toString();
                latestState = new QuantStrategyStateDTO(4, true, 45, 0, true, 0,
                        null, null, signalDate, 0, signalDate);
            }
        }
        if (latestState == null) {
            return QuantStrategyStateDTO.initial();
        }
        int confirmedStage = confirmedExtreme == null ? 0 : confirmedExtreme.stage();
        return new QuantStrategyStateDTO(
                latestState.weakTrendCandidateDays(),
                latestState.weakTrendDefenseHandled(),
                latestState.weakTrendCooldownDays(),
                latestState.positionRebalanceCooldownDays(),
                latestState.weakRecoveryRequired(),
                confirmedStage,
                confirmedExtreme == null ? null : confirmedExtreme.signalDate(),
                confirmedExtreme == null ? null : confirmedExtreme.drawdown(),
                latestState.lastActionDate(),
                confirmedStage,
                latestState.lastDefenseDate()
        );
    }

    private QuantStrategyStateDTO strategyStateFromMetrics(Map<String, Object> metrics) {
        return new QuantStrategyStateDTO(
                integerValue(metrics.get("weakTrendCandidateDaysAfter")),
                booleanValue(metrics.get("weakTrendDefenseHandledAfter")),
                integerValue(metrics.get("weakTrendCooldownDaysAfter")),
                integerValue(metrics.get("positionRebalanceCooldownDaysAfter")),
                booleanValue(metrics.get("weakRecoveryRequiredAfter")),
                integerValue(metrics.get("extremeRiskStageAfter")),
                stringValue(metrics.get("lastExtremeRiskDateAfter")),
                decimalValue(metrics.get("lastExtremeDrawdownAfter")),
                stringValue(metrics.get("lastActionDateAfter")),
                integerValue(metrics.get("extremeRiskSellCountAfter")),
                stringValue(metrics.get("lastDefenseDateAfter"))
        );
    }

    private boolean hasCompletedReductionAfter(QuantSignal signal, List<TradeRecord> reductions) {
        LocalDateTime signalTime = signal.getSignalTime();
        if (signalTime == null && signal.getTradeDate() != null) {
            signalTime = signal.getTradeDate().atStartOfDay();
        }
        if (signalTime == null) {
            return false;
        }
        LocalDateTime finalSignalTime = signalTime;
        return reductions.stream().anyMatch(trade -> {
            if (!"COMPLETED".equals(trade.getTradeStatus())
                    || !("SELL".equals(trade.getTradeType()) || "CONVERT_OUT".equals(trade.getTradeType()))) {
                return false;
            }
            LocalDateTime recordedAt = trade.getCreateTime() == null ? trade.getTradeTime() : trade.getCreateTime();
            return trade.getTradeTime() != null
                    && !trade.getTradeTime().isBefore(finalSignalTime)
                    && recordedAt != null
                    && !recordedAt.isBefore(finalSignalTime);
        });
    }

    private record ConfirmedExtremeState(int stage, String signalDate, BigDecimal drawdown) {
    }

    private record NavSeriesContext(List<QuantNavPointDTO> points, boolean intradayEstimateUsed) {
    }

    private record IntradayEstimateContext(boolean available,
                                           BigDecimal estimateNav,
                                           BigDecimal growthRate,
                                           LocalDateTime estimateTime,
                                           String sourceName) {
        private static IntradayEstimateContext unavailable() {
            return new IntradayEstimateContext(false, null, ZERO, null, null);
        }
    }

    private RiskProfile loadOrCreateRiskProfile(Long userId) {
        RiskProfile profile = riskProfileMapper.selectOne(new LambdaQueryWrapper<RiskProfile>()
                .eq(RiskProfile::getUserId, userId)
                .last("LIMIT 1"));
        if (profile != null) {
            return profile;
        }
        LocalDateTime now = LocalDateTime.now();
        RiskProfile created = new RiskProfile();
        created.setUserId(userId);
        created.setRiskLevel(RiskLevel.MEDIUM.name());
        created.setMaxEquityPositionRate(new BigDecimal("70.0000"));
        created.setMaxSingleFundPositionRate(new BigDecimal("25.0000"));
        created.setDrawdownAlertRate(new BigDecimal("8.0000"));
        created.setDailyRiseAlertRate(new BigDecimal("2.0000"));
        created.setDailyFallAlertRate(new BigDecimal("2.0000"));
        created.setConfigJson("{}");
        created.setCreateTime(now);
        created.setUpdateTime(now);
        created.setDeleted(0);
        riskProfileMapper.insert(created);
        return created;
    }

    private FundHolding loadOwnedHolding(Long userId, Long holdingId) {
        FundHolding holding = fundHoldingMapper.selectOne(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getId, holdingId)
                .eq(FundHolding::getUserId, userId)
                .last("LIMIT 1"));
        if (holding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "fund holding not found");
        }
        return holding;
    }

    private PortfolioAccount loadOwnedAccount(Long userId, Long accountId) {
        PortfolioAccount account = portfolioAccountMapper.selectOne(new LambdaQueryWrapper<PortfolioAccount>()
                .eq(PortfolioAccount::getId, accountId)
                .eq(PortfolioAccount::getUserId, userId)
                .last("LIMIT 1"));
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "portfolio account not found");
        }
        return account;
    }

    private QuantSignalVO toVO(QuantSignal signal) {
        return new QuantSignalVO(
                signal.getId(),
                signal.getAccountId(),
                signal.getHoldingId(),
                signal.getFundCode(),
                signal.getFundName(),
                signal.getAction(),
                signal.getActionText(),
                signal.getSuggestAmount(),
                signal.getSuggestRatio(),
                signal.getRiskLevel(),
                signal.getConfidence(),
                signal.getTotalScore(),
                signal.getTrendScore(),
                signal.getOpportunityScore(),
                signal.getRiskScore(),
                signal.getPositionScore(),
                signal.getMomentumScore(),
                readStringList(signal.getReasonsJson()),
                readStringList(signal.getRisksJson()),
                signal.getMetricsJson(),
                signal.getModelName(),
                signal.getModelVersion(),
                signal.getDeadline() == null ? null : signal.getDeadline().toString(),
                signal.getSignalTime(),
                signal.getFallbackUsed() != null && signal.getFallbackUsed() == 1,
                SystemConstants.DISCLAIMER
        );
    }

    private String decisionPhase(LocalTime time) {
        if (time.isBefore(LocalTime.of(9, 30))) return "BEFORE_OPEN";
        if (time.isBefore(LocalTime.of(11, 30))) return "MORNING";
        if (time.isBefore(LocalTime.of(13, 0))) return "MIDDAY_BREAK";
        if (time.isBefore(LocalTime.of(14, 30))) return "AFTERNOON";
        if (time.isBefore(LocalTime.of(14, 50))) return "PRE_DECISION";
        if (time.isBefore(LocalTime.of(14, 57))) return "FINAL_DECISION";
        return "CLOSED";
    }

    private LocalDateTime parseDeadline(LocalDate date) {
        try {
            return LocalDateTime.of(date, LocalTime.parse(properties.getQuantEngine().getDecisionDeadline()));
        } catch (DateTimeParseException exception) {
            return LocalDateTime.of(date, LocalTime.of(15, 0));
        }
    }

    private LocalDateTime parseDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim().replace(" ", "T"));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private BigDecimal positionRate(PortfolioAccount account, FundHolding holding) {
        BigDecimal totalAsset = valueOrZero(account.getTotalAsset());
        if (totalAsset.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return valueOrZero(holding.getHoldingAmount()).multiply(new BigDecimal("100.0000")).divide(totalAsset, 4, RoundingMode.HALF_UP);
    }

    private IntradayEstimateContext refreshIntradayEstimateBeforeQuant(FundHolding holding) {
        LocalDateTime now = LocalDateTime.now();
        if (!tradingCalendarService.isIntradayEstimateWindow(now)) {
            return IntradayEstimateContext.unavailable();
        }
        try {
            FundEstimateDTO estimate = fundQueryService.getIntradayEstimate(holding.getFundCode(), false);
            if (!validIntradayEstimate(estimate, now)) {
                log.warn("Intraday estimate ignored before quant for holding {} because it is stale, delayed, or invalid", holding.getId());
                return IntradayEstimateContext.unavailable();
            }
            if (StringUtils.hasText(estimate.fundName())) {
                holding.setFundName(estimate.fundName());
            }
            holding.setCurrentEstimateNav(scale(estimate.estimateNav()));
            holding.setUpdateTime(now);
            fundHoldingMapper.updateById(holding);
            return new IntradayEstimateContext(
                    true,
                    scale(estimate.estimateNav()),
                    estimateGrowthRate(holding, estimate),
                    estimate.estimateTime(),
                    firstText(estimate.sourceName(), "INTRADAY_ESTIMATE")
            );
        } catch (RuntimeException exception) {
            log.warn("Refresh intraday estimate skipped before quant for holding {}: {}", holding.getId(), exception.getMessage());
            return IntradayEstimateContext.unavailable();
        }
    }

    private boolean validIntradayEstimate(FundEstimateDTO estimate, LocalDateTime now) {
        if (estimate == null || estimate.delayed() || estimate.estimateNav() == null
                || estimate.estimateNav().compareTo(BigDecimal.ZERO) <= 0
                || estimate.estimateDate() == null || !estimate.estimateDate().equals(now.toLocalDate())
                || estimate.estimateTime() == null || !estimate.estimateTime().toLocalDate().equals(now.toLocalDate())) {
            return false;
        }
        Duration age = Duration.between(estimate.estimateTime(), now);
        if (age.isNegative()) {
            return age.abs().compareTo(INTRADAY_ESTIMATE_MAX_CLOCK_SKEW) <= 0;
        }
        return age.compareTo(INTRADAY_ESTIMATE_MAX_AGE) <= 0;
    }

    private BigDecimal estimateGrowthRate(FundHolding holding, FundEstimateDTO estimateContext) {
        if (estimateContext.estimateGrowthRate() != null) {
            return scale(estimateContext.estimateGrowthRate());
        }
        BigDecimal latest = holding.getLatestOfficialNav();
        BigDecimal estimate = estimateContext.estimateNav();
        if (latest == null || estimate == null || latest.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return estimate.subtract(latest).multiply(new BigDecimal("100.0000")).divide(latest, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeSuggestAmount(String action,
                                              BigDecimal suggestAmount,
                                              BigDecimal suggestRatio,
                                              PortfolioAccount account,
                                              FundHolding holding) {
        BigDecimal amount = scale(suggestAmount);
        if (amount.compareTo(BigDecimal.ZERO) > 0 || suggestRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return amount;
        }
        if ("SELL".equals(action) || "CONVERT".equals(action)) {
            return valueOrZero(holding.getHoldingAmount())
                    .multiply(suggestRatio)
                    .divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
        }
        if ("BUY".equals(action)) {
            return valueOrZero(account.getTotalAsset())
                    .multiply(suggestRatio)
                    .divide(new BigDecimal("100.0000"), 4, RoundingMode.HALF_UP);
        }
        return ZERO;
    }

    private boolean fallbackUsed(QuantAnalyzeResponse response) {
        return "JavaFallbackRule".equals(response.modelName()) || "fallback-v1.0.0".equals(response.modelVersion());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            return value instanceof Map ? "{}" : "[]";
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private int integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return scale(value);
    }

    private BigDecimal valueOrDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? scale(fallback) : scale(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
