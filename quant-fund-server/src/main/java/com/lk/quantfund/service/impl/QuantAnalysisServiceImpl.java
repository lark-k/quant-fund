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
import com.lk.quantfund.service.QuantAnalysisService;
import com.lk.quantfund.vo.quant.QuantEngineHealthVO;
import com.lk.quantfund.vo.quant.QuantSignalVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                                    FundQueryService fundQueryService) {
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
        refreshIntradayEstimateBeforeQuant(holding);
        PortfolioAccount account = loadOwnedAccount(userId, holding.getAccountId());
        RiskProfile riskProfile = loadOrCreateRiskProfile(userId);
        QuantAnalyzeRequest request = buildRequest(userId, account, holding, riskProfile);
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
        holdings.forEach(this::refreshIntradayEstimateBeforeQuant);
        List<QuantAnalyzeRequest> requests = holdings.stream()
                .map(holding -> buildRequest(userId, account, holding, riskProfile))
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
        } else {
            responses = fallbackBatch(requests, account, holdings, riskProfile);
            fallback = true;
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

    private QuantAnalyzeRequest buildRequest(Long userId, PortfolioAccount account, FundHolding holding, RiskProfile riskProfile) {
        LocalDateTime now = LocalDateTime.now();
        String phase = decisionPhase(now.toLocalTime());
        LocalDateTime deadline = parseDeadline(now.toLocalDate());
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
                        holding.getCurrentEstimateNav(),
                        holding.getLatestOfficialNav(),
                        estimateGrowthRate(holding),
                        null,
                        estimateGrowthRate(holding),
                        null,
                        holding.getHoldingDays() == null ? 0 : holding.getHoldingDays(),
                        holding.getCoreHolding() != null && holding.getCoreHolding() == 1,
                        holding.getWatchFocus() != null && holding.getWatchFocus() == 1
                ),
                navSeries(holding.getFundCode()),
                tradeRecords(userId, holding.getId()),
                defaultStrategyParams(),
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

    private List<QuantNavPointDTO> navSeries(String fundCode) {
        List<FundNavDaily> rows = fundNavDailyMapper.selectList(new LambdaQueryWrapper<FundNavDaily>()
                .eq(FundNavDaily::getFundCode, fundCode)
                .orderByDesc(FundNavDaily::getNavDate)
                .last("LIMIT 260"));
        return rows.stream()
                .sorted(Comparator.comparing(FundNavDaily::getNavDate))
                .map(item -> new QuantNavPointDTO(item.getNavDate(), item.getUnitNav(), item.getAccumulatedNav(), item.getDailyGrowthRate()))
                .toList();
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

    private void refreshIntradayEstimateBeforeQuant(FundHolding holding) {
        LocalDateTime now = LocalDateTime.now();
        if (!tradingCalendarService.isIntradayEstimateWindow(now)) {
            return;
        }
        try {
            FundEstimateDTO estimate = fundQueryService.getIntradayEstimate(holding.getFundCode(), false);
            if (StringUtils.hasText(estimate.fundName())) {
                holding.setFundName(estimate.fundName());
            }
            if (estimate.estimateNav() != null) {
                holding.setCurrentEstimateNav(scale(estimate.estimateNav()));
            }
            holding.setUpdateTime(now);
            fundHoldingMapper.updateById(holding);
        } catch (RuntimeException exception) {
            log.warn("Refresh intraday estimate skipped before quant for holding {}: {}", holding.getId(), exception.getMessage());
        }
    }

    private BigDecimal estimateGrowthRate(FundHolding holding) {
        BigDecimal latest = holding.getLatestOfficialNav();
        BigDecimal estimate = holding.getCurrentEstimateNav();
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
