package com.lk.quantfund.constants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RedisKeyConstants {

    public static final String PREFIX = "quantfund";

    private RedisKeyConstants() {
    }

    public static String rateLimitKey(String scope, String key) {
        return PREFIX + ":rate_limit:" + sanitize(scope) + ":" + sha256(key);
    }

    public static String repeatSubmitKey(String userId, String requestHash) {
        return PREFIX + ":repeat_submit:" + sanitize(userId) + ":" + requestHash;
    }

    public static String estimateCacheKey(String fundCode) {
        return PREFIX + ":estimate_cache:" + sanitize(fundCode);
    }

    public static String fundSearchCacheKey(String keyword) {
        return fundSearchCacheKey(keyword, "FUZZY");
    }

    public static String fundSearchCacheKey(String keyword, String mode) {
        return PREFIX + ":fund_search_cache:" + sha256((mode == null ? "FUZZY" : mode) + ":" + keyword);
    }

    public static String fundBasicCacheKey(String fundCode) {
        return PREFIX + ":fund_basic_cache:" + sanitize(fundCode);
    }

    public static String fundNavCacheKey(String fundCode, String startDate, String endDate) {
        return PREFIX + ":fund_nav_cache:" + sanitize(fundCode) + ":" + sha256(startDate + ":" + endDate);
    }

    public static String manualEstimateRefreshKey(String fundCode) {
        return PREFIX + ":estimate_refresh_cooldown:" + sanitize(fundCode);
    }

    public static String aiAnalysisLockKey(Long userId, String fundCode) {
        return PREFIX + ":ai_analysis_lock:" + userId + ":" + sanitize(fundCode);
    }

    private static String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_:-]", "_");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
