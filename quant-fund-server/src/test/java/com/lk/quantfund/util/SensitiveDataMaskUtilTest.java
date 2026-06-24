package com.lk.quantfund.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskUtilTest {

    @Test
    void shouldMaskSensitiveMapValues() {
        Object masked = SensitiveDataMaskUtil.mask(Map.of(
                "username", "quant",
                "password", "QuantFund2026",
                "tokenValue", "secret-token"
        ));

        assertThat(masked).isEqualTo(Map.of(
                "username", "quant",
                "password", "***",
                "tokenValue", "***"
        ));
    }

    @Test
    void shouldMaskSensitiveMessage() {
        String masked = SensitiveDataMaskUtil.maskMessage("password=QuantFund2026 token=abc");

        assertThat(masked).isEqualTo("password=*** token=***");
    }
}

