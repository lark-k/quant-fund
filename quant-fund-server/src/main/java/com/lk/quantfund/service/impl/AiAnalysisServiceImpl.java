package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.ai.AiAnalysisClient;
import com.lk.quantfund.ai.model.AiAnalysisContext;
import com.lk.quantfund.ai.model.AiAnalysisResult;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.entity.AiAnalysisReport;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.RiskProfile;
import com.lk.quantfund.entity.StrategySignal;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.SignalType;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.AiAnalysisReportMapper;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.RiskProfileMapper;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.service.AiAnalysisService;
import com.lk.quantfund.service.QuantAnalysisService;
import com.lk.quantfund.service.StrategyService;
import com.lk.quantfund.vo.ai.AiAnalysisReportVO;
import com.lk.quantfund.vo.quant.QuantSignalVO;
import com.lk.quantfund.vo.strategy.StrategySignalVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisServiceImpl.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final int AI_HISTORY_LIMIT = 5;
    private static final int MODEL_NAME_MAX_LENGTH = 128;
    private static final int ACTION_TEXT_MAX_LENGTH = 128;
    private static final int DEADLINE_MAX_LENGTH = 32;
    private static final int STRATEGY_MAX_LENGTH = 512;

    private final AiAnalysisClient aiAnalysisClient;
    private final StrategyService strategyService;
    private final QuantAnalysisService quantAnalysisService;
    private final QuantFundProperties properties;
    private final ObjectMapper objectMapper;
    private final AiAnalysisReportMapper aiAnalysisReportMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final RiskProfileMapper riskProfileMapper;
    private final StrategySignalMapper strategySignalMapper;

    public AiAnalysisServiceImpl(AiAnalysisClient aiAnalysisClient,
                                 StrategyService strategyService,
                                 QuantAnalysisService quantAnalysisService,
                                 QuantFundProperties properties,
                                 ObjectMapper objectMapper,
                                 AiAnalysisReportMapper aiAnalysisReportMapper,
                                 FundHoldingMapper fundHoldingMapper,
                                 PortfolioAccountMapper portfolioAccountMapper,
                                 RiskProfileMapper riskProfileMapper,
                                 StrategySignalMapper strategySignalMapper) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.strategyService = strategyService;
        this.quantAnalysisService = quantAnalysisService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.aiAnalysisReportMapper = aiAnalysisReportMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.riskProfileMapper = riskProfileMapper;
        this.strategySignalMapper = strategySignalMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAnalysisReportVO analyzeHolding(Long holdingId) {
        return analyzeHoldingForUser(UserContext.getUserId(), holdingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAnalysisReportVO analyzeHoldingForUser(Long userId, Long holdingId) {
        FundHolding holding = loadOwnedHolding(userId, holdingId);
        PortfolioAccount account = loadOwnedAccount(userId, holding.getAccountId());
        QuantSignalVO quantSignal = refreshStrategySignals(userId, holdingId);
        return analyzeHoldingWithQuantSignal(userId, account, holding, quantSignal);
    }

    private AiAnalysisReportVO analyzeHoldingWithQuantSignal(Long userId,
                                                             PortfolioAccount account,
                                                             FundHolding holding,
                                                             QuantSignalVO quantSignal) {
        AiAnalysisContext context = buildContext(userId, account, holding, quantSignal);
        AiAnalysisResult result = alignWithQuantSignal(analyzeWithFallback(context), quantSignal);
        return toVO(saveReport(userId, context, result));
    }

    private AiAnalysisResult analyzeWithFallback(AiAnalysisContext context) {
        try {
            return aiAnalysisClient.analyze(context);
        } catch (RuntimeException exception) {
            log.warn("AI client failed unexpectedly for fund {}, saving fallback report: {}",
                    context.fundCode(), exception.getMessage());
            return new AiAnalysisResult(
                    StrategyAction.WATCH,
                    "建议观察",
                    ZERO,
                    ZERO,
                    new BigDecimal("0.3000"),
                    RiskLevel.MEDIUM,
                    "15:00前",
                    "AI 降级观察",
                    List.of("AI 调用异常，已保留最新量化建议", readableMessage(exception)),
                    List.of("当日估值只作为参考，晚间正式净值前不是最终净值"),
                    "AI 分析暂不可用，使用量化结果生成保守解释。",
                    "建议以最新量化结果为准并稍后重试 AI 分析。",
                    true,
                    null
            );
        }
    }

    private QuantSignalVO refreshStrategySignals(Long userId, Long holdingId) {
        QuantSignalVO quantSignal = quantAnalysisService.analyzeHoldingForUser(userId, holdingId);
        try {
            strategyService.analyzeHoldingForUser(userId, holdingId);
        } catch (RuntimeException exception) {
            log.warn("Strategy analysis skipped before AI analysis for holding {}: {}", holdingId, exception.getMessage());
        }
        return quantSignal;
    }

    @Override
    public List<AiAnalysisReportVO> analyzeAccount(Long accountId) {
        Long userId = UserContext.getUserId();
        PortfolioAccount account = loadOwnedAccount(userId, accountId);
        List<FundHolding> holdings = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getUserId, userId)
                        .eq(FundHolding::getAccountId, accountId)
                        .orderByDesc(FundHolding::getHoldingAmount));
        if (holdings.isEmpty()) {
            return List.of();
        }
        Map<Long, QuantSignalVO> latestQuantSignals = quantAnalysisService.latestSignals(accountId, null, null, null)
                .stream()
                .collect(Collectors.toMap(QuantSignalVO::holdingId, Function.identity(), (left, right) -> left));
        int concurrency = Math.min(properties.getAi().getAccountAnalysisConcurrency(), holdings.size());
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<CompletableFuture<AiAnalysisReportVO>> futures = holdings.stream()
                    .map(holding -> CompletableFuture.supplyAsync(
                            () -> analyzeHoldingForAccount(userId, account, holding, latestQuantSignals),
                            executor))
                    .toList();
            return futures.stream()
                    .map(this::joinAnalysisFuture)
                    .toList();
        } finally {
            executor.shutdown();
        }
    }

    private AiAnalysisReportVO analyzeHoldingForAccount(Long userId,
                                                        PortfolioAccount account,
                                                        FundHolding holding,
                                                        Map<Long, QuantSignalVO> latestQuantSignals) {
        QuantSignalVO quantSignal = latestQuantSignals.get(holding.getId());
        if (quantSignal == null) {
            quantSignal = quantAnalysisService.analyzeHoldingForUser(userId, holding.getId());
        }
        return analyzeHoldingWithQuantSignal(userId, account, holding, quantSignal);
    }

    private AiAnalysisReportVO joinAnalysisFuture(CompletableFuture<AiAnalysisReportVO> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AiAnalysisReportVO> history(Long accountId, Long holdingId, String fundCode) {
        Long userId = UserContext.getUserId();
        List<Long> activeHoldingIds = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getUserId, userId))
                .stream()
                .map(FundHolding::getId)
                .toList();
        if (activeHoldingIds.isEmpty()) {
            return List.of();
        }
        activeHoldingIds.forEach(activeHoldingId -> pruneHistory(userId, activeHoldingId));
        LambdaQueryWrapper<AiAnalysisReport> wrapper = new LambdaQueryWrapper<AiAnalysisReport>()
                .eq(AiAnalysisReport::getUserId, userId)
                .in(AiAnalysisReport::getHoldingId, activeHoldingIds);
        if (accountId != null) {
            loadOwnedAccount(userId, accountId);
            wrapper.eq(AiAnalysisReport::getAccountId, accountId);
        }
        if (holdingId != null) {
            loadOwnedHolding(userId, holdingId);
            wrapper.eq(AiAnalysisReport::getHoldingId, holdingId);
        }
        if (StringUtils.hasText(fundCode)) {
            wrapper.eq(AiAnalysisReport::getFundCode, fundCode.trim());
        }
        wrapper.orderByDesc(AiAnalysisReport::getAnalysisTime).orderByDesc(AiAnalysisReport::getId);
        return aiAnalysisReportMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAnalysisReportVO regenerate(Long reportId) {
        Long userId = UserContext.getUserId();
        AiAnalysisReport report = aiAnalysisReportMapper.selectOne(new LambdaQueryWrapper<AiAnalysisReport>()
                .eq(AiAnalysisReport::getId, reportId)
                .eq(AiAnalysisReport::getUserId, userId)
                .last("LIMIT 1"));
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI analysis report not found");
        }
        if (report.getHoldingId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "holding id is required to regenerate report");
        }
        return analyzeHolding(report.getHoldingId());
    }

    private AiAnalysisContext buildContext(Long userId, PortfolioAccount account, FundHolding holding, QuantSignalVO quantSignal) {
        List<StrategySignalVO> signals = strategySignalMapper.selectList(new LambdaQueryWrapper<StrategySignal>()
                        .eq(StrategySignal::getUserId, userId)
                        .eq(StrategySignal::getHoldingId, holding.getId())
                        .orderByDesc(StrategySignal::getSignalTime)
                        .last("LIMIT 20"))
                .stream()
                .sorted(Comparator
                        .comparing((StrategySignal signal) -> SignalType.QUANT_MODEL.name().equals(signal.getSignalType()))
                        .reversed()
                        .thenComparing(StrategySignal::getSignalTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(this::toStrategySignalVO)
                .toList();
        RiskProfile riskProfile = riskProfileMapper.selectOne(new LambdaQueryWrapper<RiskProfile>()
                .eq(RiskProfile::getUserId, userId)
                .last("LIMIT 1"));
        BigDecimal totalAsset = valueOrZero(account.getTotalAsset());
        BigDecimal holdingAmount = valueOrZero(holding.getHoldingAmount());
        BigDecimal singleRate = totalAsset.compareTo(BigDecimal.ZERO) > 0
                ? holdingAmount.multiply(new BigDecimal("100.0000")).divide(totalAsset, 4, RoundingMode.HALF_UP)
                : ZERO;
        return new AiAnalysisContext(
                account.getId(),
                holding.getId(),
                holding.getFundCode(),
                holding.getFundName(),
                holding.getFundType(),
                holding.getActiveFund() != null && holding.getActiveFund() == 1,
                holding.getCurrentEstimateNav(),
                holding.getLatestOfficialNav(),
                holdingAmount,
                valueOrZero(holding.getHoldingShare()),
                valueOrZero(holding.getHoldingCost()),
                valueOrZero(holding.getHoldingProfit()),
                valueOrZero(holding.getHoldingProfitRate()),
                valueOrZero(holding.getDailyProfit()),
                holding.getHoldingDays(),
                totalAsset,
                valueOrZero(account.getEquityPositionRate()),
                singleRate,
                riskProfile == null ? ZERO : valueOrZero(riskProfile.getMaxEquityPositionRate()),
                riskProfile == null ? ZERO : valueOrZero(riskProfile.getMaxSingleFundPositionRate()),
                riskProfile == null ? ZERO : valueOrZero(riskProfile.getDailyRiseAlertRate()),
                riskProfile == null ? ZERO : valueOrZero(riskProfile.getDrawdownAlertRate()),
                riskProfile == null ? RiskLevel.MEDIUM.name() : riskProfile.getRiskLevel(),
                quantSignal,
                signals
        );
    }

    private AiAnalysisResult alignWithQuantSignal(AiAnalysisResult result, QuantSignalVO quantSignal) {
        if (quantSignal == null) {
            return result;
        }
        StrategyAction quantAction = parseAction(quantSignal.action());
        RiskLevel quantRiskLevel = parseRiskLevel(quantSignal.riskLevel());
        List<String> reasons = mergeText(
                List.of("AI 报告以最新量化建议为准：" + quantSignal.actionText()),
                quantSignal.reasons(),
                result.reasons()
        );
        List<String> risks = mergeText(quantSignal.risks(), result.risks());
        String dataSummary = result.dataSummary();
        if (!StringUtils.hasText(dataSummary)) {
            dataSummary = "量化评分 " + scale(quantSignal.totalScore()) + "，模型版本 " + quantSignal.modelVersion();
        }
        return new AiAnalysisResult(
                quantAction,
                quantSignal.actionText(),
                scale(quantSignal.suggestAmount()),
                scale(quantSignal.suggestRatio()),
                scale(quantSignal.confidence()),
                quantRiskLevel,
                StringUtils.hasText(quantSignal.deadline()) ? quantSignal.deadline() : result.deadline(),
                StringUtils.hasText(result.strategy()) ? result.strategy() : "AI 解释量化建议",
                reasons,
                risks,
                dataSummary,
                "以最新量化建议为准：" + quantSignal.actionText() + "。AI 仅解释该量化结果，不覆盖模型动作。",
                result.fallbackUsed(),
                result.rawResponse()
        );
    }

    private StrategyAction parseAction(String action) {
        try {
            return StrategyAction.valueOf(action);
        } catch (RuntimeException exception) {
            return StrategyAction.WATCH;
        }
    }

    private RiskLevel parseRiskLevel(String riskLevel) {
        try {
            return RiskLevel.valueOf(riskLevel);
        } catch (RuntimeException exception) {
            return RiskLevel.MEDIUM;
        }
    }

    @SafeVarargs
    private final List<String> mergeText(List<String>... groups) {
        List<String> result = new java.util.ArrayList<>();
        for (List<String> group : groups) {
            if (group == null) {
                continue;
            }
            for (String item : group) {
                if (StringUtils.hasText(item) && !result.contains(item)) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private AiAnalysisReport saveReport(Long userId, AiAnalysisContext context, AiAnalysisResult result) {
        LocalDateTime now = LocalDateTime.now();
        AiAnalysisReport report = new AiAnalysisReport();
        report.setUserId(userId);
        report.setAccountId(context.accountId());
        report.setHoldingId(context.holdingId());
        report.setFundCode(context.fundCode());
        report.setModelName(limitText(properties.getAi().getModel(), MODEL_NAME_MAX_LENGTH));
        report.setAction(result.action().name());
        report.setActionText(limitText(result.actionText(), ACTION_TEXT_MAX_LENGTH));
        report.setSuggestAmount(scale(result.suggestAmount()));
        report.setSuggestRatio(scale(result.suggestRatio()));
        report.setConfidence(scale(result.confidence()));
        report.setRiskLevel(result.riskLevel().name());
        report.setDeadline(limitText(result.deadline(), DEADLINE_MAX_LENGTH));
        report.setStrategy(limitText(result.strategy(), STRATEGY_MAX_LENGTH));
        report.setReasonsJson(writeJson(result.reasons()));
        report.setRisksJson(writeJson(result.risks()));
        report.setDataSummary(result.dataSummary());
        report.setFinalConclusion(result.finalConclusion());
        report.setRequestPayload(writeJson(context));
        report.setResponsePayload(normalizeResponsePayload(result.rawResponse()));
        report.setFallbackUsed(result.fallbackUsed() ? 1 : 0);
        report.setAnalysisTime(now);
        report.setCreateTime(now);
        report.setUpdateTime(now);
        report.setDeleted(0);
        aiAnalysisReportMapper.insert(report);
        pruneHistory(userId, context.holdingId());
        return report;
    }

    private void pruneHistory(Long userId, Long holdingId) {
        List<Long> expiredIds = aiAnalysisReportMapper.selectList(new LambdaQueryWrapper<AiAnalysisReport>()
                        .select(AiAnalysisReport::getId)
                        .eq(AiAnalysisReport::getUserId, userId)
                        .eq(AiAnalysisReport::getHoldingId, holdingId)
                        .orderByDesc(AiAnalysisReport::getAnalysisTime)
                        .orderByDesc(AiAnalysisReport::getId))
                .stream()
                .skip(AI_HISTORY_LIMIT)
                .map(AiAnalysisReport::getId)
                .toList();
        if (expiredIds.isEmpty()) {
            return;
        }
        aiAnalysisReportMapper.delete(new LambdaQueryWrapper<AiAnalysisReport>()
                .eq(AiAnalysisReport::getUserId, userId)
                .in(AiAnalysisReport::getId, expiredIds));
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

    private StrategySignalVO toStrategySignalVO(StrategySignal signal) {
        return new StrategySignalVO(
                signal.getId(),
                signal.getAccountId(),
                signal.getHoldingId(),
                signal.getFundCode(),
                fundName(signal),
                signal.getSignalType(),
                signal.getAction(),
                signal.getActionText(),
                signal.getSuggestAmount(),
                signal.getSuggestRatio(),
                signal.getRiskLevel(),
                signal.getConfidence(),
                readStringList(signal.getReasonJson()),
                signal.getSignalTime(),
                SystemConstants.DISCLAIMER
        );
    }

    private String fundName(StrategySignal signal) {
        FundHolding holding = signal.getHoldingId() == null ? null : fundHoldingMapper.selectById(signal.getHoldingId());
        if (holding != null && StringUtils.hasText(holding.getFundName())) {
            return holding.getFundName();
        }
        return signal.getFundCode();
    }

    private AiAnalysisReportVO toVO(AiAnalysisReport report) {
        return new AiAnalysisReportVO(
                report.getId(),
                report.getAccountId(),
                report.getHoldingId(),
                report.getFundCode(),
                fundName(report.getHoldingId(), report.getFundCode()),
                report.getModelName(),
                report.getAction(),
                report.getActionText(),
                report.getSuggestAmount(),
                report.getSuggestRatio(),
                report.getConfidence(),
                report.getRiskLevel(),
                report.getDeadline(),
                report.getStrategy(),
                readStringList(report.getReasonsJson()),
                readStringList(report.getRisksJson()),
                report.getDataSummary(),
                report.getFinalConclusion(),
                fallbackUsed(report),
                report.getAnalysisTime(),
                SystemConstants.DISCLAIMER
        );
    }

    private String fundName(Long holdingId, String fundCode) {
        FundHolding holding = holdingId == null ? null : fundHoldingMapper.selectById(holdingId);
        if (holding != null && StringUtils.hasText(holding.getFundName())) {
            return holding.getFundName();
        }
        return fundCode;
    }

    private boolean fallbackUsed(AiAnalysisReport report) {
        if (report.getFallbackUsed() != null && report.getFallbackUsed() == 1) {
            return true;
        }
        String strategy = report.getStrategy() == null ? "" : report.getStrategy();
        String reasons = report.getReasonsJson() == null ? "" : report.getReasonsJson();
        String summary = report.getDataSummary() == null ? "" : report.getDataSummary();
        return strategy.contains("Mock AI")
                || reasons.contains("mock")
                || reasons.contains("未配置真实 Key")
                || summary.contains("AI 分析不可用");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String normalizeResponsePayload(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(rawResponse));
        } catch (JsonProcessingException exception) {
            return writeJson(Map.of("rawResponse", rawResponse));
        }
    }

    private String readableMessage(RuntimeException exception) {
        if (StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        if (exception.getCause() != null && StringUtils.hasText(exception.getCause().getMessage())) {
            return exception.getCause().getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
