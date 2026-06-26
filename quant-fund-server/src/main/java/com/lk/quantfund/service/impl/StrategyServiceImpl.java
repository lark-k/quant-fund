package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.strategy.RiskProfileRequest;
import com.lk.quantfund.dto.strategy.StrategyConfigRequest;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.RiskProfile;
import com.lk.quantfund.entity.StrategyConfig;
import com.lk.quantfund.entity.StrategySignal;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.enums.StrategyType;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.RiskProfileMapper;
import com.lk.quantfund.mapper.StrategyConfigMapper;
import com.lk.quantfund.mapper.StrategySignalMapper;
import com.lk.quantfund.service.StrategyService;
import com.lk.quantfund.strategy.FundClassificationService;
import com.lk.quantfund.strategy.QuantStrategyRule;
import com.lk.quantfund.strategy.context.StrategyEvaluationContext;
import com.lk.quantfund.strategy.context.StrategySignalDraft;
import com.lk.quantfund.vo.strategy.RiskProfileVO;
import com.lk.quantfund.vo.strategy.StrategyAnalysisVO;
import com.lk.quantfund.vo.strategy.StrategyConfigVO;
import com.lk.quantfund.vo.strategy.StrategySignalVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StrategyServiceImpl implements StrategyService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final List<QuantStrategyRule> rules;
    private final FundClassificationService classificationService;
    private final ObjectMapper objectMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final FundNavDailyMapper fundNavDailyMapper;
    private final StrategyConfigMapper strategyConfigMapper;
    private final StrategySignalMapper strategySignalMapper;
    private final RiskProfileMapper riskProfileMapper;

    public StrategyServiceImpl(List<QuantStrategyRule> rules,
                               FundClassificationService classificationService,
                               ObjectMapper objectMapper,
                               FundHoldingMapper fundHoldingMapper,
                               PortfolioAccountMapper portfolioAccountMapper,
                               FundNavDailyMapper fundNavDailyMapper,
                               StrategyConfigMapper strategyConfigMapper,
                               StrategySignalMapper strategySignalMapper,
                               RiskProfileMapper riskProfileMapper) {
        this.rules = rules;
        this.classificationService = classificationService;
        this.objectMapper = objectMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.strategyConfigMapper = strategyConfigMapper;
        this.strategySignalMapper = strategySignalMapper;
        this.riskProfileMapper = riskProfileMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyAnalysisVO analyzeHolding(Long holdingId) {
        return analyzeHoldingForUser(UserContext.getUserId(), holdingId);
    }

    @Override
    public StrategyAnalysisVO analyzeHoldingForUser(Long userId, Long holdingId) {
        FundHolding holding = loadOwnedHolding(userId, holdingId);
        PortfolioAccount account = loadOwnedAccount(userId, holding.getAccountId());
        RiskProfile riskProfile = loadOrCreateRiskProfile(userId);
        StrategyEvaluationContext context = buildContext(userId, account, holding, riskProfile);
        Set<StrategyType> enabledTypes = enabledStrategyTypes(userId);
        List<StrategySignalVO> signals = rules.stream()
                .filter(rule -> enabledTypes.contains(rule.strategyType()))
                .filter(rule -> rule.supports(context))
                .flatMap(rule -> rule.evaluate(context).stream())
                .map(draft -> saveSignal(userId, account, holding, draft))
                .toList();
        if (signals.isEmpty() && !enabledTypes.isEmpty()) {
            StrategySignalDraft watch = new StrategySignalDraft(
                    com.lk.quantfund.enums.SignalType.WATCH,
                    StrategyAction.HOLD,
                    "建议持有观察",
                    ZERO,
                    ZERO,
                    RiskLevel.LOW,
                    new BigDecimal("0.6000"),
                    List.of("当前未触发加仓、减仓或高风险策略条件", "仅供参考，继续观察估值、正式净值和账户仓位")
            );
            signals = List.of(saveSignal(userId, account, holding, watch));
        }
        return new StrategyAnalysisVO(
                account.getId(),
                holding.getId(),
                holding.getFundCode(),
                holding.getFundName(),
                context.classifiedFundType(),
                signals,
                SystemConstants.DISCLAIMER
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<StrategyAnalysisVO> analyzeAccount(Long accountId) {
        Long userId = UserContext.getUserId();
        loadOwnedAccount(userId, accountId);
        return fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getUserId, userId)
                        .eq(FundHolding::getAccountId, accountId)
                        .orderByDesc(FundHolding::getHoldingAmount))
                .stream()
                .map(holding -> analyzeHolding(holding.getId()))
                .toList();
    }

    @Override
    public List<StrategySignalVO> listSignals(Long accountId, Long holdingId, String fundCode, StrategyAction action) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<StrategySignal> wrapper = new LambdaQueryWrapper<StrategySignal>()
                .eq(StrategySignal::getUserId, userId);
        if (accountId != null) {
            loadOwnedAccount(userId, accountId);
            wrapper.eq(StrategySignal::getAccountId, accountId);
        }
        if (holdingId != null) {
            loadOwnedHolding(userId, holdingId);
            wrapper.eq(StrategySignal::getHoldingId, holdingId);
        }
        if (StringUtils.hasText(fundCode)) {
            wrapper.eq(StrategySignal::getFundCode, fundCode.trim());
        }
        if (action != null) {
            wrapper.eq(StrategySignal::getAction, action.name());
        }
        wrapper.orderByDesc(StrategySignal::getSignalTime);
        return strategySignalMapper.selectList(wrapper).stream().map(this::toSignalVO).toList();
    }

    @Override
    public List<StrategyConfigVO> listConfigs() {
        Long userId = UserContext.getUserId();
        ensureDefaultConfigs(userId);
        return strategyConfigMapper.selectList(new LambdaQueryWrapper<StrategyConfig>()
                        .eq(StrategyConfig::getUserId, userId)
                        .orderByAsc(StrategyConfig::getStrategyType))
                .stream()
                .map(this::toConfigVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyConfigVO saveConfig(StrategyConfigRequest request) {
        validateJson(request.paramsJson());
        Long userId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        StrategyConfig config = strategyConfigMapper.selectOne(new LambdaQueryWrapper<StrategyConfig>()
                .eq(StrategyConfig::getUserId, userId)
                .eq(StrategyConfig::getStrategyType, request.strategyType().name())
                .eq(request.fundType() != null, StrategyConfig::getFundType, request.fundType() == null ? null : request.fundType().name())
                .isNull(request.fundType() == null, StrategyConfig::getFundType)
                .last("LIMIT 1"));
        if (config == null) {
            config = new StrategyConfig();
            config.setUserId(userId);
            config.setStrategyType(request.strategyType().name());
            config.setFundType(request.fundType() == null ? null : request.fundType().name());
            config.setCreateTime(now);
            config.setDeleted(0);
        }
        config.setConfigName(request.configName().trim());
        config.setParamsJson(request.paramsJson());
        config.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        config.setUpdateTime(now);
        if (config.getId() == null) {
            strategyConfigMapper.insert(config);
        } else {
            strategyConfigMapper.updateById(config);
        }
        syncRiskProfileFromStrategyConfig(userId, request.paramsJson());
        refreshTodaySignalsAfterConfigChange(userId);
        return toConfigVO(config);
    }

    @Override
    public RiskProfileVO getRiskProfile() {
        return toRiskProfileVO(loadOrCreateRiskProfile(UserContext.getUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiskProfileVO updateRiskProfile(RiskProfileRequest request) {
        validateJsonIfPresent(request.configJson());
        Long userId = UserContext.getUserId();
        RiskProfile profile = loadOrCreateRiskProfile(userId);
        profile.setRiskLevel(request.riskLevel().name());
        profile.setMaxEquityPositionRate(scale(request.maxEquityPositionRate()));
        profile.setMaxSingleFundPositionRate(scale(request.maxSingleFundPositionRate()));
        profile.setDrawdownAlertRate(scale(request.drawdownAlertRate()));
        profile.setDailyRiseAlertRate(scale(request.dailyRiseAlertRate()));
        profile.setDailyFallAlertRate(scale(request.dailyFallAlertRate()));
        profile.setConfigJson(request.configJson());
        profile.setUpdateTime(LocalDateTime.now());
        riskProfileMapper.updateById(profile);
        syncPositionConfigFromRiskProfile(userId, profile);
        refreshTodaySignalsAfterConfigChange(userId);
        return toRiskProfileVO(profile);
    }

    private StrategyEvaluationContext buildContext(Long userId, PortfolioAccount account, FundHolding holding, RiskProfile riskProfile) {
        List<FundNavDaily> navList = fundNavDailyMapper.selectList(new LambdaQueryWrapper<FundNavDaily>()
                .eq(FundNavDaily::getFundCode, holding.getFundCode())
                .ge(FundNavDaily::getNavDate, LocalDate.now().minusDays(120))
                .orderByAsc(FundNavDaily::getNavDate));
        BigDecimal currentNav = holding.getCurrentEstimateNav() != null ? holding.getCurrentEstimateNav() : holding.getLatestOfficialNav();
        BigDecimal highNav = navList.stream()
                .map(FundNavDaily::getUnitNav)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .max(BigDecimal::compareTo)
                .orElse(currentNav);
        BigDecimal drawdown = currentNav != null && highNav != null && highNav.compareTo(BigDecimal.ZERO) > 0
                ? highNav.subtract(currentNav).multiply(new BigDecimal("100.0000")).divide(highNav, 4, RoundingMode.HALF_UP).max(ZERO)
                : ZERO;
        String classified = classificationService.classify(
                holding.getFundName(),
                holding.getFundType(),
                holding.getActiveFund() != null && holding.getActiveFund() == 1,
                null
        );
        return new StrategyEvaluationContext(userId, account, holding, riskProfile, navList, currentNav, highNav, drawdown, classified);
    }

    private StrategySignalVO saveSignal(Long userId, PortfolioAccount account, FundHolding holding, StrategySignalDraft draft) {
        LocalDateTime now = LocalDateTime.now();
        StrategySignal signal = strategySignalMapper.selectOne(new LambdaQueryWrapper<StrategySignal>()
                .eq(StrategySignal::getUserId, userId)
                .eq(StrategySignal::getHoldingId, holding.getId())
                .eq(StrategySignal::getSignalType, draft.signalType().name())
                .eq(StrategySignal::getAction, draft.action().name())
                .eq(StrategySignal::getActionText, draft.actionText())
                .ge(StrategySignal::getSignalTime, now.toLocalDate().atStartOfDay())
                .orderByDesc(StrategySignal::getSignalTime)
                .last("LIMIT 1"));
        if (signal == null) {
            signal = new StrategySignal();
            signal.setUserId(userId);
            signal.setAccountId(account.getId());
            signal.setHoldingId(holding.getId());
            signal.setFundCode(holding.getFundCode());
            signal.setCreateTime(now);
            signal.setDeleted(0);
        }
        signal.setSignalType(draft.signalType().name());
        signal.setAction(draft.action().name());
        signal.setActionText(draft.actionText());
        signal.setSuggestAmount(scale(draft.suggestAmount()));
        signal.setSuggestRatio(scale(draft.suggestRatio()));
        signal.setRiskLevel(draft.riskLevel().name());
        signal.setConfidence(scale(draft.confidence()));
        signal.setReasonJson(writeReasons(draft.reasons()));
        signal.setSignalTime(now);
        signal.setUpdateTime(now);
        if (signal.getId() == null) {
            strategySignalMapper.insert(signal);
        } else {
            strategySignalMapper.updateById(signal);
        }
        return toSignalVO(signal);
    }

    private Set<StrategyType> enabledStrategyTypes(Long userId) {
        ensureDefaultConfigs(userId);
        List<StrategyConfig> configs = strategyConfigMapper.selectList(new LambdaQueryWrapper<StrategyConfig>()
                .eq(StrategyConfig::getUserId, userId)
                .eq(StrategyConfig::getEnabled, 1));
        EnumSet<StrategyType> enabled = EnumSet.noneOf(StrategyType.class);
        for (StrategyConfig config : configs) {
            enabled.add(StrategyType.valueOf(config.getStrategyType()));
        }
        return enabled;
    }

    private void ensureDefaultConfigs(Long userId) {
        if (strategyConfigMapper.selectCount(new LambdaQueryWrapper<StrategyConfig>().eq(StrategyConfig::getUserId, userId)) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (StrategyType type : StrategyType.values()) {
            StrategyConfig config = new StrategyConfig();
            config.setUserId(userId);
            config.setConfigName(defaultName(type));
            config.setStrategyType(type.name());
            config.setFundType(null);
            config.setParamsJson("{}");
            config.setEnabled(1);
            config.setCreateTime(now);
            config.setUpdateTime(now);
            config.setDeleted(0);
            strategyConfigMapper.insert(config);
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

    private void syncRiskProfileFromStrategyConfig(Long userId, String paramsJson) {
        JsonNode params = readParams(paramsJson);
        boolean changed = false;
        RiskProfile profile = loadOrCreateRiskProfile(userId);
        if (params.hasNonNull("equityLimitPct")) {
            profile.setMaxEquityPositionRate(rateParam(params, "equityLimitPct"));
            changed = true;
        }
        if (params.hasNonNull("singleFundLimitPct")) {
            profile.setMaxSingleFundPositionRate(rateParam(params, "singleFundLimitPct"));
            changed = true;
        }
        if (params.hasNonNull("largeRisePct")) {
            profile.setDailyRiseAlertRate(rateParam(params, "largeRisePct"));
            changed = true;
        }
        if (params.hasNonNull("heavyDrawdownPct")) {
            profile.setDrawdownAlertRate(rateParam(params, "heavyDrawdownPct"));
            changed = true;
        }
        if (changed) {
            profile.setUpdateTime(LocalDateTime.now());
            riskProfileMapper.updateById(profile);
        }
    }

    private void syncPositionConfigFromRiskProfile(Long userId, RiskProfile profile) {
        StrategyConfig config = strategyConfigMapper.selectOne(new LambdaQueryWrapper<StrategyConfig>()
                .eq(StrategyConfig::getUserId, userId)
                .eq(StrategyConfig::getStrategyType, StrategyType.POSITION_MONITOR.name())
                .isNull(StrategyConfig::getFundType)
                .last("LIMIT 1"));
        if (config == null) {
            return;
        }
        JsonNode params = readParams(StringUtils.hasText(config.getParamsJson()) ? config.getParamsJson() : "{}");
        ObjectNode merged = params != null && params.isObject()
                ? (ObjectNode) params.deepCopy()
                : objectMapper.createObjectNode();
        merged.put("equityLimitPct", scale(profile.getMaxEquityPositionRate()));
        merged.put("singleFundLimitPct", scale(profile.getMaxSingleFundPositionRate()));
        merged.put("largeRisePct", scale(profile.getDailyRiseAlertRate()));
        try {
            config.setParamsJson(objectMapper.writeValueAsString(merged));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "strategy params must be valid JSON");
        }
        config.setUpdateTime(LocalDateTime.now());
        strategyConfigMapper.updateById(config);
    }

    private void refreshTodaySignalsAfterConfigChange(Long userId) {
        strategySignalMapper.delete(new LambdaQueryWrapper<StrategySignal>()
                .eq(StrategySignal::getUserId, userId)
                .ge(StrategySignal::getSignalTime, LocalDate.now().atStartOfDay()));
        fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                        .eq(FundHolding::getUserId, userId)
                        .orderByAsc(FundHolding::getFundCode))
                .forEach(holding -> analyzeHoldingForUser(userId, holding.getId()));
    }

    private JsonNode readParams(String paramsJson) {
        try {
            return objectMapper.readTree(paramsJson);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "strategy params must be valid JSON");
        }
    }

    private BigDecimal rateParam(JsonNode params, String field) {
        BigDecimal value;
        try {
            value = new BigDecimal(params.get(field).asText());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "strategy rate param must be numeric: " + field);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100.0000")) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "strategy rate param out of range: " + field);
        }
        return scale(value);
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

    private StrategyConfigVO toConfigVO(StrategyConfig config) {
        return new StrategyConfigVO(
                config.getId(),
                config.getConfigName(),
                config.getStrategyType(),
                config.getFundType(),
                config.getParamsJson(),
                config.getEnabled() != null && config.getEnabled() == 1,
                config.getUpdateTime()
        );
    }

    private RiskProfileVO toRiskProfileVO(RiskProfile profile) {
        return new RiskProfileVO(
                profile.getId(),
                profile.getRiskLevel(),
                profile.getMaxEquityPositionRate(),
                profile.getMaxSingleFundPositionRate(),
                profile.getDrawdownAlertRate(),
                profile.getDailyRiseAlertRate(),
                profile.getDailyFallAlertRate(),
                profile.getConfigJson(),
                profile.getUpdateTime()
        );
    }

    private StrategySignalVO toSignalVO(StrategySignal signal) {
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
                readReasons(signal.getReasonJson()),
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

    private String writeReasons(List<String> reasons) {
        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<String> readReasons(String reasonJson) {
        try {
            return objectMapper.readValue(reasonJson, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void validateJson(String json) {
        try {
            objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "strategy params must be valid JSON");
        }
    }

    private void validateJsonIfPresent(String json) {
        if (StringUtils.hasText(json)) {
            validateJson(json);
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private String defaultName(StrategyType type) {
        return switch (type) {
            case FUND_CLASSIFICATION -> "基金分类策略";
            case DRAWDOWN_STOP_PROFIT -> "回撤止盈法";
            case DYNAMIC_LADDER_STOP_PROFIT -> "动态梯度止盈法";
            case FIXED_BATCH_STOP_PROFIT -> "固定分批止盈";
            case POSITION_MONITOR -> "账户仓位监控";
            case BUY_DIP -> "回撤低吸规则";
            case RISK_ALERT -> "风险提示规则";
        };
    }
}
