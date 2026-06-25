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
    private final Scheduler scheduler = new Scheduler();

    public Ai getAi() {
        return ai;
    }

    public FundDataSource getFundDataSource() {
        return fundDataSource;
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
