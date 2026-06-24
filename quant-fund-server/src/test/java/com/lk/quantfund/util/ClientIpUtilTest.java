package com.lk.quantfund.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class ClientIpUtilTest {

    @Test
    void shouldPreferForwardedIpHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.1.1.1, 10.1.1.2");

        assertThat(ClientIpUtil.getClientIp(request)).isEqualTo("10.1.1.1");
    }

    @Test
    void shouldFallbackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(ClientIpUtil.getClientIp(request)).isEqualTo("127.0.0.1");
    }
}

