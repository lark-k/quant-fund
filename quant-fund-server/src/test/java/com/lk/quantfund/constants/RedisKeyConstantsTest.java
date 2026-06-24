package com.lk.quantfund.constants;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisKeyConstantsTest {

    @Test
    void shouldBuildNamespacedRateLimitKey() {
        String key = RedisKeyConstants.rateLimitKey("user:1", "/api/auth/me");

        assertThat(key).startsWith("quantfund:rate_limit:user:1:");
    }

    @Test
    void shouldBuildRepeatSubmitKey() {
        String key = RedisKeyConstants.repeatSubmitKey("1", "abc");

        assertThat(key).isEqualTo("quantfund:repeat_submit:1:abc");
    }

    @Test
    void shouldSeparateFundSearchCacheByMode() {
        String fuzzyKey = RedisKeyConstants.fundSearchCacheKey("白酒", "FUZZY");
        String exactKey = RedisKeyConstants.fundSearchCacheKey("白酒", "EXACT");

        assertThat(fuzzyKey).startsWith("quantfund:fund_search_cache:");
        assertThat(exactKey).startsWith("quantfund:fund_search_cache:");
        assertThat(fuzzyKey).isNotEqualTo(exactKey);
    }
}
