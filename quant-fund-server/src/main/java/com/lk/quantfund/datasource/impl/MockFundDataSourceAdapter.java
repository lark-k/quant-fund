package com.lk.quantfund.datasource.impl;

import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.FundDataSourceAdapter;
import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundPeerRankDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MockFundDataSourceAdapter implements FundDataSourceAdapter {

    private static final String SOURCE_NAME = "MOCK";

    private final QuantFundProperties properties;

    public MockFundDataSourceAdapter(QuantFundProperties properties) {
        this.properties = properties;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public int priority() {
        return 999;
    }

    @Override
    public boolean enabled() {
        return properties.getFundDataSource().isMockFallbackEnabled();
    }

    @Override
    public List<FundSearchResultDTO> searchFunds(String keyword) {
        return List.of(
                new FundSearchResultDTO("000001", "华夏成长混合", "MIXED", "HXCC", SOURCE_NAME),
                new FundSearchResultDTO("110022", "易方达消费行业股票", "ACTIVE_EQUITY", "YFDXFHYGP", SOURCE_NAME),
                new FundSearchResultDTO("161725", "招商中证白酒指数", "INDEX", "ZSZZBJZS", SOURCE_NAME)
        ).stream()
                .filter(item -> item.fundCode().contains(keyword) || item.fundName().contains(keyword))
                .toList();
    }

    @Override
    public Optional<FundBasicInfoDTO> getBasicInfo(String fundCode) {
        String name = switch (fundCode) {
            case "110022" -> "易方达消费行业股票";
            case "161725" -> "招商中证白酒指数";
            default -> "华夏成长混合";
        };
        String type = "161725".equals(fundCode) ? "INDEX" : "MIXED";
        return Optional.of(new FundBasicInfoDTO(fundCode, name, type, !"161725".equals(fundCode),
                "161725".equals(fundCode) ? "中证白酒指数" : null,
                "模拟经理", "模拟基金公司", "MEDIUM", "mock,local", SOURCE_NAME));
    }

    @Override
    public List<FundNavPointDTO> getHistoricalNav(String fundCode, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(5) : startDate;
        return start.datesUntil((endDate == null ? LocalDate.now() : endDate).plusDays(1))
                .map(date -> new FundNavPointDTO(fundCode, date, BigDecimal.valueOf(1.1234), BigDecimal.valueOf(3.4567),
                        BigDecimal.valueOf(0.12), SOURCE_NAME))
                .toList();
    }

    @Override
    public Optional<FundEstimateDTO> getIntradayEstimate(String fundCode) {
        return Optional.of(new FundEstimateDTO(fundCode, getBasicInfo(fundCode).map(FundBasicInfoDTO::fundName).orElse("Mock Fund"),
                BigDecimal.valueOf(1.2345), BigDecimal.valueOf(-0.38), LocalDate.now(), LocalDateTime.now(),
                SOURCE_NAME, true, "{\"mock\":true}"));
    }

    @Override
    public List<FundStockHoldingDTO> getHeavyStocks(String fundCode) {
        return List.of(new FundStockHoldingDTO(fundCode, "600519", "贵州茅台", BigDecimal.valueOf(8.12),
                "白酒", BigDecimal.valueOf(1207.70), BigDecimal.valueOf(-1.21), "1.600519",
                LocalDate.now().minusMonths(3), SOURCE_NAME));
    }

    @Override
    public List<FundThemeDTO> getRelatedThemes(String fundCode) {
        return List.of(new FundThemeDTO(fundCode, "白酒", "HEAVY_STOCK_WEIGHTED",
                BigDecimal.valueOf(8.12), BigDecimal.valueOf(-1.21), SOURCE_NAME));
    }

    @Override
    public Optional<FundPeerRankDTO> getPeerRank(String fundCode) {
        return Optional.of(new FundPeerRankDTO(fundCode, "同类第 35/100", "混合型",
                35, 100, BigDecimal.valueOf(35), "MOCK", SOURCE_NAME));
    }
}
