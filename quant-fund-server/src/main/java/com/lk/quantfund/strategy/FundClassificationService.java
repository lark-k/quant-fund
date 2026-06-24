package com.lk.quantfund.strategy;

import com.lk.quantfund.enums.FundType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FundClassificationService {

    public String classify(String fundName, String rawFundType, Boolean activeFund, String trackingIndex) {
        String name = lower(fundName);
        String type = lower(rawFundType);
        if (name.contains("qdii") || name.contains("海外") || name.contains("全球")) {
            return FundType.QDII.name();
        }
        if (name.contains("货币") || type.contains("money")) {
            return FundType.MONEY_MARKET.name();
        }
        if (name.contains("债") || type.contains("bond")) {
            return name.contains("固收") || name.contains("+") ? FundType.FIXED_INCOME_PLUS.name() : FundType.BOND.name();
        }
        if (name.contains("etf联接") || name.contains("etf连接") || name.contains("联接")) {
            return FundType.ETF_LINK.name();
        }
        if (name.contains("etf")) {
            return FundType.ETF.name();
        }
        if (name.contains("指数增强") || name.contains("增强")) {
            return FundType.INDEX_ENHANCED.name();
        }
        if (name.contains("指数") || StringUtils.hasText(trackingIndex)) {
            return FundType.INDEX.name();
        }
        if (Boolean.TRUE.equals(activeFund)) {
            return FundType.ACTIVE_EQUITY.name();
        }
        if (name.contains("混合") || type.contains("mixed")) {
            return FundType.MIXED.name();
        }
        return StringUtils.hasText(rawFundType) ? rawFundType : FundType.UNKNOWN.name();
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}

