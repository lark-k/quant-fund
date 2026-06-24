package com.lk.quantfund.service.valuation;

import com.lk.quantfund.datasource.model.FundThemeDTO;
import com.lk.quantfund.service.FundQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FundValuationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private final FundQueryService fundQueryService;

    public FundValuationService(FundQueryService fundQueryService) {
        this.fundQueryService = fundQueryService;
    }

    public FundValuationResult estimate(String fundCode, String fundName, String fundType, BigDecimal fundEstimateRate) {
        try {
            List<FundThemeDTO> themes = fundQueryService.getRelatedThemes(fundCode);
            FundThemeDTO indexTheme = indexTheme(fundCode, fundName, fundType, themes);
            if (indexTheme != null) {
                return new FundValuationResult(
                        indexTheme.themeName(),
                        valueOrFallback(indexTheme.estimatedRate(), fundEstimateRate),
                        indexTheme.sourceName(),
                        "跟踪指数/场内指数估算",
                        marketStatus(fundName, fundType)
                );
            }
            FundValuationResult weighted = weightedThemeResult(themes, fundEstimateRate, fundName, fundType);
            if (weighted != null) {
                return weighted;
            }
            FundThemeDTO primary = themes.stream()
                    .filter(theme -> StringUtils.hasText(theme.themeName()))
                    .filter(theme -> !genericThemeName(theme.themeName()))
                    .max(Comparator.comparing(theme -> theme.weight() == null ? ZERO : theme.weight()))
                    .orElse(null);
            if (primary != null) {
                BigDecimal rate = primary.estimatedRate() != null ? primary.estimatedRate() : valueOrZero(fundEstimateRate);
                return new FundValuationResult(
                        primary.themeName(),
                        rate,
                        primary.sourceName(),
                        "重仓股占比加权估算",
                        marketStatus(fundName, fundType)
                );
            }
        } catch (RuntimeException ignored) {
            // Fall back to the single-fund estimate when detailed holdings or quotes are temporarily unavailable.
        }
        return new FundValuationResult(
                fallbackThemeName(fundName, fundType),
                valueOrZero(fundEstimateRate),
                "FUND_ESTIMATE",
                "基金估值涨跌率",
                marketStatus(fundName, fundType)
        );
    }

    private FundValuationResult weightedThemeResult(List<FundThemeDTO> themes, BigDecimal fundEstimateRate,
                                                    String fundName, String fundType) {
        List<FundThemeDTO> namedThemes = themes.stream()
                .filter(theme -> StringUtils.hasText(theme.themeName()))
                .filter(theme -> !genericThemeName(theme.themeName()))
                .filter(theme -> theme.weight() != null && theme.weight().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        List<FundThemeDTO> usable = namedThemes.stream()
                .filter(theme -> theme.estimatedRate() != null)
                .toList();
        if (usable.isEmpty()) {
            if (namedThemes.isEmpty()) {
                return null;
            }
            return new FundValuationResult(
                    joinedThemeName(namedThemes, fundName, fundType),
                    valueOrZero(fundEstimateRate),
                    "FUND_THEME_MAPPING",
                    "关联主题映射，涨跌率采用基金估值/官方净值",
                    marketStatus(fundName, fundType)
            );
        }
        BigDecimal totalWeight = usable.stream()
                .map(FundThemeDTO::weight)
                .reduce(ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal weightedRate = usable.stream()
                .map(theme -> theme.weight().multiply(theme.estimatedRate()))
                .reduce(ZERO, BigDecimal::add)
                .divide(totalWeight, 4, RoundingMode.HALF_UP);
        String themeName = joinedThemeName(usable, fundName, fundType);
        BigDecimal coverage = totalWeight.min(HUNDRED);
        BigDecimal finalRate = coverage.compareTo(new BigDecimal("35.0000")) >= 0 || fundEstimateRate == null
                ? weightedRate
                : weightedRate.multiply(coverage).add(fundEstimateRate.multiply(HUNDRED.subtract(coverage))).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return new FundValuationResult(
                themeName,
                finalRate,
                "HEAVY_STOCK_WEIGHTED",
                "重仓股行业/板块占比加权估算",
                marketStatus(fundName, fundType)
        );
    }

    private String joinedThemeName(List<FundThemeDTO> themes, String fundName, String fundType) {
        return themes.stream()
                .sorted(Comparator.comparing(FundThemeDTO::weight, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .limit(3)
                .map(FundThemeDTO::themeName)
                .filter(theme -> !genericThemeName(theme))
                .distinct()
                .reduce((left, right) -> left + "/" + right)
                .orElse(fallbackThemeName(fundName, fundType));
    }

    private FundThemeDTO indexTheme(String fundCode, String fundName, String fundType, List<FundThemeDTO> themes) {
        if (!isIndexLike(fundName, fundType)) {
            return null;
        }
        String themeName = fallbackThemeName(fundName, fundType);
        return themes.stream()
                .filter(theme -> StringUtils.hasText(theme.themeName()))
                .filter(theme -> theme.themeName().contains(themeName) || themeName.contains(theme.themeName()))
                .findFirst()
                .orElseGet(() -> new FundThemeDTO(fundCode, themeName, "TRACKING_INDEX_NAME", HUNDRED, valueOrZero(null), "NAME_RULE"));
    }

    private boolean isIndexLike(String fundName, String fundType) {
        String text = safe(fundName) + safe(fundType);
        return text.contains("指数") || text.contains("ETF") || text.contains("联接") || text.contains("主题指数")
                || text.contains("INDEX") || text.contains("ETF_LINK");
    }

    public String marketStatus(String fundName, String fundType) {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return "非交易日";
        }
        LocalTime time = now.toLocalTime();
        if (isHongKongTrading(time) && isHongKongRelated(fundName, fundType)) {
            return "港股交易中";
        }
        if (isAStockTrading(time)) {
            return "A股交易中";
        }
        if (isUsRelated(fundName, fundType)) {
            return "海外市场参考";
        }
        if (time.isAfter(LocalTime.of(15, 0)) && isHongKongTrading(time) && isHongKongRelated(fundName, fundType)) {
            return "港股交易中";
        }
        return time.isBefore(LocalTime.of(9, 30)) ? "未开盘" : "已收盘";
    }

    private boolean isAStockTrading(LocalTime time) {
        return (!time.isBefore(LocalTime.of(9, 30)) && !time.isAfter(LocalTime.of(11, 30)))
                || (!time.isBefore(LocalTime.of(13, 0)) && !time.isAfter(LocalTime.of(15, 0)));
    }

    private boolean isHongKongTrading(LocalTime time) {
        return (!time.isBefore(LocalTime.of(9, 30)) && time.isBefore(LocalTime.of(12, 0)))
                || (!time.isBefore(LocalTime.of(13, 0)) && !time.isAfter(LocalTime.of(16, 0)));
    }

    private boolean isHongKongRelated(String fundName, String fundType) {
        String text = safe(fundName) + safe(fundType);
        return text.contains("港") || text.contains("恒生") || text.contains("互联");
    }

    private boolean isUsRelated(String fundName, String fundType) {
        String text = safe(fundName) + safe(fundType);
        return text.contains("QDII") || text.contains("全球") || text.contains("海外") || text.contains("纳斯达克") || text.contains("标普");
    }

    private String fallbackThemeName(String fundName, String fundType) {
        String name = safe(fundName);
        String type = safe(fundType);
        if (name.contains("中证白酒") || name.contains("白酒")) return "中证白酒";
        if (name.contains("恒生科技")) return "恒生科技";
        if (name.contains("电网设备") || name.contains("特高压")) return "中证电网设备";
        if (name.contains("电网") || name.contains("电力设备")) return "电网设备";
        if (name.contains("广发远见")) return "光纤/算力租赁/存储芯片";
        if (name.contains("财通成长") || name.contains("PCB")) return "PCB/CPO";
        if (name.contains("华夏恒生科技") || name.contains("恒生科技")) return "恒生科技";
        if (name.contains("CPO") || name.contains("光模块")) return "CPO";
        if (name.contains("光纤") || name.contains("光通信")) return "光纤";
        if (name.contains("算力") || name.contains("租赁")) return "算力租赁";
        if (name.contains("存储") || name.contains("存储芯片")) return "存储芯片";
        if (name.contains("PCB")) return "PCB";
        if (name.contains("新能源")) return "新能源";
        if (name.contains("科技") || name.contains("人工智能") || name.contains("AI")) return "科技成长";
        if (name.contains("半导体") || name.contains("芯片")) return "半导体";
        if (name.contains("医药") || name.contains("医疗")) return "医药";
        if (name.contains("海外") || name.contains("全球") || "QDII".equalsIgnoreCase(type)) return "海外基金";
        if (name.contains("沪深300")) return "沪深300";
        if (type.contains("BOND")) return "债券基金";
        if (type.contains("INDEX") || type.contains("ETF")) return "跟踪指数待同步";
        return "重仓板块待同步";
    }

    private boolean genericThemeName(String themeName) {
        String name = safe(themeName);
        return name.isBlank()
                || "主动权益".equals(name)
                || "指数基金".equals(name)
                || "其他重仓".equals(name)
                || "未分类".equals(name)
                || "重仓板块待同步".equals(name)
                || "跟踪指数待同步".equals(name);
    }

    private BigDecimal valueOrFallback(BigDecimal value, BigDecimal fallback) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0 ? valueOrZero(fallback) : value;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
