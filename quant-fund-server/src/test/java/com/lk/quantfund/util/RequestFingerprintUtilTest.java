package com.lk.quantfund.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class RequestFingerprintUtilTest {

    @Test
    void shouldCreateStableFingerprintForSameRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");

        String first = RequestFingerprintUtil.fingerprint(request, new Object[]{"same"});
        String second = RequestFingerprintUtil.fingerprint(request, new Object[]{"same"});

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }
}

