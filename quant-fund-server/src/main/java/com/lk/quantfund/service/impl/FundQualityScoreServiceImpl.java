package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.common.PageResponse;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.screener.FundScreenerQueryRequest;
import com.lk.quantfund.entity.ScreenerFactorSnapshot;
import com.lk.quantfund.entity.ScreenerFundUniverse;
import com.lk.quantfund.entity.ScreenerQualityScore;
import com.lk.quantfund.mapper.ScreenerFactorSnapshotMapper;
import com.lk.quantfund.mapper.ScreenerFundUniverseMapper;
import com.lk.quantfund.mapper.ScreenerQualityScoreMapper;
import com.lk.quantfund.service.FundQualityScoreService;
import com.lk.quantfund.vo.screener.FundScreenerExplainVO;
import com.lk.quantfund.vo.screener.FundScreenerRankItemVO;
import com.lk.quantfund.vo.screener.FundScreenerScoreBreakdownVO;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FundQualityScoreServiceImpl implements FundQualityScoreService {

    private static final String MODEL_VERSION = "screener-rule-v2";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");
    private static final String REASON_RETURN_120D = "\u8fd1120\u65e5\u6536\u76ca\u8868\u73b0\u8f83\u597d";
    private static final String REASON_DRAWDOWN = "\u8fd1120\u65e5\u6700\u5927\u56de\u64a4\u5904\u4e8e\u53ef\u63a7\u533a\u95f4";
    private static final String REASON_SAMPLE = "\u51c0\u503c\u6837\u672c\u5145\u8db3\uff0c\u8bc4\u5206\u53ef\u4fe1\u5ea6\u8f83\u9ad8";
    private static final String RISK_DISCLAIMER = "\u57fa\u91d1\u4f18\u9009\u7ed3\u679c\u4ec5\u4f9b\u53c2\u8003\uff0c\u4e0d\u6784\u6210\u6295\u8d44\u5efa\u8bae";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ScreenerFactorSnapshotMapper screenerFactorSnapshotMapper;
    private final ScreenerQualityScoreMapper screenerQualityScoreMapper;
    private final ScreenerFundUniverseMapper screenerFundUniverseMapper;
    private final ObjectMapper objectMapper;
    private final QuantFundProperties.ScreenerStrategy screenerStrategy;

    @Autowired
    public FundQualityScoreServiceImpl(ScreenerFactorSnapshotMapper screenerFactorSnapshotMapper,
                                       ScreenerQualityScoreMapper screenerQualityScoreMapper,
                                       ScreenerFundUniverseMapper screenerFundUniverseMapper,
                                       ObjectMapper objectMapper,
                                       QuantFundProperties properties) {
        this.screenerFactorSnapshotMapper = screenerFactorSnapshotMapper;
        this.screenerQualityScoreMapper = screenerQualityScoreMapper;
        this.screenerFundUniverseMapper = screenerFundUniverseMapper;
        this.objectMapper = objectMapper;
        this.screenerStrategy = properties.getScreenerStrategy();
    }

    FundQualityScoreServiceImpl(ScreenerFactorSnapshotMapper screenerFactorSnapshotMapper,
                                ScreenerQualityScoreMapper screenerQualityScoreMapper,
                                ScreenerFundUniverseMapper screenerFundUniverseMapper,
                                ObjectMapper objectMapper) {
        this(screenerFactorSnapshotMapper, screenerQualityScoreMapper, screenerFundUniverseMapper,
                objectMapper, new QuantFundProperties());
    }

    @Override
    public PageResponse<FundScreenerRankItemVO> rank(FundScreenerQueryRequest request) {
        List<ScreenerQualityScore> scores = screenerQualityScoreMapper.selectList(new LambdaQueryWrapper<ScreenerQualityScore>()
                .orderByDesc(ScreenerQualityScore::getQualityScore));
        List<ScreenerQualityScore> latestScores = latestScoresByCode(scores).values().stream().toList();
        Map<String, ScreenerFundUniverse> universeByCode = universesByCode();
        Map<String, ScreenerFactorSnapshot> latestFactorByCode = latestFactorsByCode();
        List<FundScreenerRankItemVO> records = latestScores.stream()
                .map(score -> new RankSource(score, universeByCode.get(score.getFundCode()), latestFactorByCode.get(score.getFundCode())))
                .filter(source -> matches(request, source))
                .map(source -> toRankItem(source.score(), source.universe(), source.factor()))
                .sorted(rankComparator(request.sortBy()))
                .toList();
        int fromIndex = (int) Math.min(records.size(), (request.pageNo() - 1) * request.pageSize());
        int toIndex = (int) Math.min(records.size(), fromIndex + request.pageSize());
        return PageResponse.of(request.pageNo(), request.pageSize(), records.size(), records.subList(fromIndex, toIndex));
    }

    private Map<String, ScreenerFundUniverse> universesByCode() {
        return screenerFundUniverseMapper.selectList(new LambdaQueryWrapper<ScreenerFundUniverse>())
                .stream()
                .filter(universe -> StringUtils.hasText(universe.getFundCode()))
                .collect(Collectors.toMap(ScreenerFundUniverse::getFundCode, Function.identity(), (left, right) -> left));
    }

    private Map<String, ScreenerQualityScore> latestScoresByCode(List<ScreenerQualityScore> scores) {
        Map<String, ScreenerQualityScore> latest = new LinkedHashMap<>();
        for (ScreenerQualityScore score : scores) {
            if (!StringUtils.hasText(score.getFundCode())) {
                continue;
            }
            ScreenerQualityScore existing = latest.get(score.getFundCode());
            if (existing == null || isAfter(score, existing)) {
                latest.put(score.getFundCode(), score);
            }
        }
        return latest;
    }

    private boolean isAfter(ScreenerQualityScore candidate, ScreenerQualityScore existing) {
        if (candidate.getScoreDate() == null) {
            return false;
        }
        if (existing.getScoreDate() == null) {
            return true;
        }
        return candidate.getScoreDate().isAfter(existing.getScoreDate());
    }

    private Map<String, ScreenerFactorSnapshot> latestFactorsByCode() {
        Map<String, ScreenerFactorSnapshot> factors = new LinkedHashMap<>();
        screenerFactorSnapshotMapper.selectList(new LambdaQueryWrapper<ScreenerFactorSnapshot>()
                        .orderByDesc(ScreenerFactorSnapshot::getFactorDate))
                .stream()
                .filter(factor -> StringUtils.hasText(factor.getFundCode()))
                .forEach(factor -> factors.putIfAbsent(factor.getFundCode(), factor));
        return factors;
    }

    @Override
    public FundScreenerExplainVO explain(String fundCode) {
        ScreenerQualityScore score = findScore(fundCode);
        if (score == null) {
            return FundScreenerExplainVO.empty(fundCode);
        }
        ScreenerFundUniverse universe = findUniverse(fundCode);
        ScreenerFactorSnapshot factor = findFactor(fundCode);
        return new FundScreenerExplainVO(
                fundCode,
                universe == null ? null : universe.getFundName(),
                universe == null ? null : universe.getFundType(),
                toDouble(score.getQualityScore()),
                score.getRecommendLevel(),
                scoreBreakdown(score),
                factorMap(factor),
                readStringList(score.getReasonsJson()),
                readStringList(score.getRisksJson()),
                score.getScoreDate() == null ? null : score.getScoreDate().toString(),
                score.getModelVersion(),
                SystemConstants.DISCLAIMER
        );
    }

    @Override
    public FundScreenerTaskResultVO refreshScore() {
        long started = System.currentTimeMillis();
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        List<ScreenerFactorSnapshot> factors = latestFactorsByCode().values().stream()
                .sorted(Comparator.comparing(ScreenerFactorSnapshot::getFundCode))
                .toList();
        Map<String, ScreenerFundUniverse> universeByCode = universesByCode();
        Map<String, ScreenerQualityScore> existingScoreByKey = existingScoresByKey();
        List<ScreenerQualityScore> calculated = new ArrayList<>();
        for (ScreenerFactorSnapshot factor : factors) {
            try {
                ScreenerFundUniverse universe = universeByCode.get(factor.getFundCode());
                calculated.add(calculate(factor, universe));
                success++;
            } catch (RuntimeException exception) {
                failed++;
                errors.add(factor.getFundCode() + ": " + exception.getMessage());
            }
        }
        calculated.sort(Comparator.comparing(ScreenerQualityScore::getQualityScore, Comparator.nullsLast(BigDecimal::compareTo)).reversed());
        for (int index = 0; index < calculated.size(); index++) {
            ScreenerQualityScore score = calculated.get(index);
            score.setRankNo(index + 1);
            score.setRankPercentile(BigDecimal.valueOf(index + 1L)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(calculated.size()), 4, RoundingMode.HALF_UP));
            score.setRecommendLevel(recommendLevel(score.getQualityScore(), score.getRankNo(), calculated.size()));
            upsert(score, existingScoreByKey);
        }
        return new FundScreenerTaskResultVO(
                "REFRESH_SCORE",
                failed == 0 ? "SUCCESS" : success > 0 ? "PARTIAL_SUCCESS" : "FAILED",
                success,
                failed,
                0,
                System.currentTimeMillis() - started,
                errors,
                failed == 0 ? "\u57fa\u91d1\u4f18\u9009\u8bc4\u5206\u5237\u65b0\u5b8c\u6210" : "\u57fa\u91d1\u4f18\u9009\u8bc4\u5206\u90e8\u5206\u5931\u8d25",
                LocalDateTime.now()
        );
    }

    private ScreenerQualityScore calculate(ScreenerFactorSnapshot factor, ScreenerFundUniverse universe) {
        BigDecimal returnQualityScore = returnQualityScore(factor);
        BigDecimal drawdownControlScore = drawdownControlScore(factor);
        BigDecimal consistencyScore = consistencyScore(factor);
        BigDecimal investabilityScore = investabilityScore(factor, universe);
        BigDecimal returnScore = returnQualityScore;
        BigDecimal riskScore = drawdownControlScore;
        BigDecimal stabilityScore = consistencyScore;
        BigDecimal excessScore = clip(new BigDecimal("50.0000").add(valueOrZero(factor.getExcessReturn120d()).multiply(new BigDecimal("4.0000"))));
        BigDecimal peerScore = clip(HUNDRED.subtract(valueOrDefault(factor.getPeerPercentile(), new BigDecimal("50.0000"))));
        BigDecimal liquidityScore = clip(valueOrDefault(factor.getFundSize(), new BigDecimal("25.0000")).multiply(new BigDecimal("2.0000")));
        BigDecimal dataScore = clip(BigDecimal.valueOf(factor.getNavSampleSize() == null ? 0 : factor.getNavSampleSize())
                .multiply(HUNDRED)
                .divide(new BigDecimal("250.0000"), 4, RoundingMode.HALF_UP));

        BigDecimal qualityScore = qualityScore(universe == null ? null : universe.getFundType(),
                returnScore, riskScore, stabilityScore, excessScore, peerScore, liquidityScore, dataScore);
        ScreenerQualityScore score = new ScreenerQualityScore();
        score.setFundCode(factor.getFundCode());
        score.setScoreDate(factor.getFactorDate());
        score.setQualityScore(qualityScore);
        score.setReturnScore(returnScore);
        score.setRiskScore(riskScore);
        score.setStabilityScore(stabilityScore);
        score.setExcessScore(excessScore);
        score.setPeerScore(peerScore);
        score.setLiquidityScore(liquidityScore);
        score.setDataScore(dataScore);
        score.setReturnQualityScore(returnQualityScore);
        score.setDrawdownControlScore(drawdownControlScore);
        score.setConsistencyScore(consistencyScore);
        score.setInvestabilityScore(investabilityScore);
        score.setRecommendLevel(recommendLevel(qualityScore, 1, 1));
        score.setReasonsJson(writeStringList(reasons(factor)));
        score.setRisksJson(writeStringList(List.of(RISK_DISCLAIMER)));
        score.setModelVersion(MODEL_VERSION);
        LocalDateTime now = LocalDateTime.now();
        score.setCreateTime(now);
        score.setUpdateTime(now);
        score.setDeleted(0);
        return score;
    }

    private BigDecimal qualityScore(String fundType, BigDecimal returnScore, BigDecimal riskScore, BigDecimal stabilityScore,
                                    BigDecimal excessScore, BigDecimal peerScore, BigDecimal liquidityScore, BigDecimal dataScore) {
        if ("INDEX".equalsIgnoreCase(fundType)) {
            return weighted(
                    returnScore, "0.2500",
                    riskScore, "0.2000",
                    stabilityScore, "0.1000",
                    excessScore, "0.2500",
                    peerScore, "0.0000",
                    liquidityScore, "0.1000",
                    dataScore, "0.0500"
            );
        }
        return weighted(
                returnScore, "0.3000",
                riskScore, "0.2500",
                stabilityScore, "0.2000",
                excessScore, "0.1000",
                peerScore, "0.1000",
                liquidityScore, "0.0000",
                dataScore, "0.0500"
        );
    }

    private BigDecimal returnQualityScore(ScreenerFactorSnapshot factor) {
        BigDecimal raw = new BigDecimal("45.0000")
                .add(capped(valueOrZero(factor.getReturn60d()), "20.0000").multiply(new BigDecimal("0.7000")))
                .add(capped(valueOrZero(factor.getReturn120d()), "30.0000").multiply(new BigDecimal("1.0000")))
                .add(capped(valueOrZero(factor.getReturn250d()), "50.0000").multiply(new BigDecimal("0.3500")))
                .add(valueOrZero(factor.getReturnDrawdownRatio120d()).multiply(new BigDecimal("5.0000")))
                .add(valueOrZero(factor.getExcessReturn120d()).multiply(new BigDecimal("2.0000")));
        return clip(raw);
    }

    private BigDecimal drawdownControlScore(ScreenerFactorSnapshot factor) {
        BigDecimal raw = new BigDecimal("92.0000")
                .add(valueOrZero(factor.getMaxDrawdown120d()).multiply(new BigDecimal("1.6000")))
                .subtract(valueOrZero(factor.getVolatility120d()).multiply(new BigDecimal("0.3500")))
                .add(valueOrZero(factor.getReturnDrawdownRatio120d()).multiply(new BigDecimal("4.0000")));
        return clip(raw);
    }

    private BigDecimal consistencyScore(ScreenerFactorSnapshot factor) {
        BigDecimal raw = valueOrDefault(factor.getReturnConsistencyScore(), new BigDecimal("50.0000"))
                .multiply(new BigDecimal("0.7000"))
                .add(valueOrDefault(factor.getPositiveDayRatio60d(), new BigDecimal("50.0000")).multiply(new BigDecimal("0.3000")))
                .add(valueOrZero(factor.getTrendSlope60d()).multiply(new BigDecimal("20.0000")));
        return clip(raw);
    }

    private BigDecimal investabilityScore(ScreenerFactorSnapshot factor, ScreenerFundUniverse universe) {
        BigDecimal sizeScore = clip(valueOrDefault(factor.getFundSize(),
                universe == null ? null : universe.getFundSize(),
                new BigDecimal("25.0000")).multiply(new BigDecimal("2.0000")));
        BigDecimal dataScore = clip(BigDecimal.valueOf(factor.getNavSampleSize() == null ? 0 : factor.getNavSampleSize())
                .multiply(HUNDRED)
                .divide(new BigDecimal("250.0000"), 4, RoundingMode.HALF_UP));
        BigDecimal ageScore = clip(valueOrZero(factor.getFundAgeYears()).multiply(new BigDecimal("16.0000")));
        return weighted(
                dataScore, "0.4500",
                sizeScore, "0.2500",
                ageScore, "0.2500",
                new BigDecimal("80.0000"), "0.0500",
                ZERO, "0.0000",
                ZERO, "0.0000",
                ZERO, "0.0000"
        );
    }

    private BigDecimal weighted(BigDecimal returnScore, String returnWeight,
                                BigDecimal riskScore, String riskWeight,
                                BigDecimal stabilityScore, String stabilityWeight,
                                BigDecimal excessScore, String excessWeight,
                                BigDecimal peerScore, String peerWeight,
                                BigDecimal liquidityScore, String liquidityWeight,
                                BigDecimal dataScore, String dataWeight) {
        return returnScore.multiply(new BigDecimal(returnWeight))
                .add(riskScore.multiply(new BigDecimal(riskWeight)))
                .add(stabilityScore.multiply(new BigDecimal(stabilityWeight)))
                .add(excessScore.multiply(new BigDecimal(excessWeight)))
                .add(peerScore.multiply(new BigDecimal(peerWeight)))
                .add(liquidityScore.multiply(new BigDecimal(liquidityWeight)))
                .add(dataScore.multiply(new BigDecimal(dataWeight)))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private Map<String, ScreenerQualityScore> existingScoresByKey() {
        return screenerQualityScoreMapper.selectList(new LambdaQueryWrapper<ScreenerQualityScore>())
                .stream()
                .filter(score -> StringUtils.hasText(score.getFundCode()) && score.getScoreDate() != null)
                .collect(Collectors.toMap(this::scoreKey, Function.identity(), (left, right) -> left));
    }

    private void upsert(ScreenerQualityScore score, Map<String, ScreenerQualityScore> existingScoreByKey) {
        ScreenerQualityScore existing = existingScoreByKey.get(scoreKey(score));
        if (existing == null) {
            screenerQualityScoreMapper.insert(score);
        } else {
            score.setId(existing.getId());
            score.setCreateTime(existing.getCreateTime());
            screenerQualityScoreMapper.updateById(score);
        }
    }

    private String scoreKey(ScreenerQualityScore score) {
        return score.getFundCode() + "|" + score.getScoreDate();
    }

    private List<String> reasons(ScreenerFactorSnapshot factor) {
        List<String> reasons = new ArrayList<>();
        if (valueOrZero(factor.getReturn120d()).compareTo(BigDecimal.ZERO) > 0) {
            reasons.add(REASON_RETURN_120D);
        }
        if (factor.getMaxDrawdown120d() != null && factor.getMaxDrawdown120d().compareTo(new BigDecimal("-15.0000")) > 0) {
            reasons.add(REASON_DRAWDOWN);
        }
        if (factor.getNavSampleSize() != null && factor.getNavSampleSize() >= 120) {
            reasons.add(REASON_SAMPLE);
        }
        if (reasons.isEmpty()) {
            reasons.add("\u7efc\u5408\u8bc4\u5206\u5904\u4e8e\u53ef\u89c2\u5bdf\u533a\u95f4");
        }
        return reasons;
    }

    private FundScreenerRankItemVO toRankItem(ScreenerQualityScore score, ScreenerFundUniverse universe, ScreenerFactorSnapshot factor) {
        return new FundScreenerRankItemVO(
                score.getFundCode(),
                universe == null ? null : universe.getFundName(),
                universe == null ? null : universe.getFundType(),
                universe == null ? null : universe.getCompanyName(),
                universe == null ? null : universe.getManagerName(),
                primitive(score.getQualityScore()),
                primitive(score.getReturnScore()),
                primitive(score.getRiskScore()),
                primitive(score.getStabilityScore()),
                primitive(score.getExcessScore()),
                primitive(score.getPeerScore()),
                primitive(score.getLiquidityScore()),
                primitive(score.getDataScore()),
                primitive(score.getReturnQualityScore()),
                primitive(score.getDrawdownControlScore()),
                primitive(score.getConsistencyScore()),
                primitive(score.getInvestabilityScore()),
                score.getRankNo(),
                toDouble(score.getRankPercentile()),
                score.getRecommendLevel(),
                factor == null ? null : toDouble(factor.getReturn60d()),
                factor == null ? null : toDouble(factor.getReturn120d()),
                factor == null ? null : toDouble(factor.getReturn250d()),
                factor == null ? null : toDouble(factor.getMaxDrawdown120d()),
                factor == null ? null : toDouble(factor.getVolatility120d()),
                factor == null ? null : toDouble(factor.getPeerPercentile()),
                factor == null ? null : toDouble(factor.getReturnDrawdownRatio120d()),
                factor == null ? null : toDouble(factor.getReturnConsistencyScore()),
                factor == null ? null : factor.getBenchmarkCode(),
                score.getScoreDate() == null ? null : score.getScoreDate().toString(),
                readStringList(score.getReasonsJson()),
                readStringList(score.getRisksJson()),
                SystemConstants.DISCLAIMER
        );
    }

    private boolean matches(FundScreenerQueryRequest request, RankSource source) {
        ScreenerQualityScore score = source.score();
        ScreenerFundUniverse universe = source.universe();
        if (StringUtils.hasText(request.fundType())
                && (universe == null || !Objects.equals(request.fundType(), universe.getFundType()))) {
            return false;
        }
        if (StringUtils.hasText(request.recommendLevel()) && !Objects.equals(request.recommendLevel(), score.getRecommendLevel())) {
            return false;
        }
        if (request.minScore() != null && valueOrZero(score.getQualityScore()).compareTo(request.minScore()) < 0) {
            return false;
        }
        if (request.minFundSize() != null
                && (universe == null || universe.getFundSize() == null || universe.getFundSize().compareTo(request.minFundSize()) < 0)) {
            return false;
        }
        if (Boolean.TRUE.equals(request.excludeShareClassC()) && isShareClassC(universe)) {
            return false;
        }
        return !Boolean.TRUE.equals(request.onlyActiveFund())
                || (universe != null && Integer.valueOf(1).equals(universe.getActiveFund()));
    }

    private boolean isShareClassC(ScreenerFundUniverse universe) {
        if (universe == null || !StringUtils.hasText(universe.getShareClass())) {
            return false;
        }
        return "C".equalsIgnoreCase(universe.getShareClass().trim());
    }

    private Comparator<FundScreenerRankItemVO> rankComparator(String sortBy) {
        String actualSortBy = StringUtils.hasText(sortBy) ? sortBy : "qualityScore";
        return switch (actualSortBy) {
            case "return120d" -> Comparator.comparing(FundScreenerRankItemVO::return120d, Comparator.nullsLast(Double::compareTo)).reversed();
            case "riskScore" -> Comparator.comparingDouble(FundScreenerRankItemVO::riskScore).reversed();
            case "returnScore" -> Comparator.comparingDouble(FundScreenerRankItemVO::returnScore).reversed();
            default -> Comparator.comparingDouble(FundScreenerRankItemVO::qualityScore).reversed();
        };
    }

    private FundScreenerScoreBreakdownVO scoreBreakdown(ScreenerQualityScore score) {
        return new FundScreenerScoreBreakdownVO(
                primitive(score.getReturnScore()),
                primitive(score.getRiskScore()),
                primitive(score.getStabilityScore()),
                primitive(score.getExcessScore()),
                primitive(score.getPeerScore()),
                primitive(score.getLiquidityScore()),
                primitive(score.getDataScore()),
                primitive(score.getReturnQualityScore()),
                primitive(score.getDrawdownControlScore()),
                primitive(score.getConsistencyScore()),
                primitive(score.getInvestabilityScore())
        );
    }

    private Map<String, Object> factorMap(ScreenerFactorSnapshot factor) {
        if (factor == null) {
            return Map.of();
        }
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("return20d", factor.getReturn20d());
        factors.put("return60d", factor.getReturn60d());
        factors.put("return120d", factor.getReturn120d());
        factors.put("return250d", factor.getReturn250d());
        factors.put("maxDrawdown120d", factor.getMaxDrawdown120d());
        factors.put("volatility120d", factor.getVolatility120d());
        factors.put("positiveDayRatio60d", factor.getPositiveDayRatio60d());
        factors.put("excessReturn120d", factor.getExcessReturn120d());
        factors.put("peerPercentile", factor.getPeerPercentile());
        factors.put("benchmarkCode", factor.getBenchmarkCode());
        factors.put("returnDrawdownRatio120d", factor.getReturnDrawdownRatio120d());
        factors.put("returnConsistencyScore", factor.getReturnConsistencyScore());
        factors.put("fundAgeYears", factor.getFundAgeYears());
        factors.put("fundSize", factor.getFundSize());
        factors.put("navSampleSize", factor.getNavSampleSize());
        return factors;
    }

    private ScreenerQualityScore findScore(String fundCode) {
        ScreenerQualityScore score = screenerQualityScoreMapper.selectOne(new LambdaQueryWrapper<ScreenerQualityScore>()
                .eq(ScreenerQualityScore::getFundCode, fundCode)
                .orderByDesc(ScreenerQualityScore::getScoreDate)
                .last("LIMIT 1"));
        if (score != null) {
            return score;
        }
        return screenerQualityScoreMapper.selectList(new LambdaQueryWrapper<ScreenerQualityScore>()
                        .eq(ScreenerQualityScore::getFundCode, fundCode)
                        .orderByDesc(ScreenerQualityScore::getScoreDate))
                .stream()
                .filter(item -> Objects.equals(fundCode, item.getFundCode()))
                .max(Comparator.comparing(ScreenerQualityScore::getScoreDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private ScreenerFundUniverse findUniverse(String fundCode) {
        return screenerFundUniverseMapper.selectOne(new LambdaQueryWrapper<ScreenerFundUniverse>()
                .eq(ScreenerFundUniverse::getFundCode, fundCode)
                .last("LIMIT 1"));
    }

    private ScreenerFactorSnapshot findFactor(String fundCode) {
        return screenerFactorSnapshotMapper.selectOne(new LambdaQueryWrapper<ScreenerFactorSnapshot>()
                .eq(ScreenerFactorSnapshot::getFundCode, fundCode)
                .orderByDesc(ScreenerFactorSnapshot::getFactorDate)
                .last("LIMIT 1"));
    }

    private String recommendLevel(BigDecimal qualityScore, int rankNo, int total) {
        int strongCutoff = percentileCutoff(total, screenerStrategy.getStrongTopPercent());
        int watchCutoff = Math.max(strongCutoff, percentileCutoff(total, screenerStrategy.getWatchTopPercent()));
        if (qualityScore.compareTo(screenerStrategy.getStrongMinScore()) >= 0 && rankNo <= strongCutoff) {
            return "STRONG";
        }
        if (qualityScore.compareTo(screenerStrategy.getWatchMinScore()) >= 0 || rankNo <= watchCutoff) {
            return "WATCH";
        }
        if (qualityScore.compareTo(screenerStrategy.getNeutralMinScore()) >= 0) {
            return "NEUTRAL";
        }
        return "AVOID";
    }

    private int percentileCutoff(int total, int percent) {
        return Math.max(1, (int) Math.ceil(total * (percent / 100.0)));
    }

    private BigDecimal scoreFromReturn(BigDecimal value) {
        return new BigDecimal("50.0000").add(valueOrZero(value).multiply(new BigDecimal("2.0000")));
    }

    private BigDecimal scoreFromDrawdown(BigDecimal value) {
        return HUNDRED.add(valueOrZero(value).multiply(new BigDecimal("2.0000")));
    }

    private BigDecimal clip(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        if (value.compareTo(HUNDRED) > 0) {
            return HUNDRED;
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal valueOrDefault(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }

    private BigDecimal valueOrDefault(BigDecimal value, BigDecimal secondaryValue, BigDecimal defaultValue) {
        if (value != null) {
            return value;
        }
        return secondaryValue == null ? defaultValue : secondaryValue;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal capped(BigDecimal value, String cap) {
        BigDecimal upper = new BigDecimal(cap);
        if (value.compareTo(upper) > 0) {
            return upper;
        }
        return value;
    }

    private double primitive(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private record RankSource(ScreenerQualityScore score, ScreenerFundUniverse universe, ScreenerFactorSnapshot factor) {
    }
}
