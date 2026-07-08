package com.lk.quantfund.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "quantfund")
public class QuantFundProperties {

    private final Ai ai = new Ai();
    private final FundDataSource fundDataSource = new FundDataSource();
    private final QuantEngine quantEngine = new QuantEngine();
    private final Scheduler scheduler = new Scheduler();

    public Ai getAi() {
        return ai;
    }

    public FundDataSource getFundDataSource() {
        return fundDataSource;
    }

    public QuantEngine getQuantEngine() {
        return quantEngine;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public static class Ai {
        private boolean enabled = true;
        private boolean mockEnabled = false;
        @NotBlank
        private String provider = "deepseek";
        @NotBlank
        private String model = "deepseek-v4-flash";
        @NotBlank
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        @Min(1000)
        private int timeoutMs = 15000;
        @Min(256)
        private int maxTokens = 1200;
        private boolean reasoningEnabled = false;
        @Min(1)
        private int accountAnalysisConcurrency = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMockEnabled() {
            return mockEnabled;
        }

        public void setMockEnabled(boolean mockEnabled) {
            this.mockEnabled = mockEnabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public boolean isReasoningEnabled() {
            return reasoningEnabled;
        }

        public void setReasoningEnabled(boolean reasoningEnabled) {
            this.reasoningEnabled = reasoningEnabled;
        }

        public int getAccountAnalysisConcurrency() {
            return accountAnalysisConcurrency;
        }

        public void setAccountAnalysisConcurrency(int accountAnalysisConcurrency) {
            this.accountAnalysisConcurrency = accountAnalysisConcurrency;
        }
    }

    public static class FundDataSource {
        @Min(1000)
        private int timeoutMs = 5000;
        @Min(10)
        private int refreshIntervalSeconds = 120;
        @Min(1)
        private int manualRefreshCooldownSeconds = 10;
        private boolean mockFallbackEnabled = false;
        private boolean eastMoneyEnabled = true;
        @NotBlank
        private String eastMoneySearchUrl = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx";
        @NotBlank
        private String eastMoneyFundListUrl = "https://fund.eastmoney.com/js/fundcode_search.js";
        @NotBlank
        private String eastMoneyEstimateUrl = "https://fundgz.1234567.com.cn/js/{fundCode}.js";
        @NotBlank
        private String eastMoneyHistoricalNavUrl = "https://api.fund.eastmoney.com/f10/lsjz";
        @NotBlank
        private String eastMoneyFundDetailUrl = "https://fund.eastmoney.com/pingzhongdata/{fundCode}.js";
        @NotBlank
        private String eastMoneyFundArchiveUrl = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx";
        @NotBlank
        private String eastMoneyQuoteUrl = "https://push2.eastmoney.com/api/qt/ulist.np/get";

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getRefreshIntervalSeconds() {
            return refreshIntervalSeconds;
        }

        public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
            this.refreshIntervalSeconds = refreshIntervalSeconds;
        }

        public int getManualRefreshCooldownSeconds() {
            return manualRefreshCooldownSeconds;
        }

        public void setManualRefreshCooldownSeconds(int manualRefreshCooldownSeconds) {
            this.manualRefreshCooldownSeconds = manualRefreshCooldownSeconds;
        }

        public boolean isMockFallbackEnabled() {
            return mockFallbackEnabled;
        }

        public void setMockFallbackEnabled(boolean mockFallbackEnabled) {
            this.mockFallbackEnabled = mockFallbackEnabled;
        }

        public boolean isEastMoneyEnabled() {
            return eastMoneyEnabled;
        }

        public void setEastMoneyEnabled(boolean eastMoneyEnabled) {
            this.eastMoneyEnabled = eastMoneyEnabled;
        }

        public String getEastMoneySearchUrl() {
            return eastMoneySearchUrl;
        }

        public void setEastMoneySearchUrl(String eastMoneySearchUrl) {
            this.eastMoneySearchUrl = eastMoneySearchUrl;
        }

        public String getEastMoneyFundListUrl() {
            return eastMoneyFundListUrl;
        }

        public void setEastMoneyFundListUrl(String eastMoneyFundListUrl) {
            this.eastMoneyFundListUrl = eastMoneyFundListUrl;
        }

        public String getEastMoneyEstimateUrl() {
            return eastMoneyEstimateUrl;
        }

        public void setEastMoneyEstimateUrl(String eastMoneyEstimateUrl) {
            this.eastMoneyEstimateUrl = eastMoneyEstimateUrl;
        }

        public String getEastMoneyHistoricalNavUrl() {
            return eastMoneyHistoricalNavUrl;
        }

        public void setEastMoneyHistoricalNavUrl(String eastMoneyHistoricalNavUrl) {
            this.eastMoneyHistoricalNavUrl = eastMoneyHistoricalNavUrl;
        }

        public String getEastMoneyFundDetailUrl() {
            return eastMoneyFundDetailUrl;
        }

        public void setEastMoneyFundDetailUrl(String eastMoneyFundDetailUrl) {
            this.eastMoneyFundDetailUrl = eastMoneyFundDetailUrl;
        }

        public String getEastMoneyFundArchiveUrl() {
            return eastMoneyFundArchiveUrl;
        }

        public void setEastMoneyFundArchiveUrl(String eastMoneyFundArchiveUrl) {
            this.eastMoneyFundArchiveUrl = eastMoneyFundArchiveUrl;
        }

        public String getEastMoneyQuoteUrl() {
            return eastMoneyQuoteUrl;
        }

        public void setEastMoneyQuoteUrl(String eastMoneyQuoteUrl) {
            this.eastMoneyQuoteUrl = eastMoneyQuoteUrl;
        }
    }

    public static class QuantEngine {
        private boolean enabled = true;
        @NotBlank
        private String baseUrl = "http://127.0.0.1:8091";
        @Min(1000)
        private int timeoutMs = 8000;
        @Min(1000)
        private int batchTimeoutMs = 60000;
        @Min(1000)
        private int backtestTimeoutMs = 300000;
        @Min(1)
        private int responseMaxInMemoryMb = 16;
        @NotBlank
        private String modelVersion = "rule-v1.36.0";
        private boolean fallbackToJavaRules = false;
        @NotBlank
        private String decisionDeadline = "15:00:00";
        @Min(1)
        private int maxBatchHoldings = 100;
        @Min(1)
        private int maxBacktestFunds = 1000;
        @Min(1)
        private int maxParamGrid = 100;
        private BigDecimal buyThreshold = new BigDecimal("52.0000");
        private BigDecimal sellThreshold = new BigDecimal("6.0000");
        private BigDecimal maxSinglePositionRate = new BigDecimal("45.0000");
        private BigDecimal buyStepRatio = new BigDecimal("20.0000");
        private BigDecimal sellStepRatio = new BigDecimal("8.0000");
        private BigDecimal takeProfitRate = new BigDecimal("300.0000");
        private BigDecimal stopLossRate = new BigDecimal("-18.0000");
        private int minNavSamples = 40;
        private int warmupDays = 180;
        private BigDecimal trendHoldReturn20d = new BigDecimal("1.5000");
        private BigDecimal trendHoldMa20Deviation = new BigDecimal("-7.0000");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getBatchTimeoutMs() {
            return batchTimeoutMs;
        }

        public void setBatchTimeoutMs(int batchTimeoutMs) {
            this.batchTimeoutMs = batchTimeoutMs;
        }

        public int getBacktestTimeoutMs() {
            return backtestTimeoutMs;
        }

        public void setBacktestTimeoutMs(int backtestTimeoutMs) {
            this.backtestTimeoutMs = backtestTimeoutMs;
        }

        public int getResponseMaxInMemoryMb() {
            return responseMaxInMemoryMb;
        }

        public void setResponseMaxInMemoryMb(int responseMaxInMemoryMb) {
            this.responseMaxInMemoryMb = responseMaxInMemoryMb;
        }

        public String getModelVersion() {
            return modelVersion;
        }

        public void setModelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
        }

        public boolean isFallbackToJavaRules() {
            return fallbackToJavaRules;
        }

        public void setFallbackToJavaRules(boolean fallbackToJavaRules) {
            this.fallbackToJavaRules = fallbackToJavaRules;
        }

        public String getDecisionDeadline() {
            return decisionDeadline;
        }

        public void setDecisionDeadline(String decisionDeadline) {
            this.decisionDeadline = decisionDeadline;
        }

        public int getMaxBatchHoldings() {
            return maxBatchHoldings;
        }

        public void setMaxBatchHoldings(int maxBatchHoldings) {
            this.maxBatchHoldings = maxBatchHoldings;
        }

        public int getMaxBacktestFunds() {
            return maxBacktestFunds;
        }

        public void setMaxBacktestFunds(int maxBacktestFunds) {
            this.maxBacktestFunds = maxBacktestFunds;
        }

        public int getMaxParamGrid() {
            return maxParamGrid;
        }

        public void setMaxParamGrid(int maxParamGrid) {
            this.maxParamGrid = maxParamGrid;
        }

        public BigDecimal getBuyThreshold() {
            return buyThreshold;
        }

        public void setBuyThreshold(BigDecimal buyThreshold) {
            this.buyThreshold = buyThreshold;
        }

        public BigDecimal getSellThreshold() {
            return sellThreshold;
        }

        public void setSellThreshold(BigDecimal sellThreshold) {
            this.sellThreshold = sellThreshold;
        }

        public BigDecimal getMaxSinglePositionRate() {
            return maxSinglePositionRate;
        }

        public void setMaxSinglePositionRate(BigDecimal maxSinglePositionRate) {
            this.maxSinglePositionRate = maxSinglePositionRate;
        }

        public BigDecimal getBuyStepRatio() {
            return buyStepRatio;
        }

        public void setBuyStepRatio(BigDecimal buyStepRatio) {
            this.buyStepRatio = buyStepRatio;
        }

        public BigDecimal getSellStepRatio() {
            return sellStepRatio;
        }

        public void setSellStepRatio(BigDecimal sellStepRatio) {
            this.sellStepRatio = sellStepRatio;
        }

        public BigDecimal getTakeProfitRate() {
            return takeProfitRate;
        }

        public void setTakeProfitRate(BigDecimal takeProfitRate) {
            this.takeProfitRate = takeProfitRate;
        }

        public BigDecimal getStopLossRate() {
            return stopLossRate;
        }

        public void setStopLossRate(BigDecimal stopLossRate) {
            this.stopLossRate = stopLossRate;
        }

        public int getMinNavSamples() {
            return minNavSamples;
        }

        public void setMinNavSamples(int minNavSamples) {
            this.minNavSamples = minNavSamples;
        }

        public int getWarmupDays() {
            return warmupDays;
        }

        public void setWarmupDays(int warmupDays) {
            this.warmupDays = warmupDays;
        }

        public BigDecimal getTrendHoldReturn20d() {
            return trendHoldReturn20d;
        }

        public void setTrendHoldReturn20d(BigDecimal trendHoldReturn20d) {
            this.trendHoldReturn20d = trendHoldReturn20d;
        }

        public BigDecimal getTrendHoldMa20Deviation() {
            return trendHoldMa20Deviation;
        }

        public void setTrendHoldMa20Deviation(BigDecimal trendHoldMa20Deviation) {
            this.trendHoldMa20Deviation = trendHoldMa20Deviation;
        }
    }

