package com.lk.quantfund.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

    @Test
    void passwordEncoderShouldUseOneWayHashing() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

        String encoded = encoder.encode("QuantFund2026");

        assertThat(encoded).isNotEqualTo("QuantFund2026");
        assertThat(encoder.matches("QuantFund2026", encoded)).isTrue();
        assertThat(encoder.matches("WrongPassword2026", encoded)).isFalse();
    }
}

