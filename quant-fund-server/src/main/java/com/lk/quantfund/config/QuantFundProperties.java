package com.lk.quantfund.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
        @NotBlank
        private String modelVersion = "rule-v1.0.0";
        private boolean fallbackToJavaRules = true;
        @NotBlank
        private String decisionDeadline = "15:00:00";
        @Min(1)
        private int maxBatchHoldings = 100;
        @Min(1)
        private int maxBacktestFunds = 1000;
        @Min(1)
        private int maxParamGrid = 100;

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
    }

    public static class Scheduler {
        private boolean enabled = true;
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