    public static class Scheduler {
        private boolean enabled = true;
        private boolean screenerEnabled = false;
        private String holidays = "";
        private String hongKongHolidays = "";
        private String usHolidays = "";
        @Min(0)
        private int batchDelayMaxMillis = 800;
        @Min(1)
        private int aiFocusHoldingLimit = 200;
        @Min(1)
        private int snapshotBackfillTradingDays = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isScreenerEnabled() {
            return screenerEnabled;
        }

        public void setScreenerEnabled(boolean screenerEnabled) {
            this.screenerEnabled = screenerEnabled;
        }

        public String getHolidays() {
            return holidays;
        }

        public void setHolidays(String holidays) {
            this.holidays = holidays;
        }

        public String getHongKongHolidays() {
            return hongKongHolidays;
        }

        public void setHongKongHolidays(String hongKongHolidays) {
            this.hongKongHolidays = hongKongHolidays;
        }

        public String getUsHolidays() {
            return usHolidays;
        }

        public void setUsHolidays(String usHolidays) {
            this.usHolidays = usHolidays;
        }

        public int getBatchDelayMaxMillis() {
            return batchDelayMaxMillis;
        }

        public void setBatchDelayMaxMillis(int batchDelayMaxMillis) {
            this.batchDelayMaxMillis = batchDelayMaxMillis;
        }

        public int getAiFocusHoldingLimit() {
            return aiFocusHoldingLimit;
        }

        public void setAiFocusHoldingLimit(int aiFocusHoldingLimit) {
            this.aiFocusHoldingLimit = aiFocusHoldingLimit;
        }

        public int getSnapshotBackfillTradingDays() {
            return snapshotBackfillTradingDays;
        }

        public void setSnapshotBackfillTradingDays(int snapshotBackfillTradingDays) {
            this.snapshotBackfillTradingDays = snapshotBackfillTradingDays;
        }
    }
}
