package com.lk.quantfund.datasource.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.lk.quantfund.config.QuantFundProperties;
import org.junit.jupiter.api.Test;

class MockFundDataSourceAdapterTest {

    private final QuantFundProperties properties = new QuantFundProperties();
    private final MockFundDataSourceAdapter adapter = new MockFundDataSourceAdapter(properties);

    @Test
    void shouldBeDisabledByDefaultForRealDatasourceDelivery() {
        assertThat(adapter.enabled()).isFalse();
        properties.getFundDataSource().setMockFallbackEnabled(true);
        assertThat(adapter.enabled()).isTrue();
    }

    @Test
    void shouldSearchLocalMockFunds() {
        assertThat(adapter.searchFunds("白酒"))
                .extracting("fundCode")
                .contains("161725");
    }

    @Test
    void shouldReturnEstimateAsDelayedReferenceData() {
        assertThat(adapter.getIntradayEstimate("000001"))
                .isPresent()
                .get()
                .extracting("delayed")
                .isEqualTo(true);
    }
}
