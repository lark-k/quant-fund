package com.lk.quantfund.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.lk.quantfund.entity.FundEstimateIntraday;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class IntradayEstimateFreshnessTest {

    @Test
    void shouldAcceptLatestEstimateFromTodayEvenWhenProviderStoppedUpdating() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 14, 30);
        FundEstimateIntraday recent = estimate(now.minusMinutes(5), 0);
        FundEstimateIntraday earlierToday = estimate(now.toLocalDate().atTime(9, 45), 0);
        FundEstimateIntraday delayed = estimate(now.minusMinutes(1), 1);

        assertThat(IntradayEstimateFreshness.isFresh(recent, now)).isTrue();
        assertThat(IntradayEstimateFreshness.isFresh(earlierToday, now)).isTrue();
        assertThat(IntradayEstimateFreshness.isFresh(delayed, now)).isFalse();
    }

    @Test
    void shouldRejectPreviousDayAndFutureEstimate() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 14, 30);

        assertThat(IntradayEstimateFreshness.isFresh(estimate(now.minusDays(1), 0), now)).isFalse();
        assertThat(IntradayEstimateFreshness.isFresh(estimate(now.plusMinutes(3), 0), now)).isFalse();
    }

    private FundEstimateIntraday estimate(LocalDateTime time, int delayed) {
        FundEstimateIntraday estimate = new FundEstimateIntraday();
        estimate.setFundCode("000001");
        estimate.setEstimateDate(time.toLocalDate());
        estimate.setEstimateTime(time);
        estimate.setDelayed(delayed);
        return estimate;
    }
}
