package com.lk.quantfund.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum FundType {
    ACTIVE_EQUITY,
    INDEX,
    ETF,
    ETF_LINK,
    INDEX_ENHANCED,
    BOND,
    FIXED_INCOME_PLUS,
    MONEY_MARKET,
    QDII,
    MIXED,
    UNKNOWN;

    @JsonCreator
    public static FundType from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase();
        for (FundType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        if (normalized.contains("ETF")) {
            return normalized.contains("LINK") || value.contains("联接") ? ETF_LINK : ETF;
        }
        if (normalized.contains("INDEX") || value.contains("指数")) {
            return value.contains("增强") ? INDEX_ENHANCED : INDEX;
        }
        if (normalized.contains("BOND") || value.contains("债")) {
            return BOND;
        }
        if (normalized.contains("MONEY") || value.contains("货币")) {
            return MONEY_MARKET;
        }
        if (normalized.contains("QDII") || value.contains("海外") || value.contains("全球")) {
            return QDII;
        }
        if (normalized.contains("MIXED") || value.contains("混合")) {
            return MIXED;
        }
        if (normalized.contains("ACTIVE") || value.contains("主动") || value.contains("股票")) {
            return ACTIVE_EQUITY;
        }
        return UNKNOWN;
    }
}
