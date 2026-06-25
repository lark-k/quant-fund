package com.lk.quantfund.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.scheduler.TradingCalendarService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OfficialNavTimingTest {

    private final TradingCalendarService tradingCalendarService = new TradingCalendarService(new QuantFundProperties());

    @Test
    void qdiiHangSengTechShouldUseNavDateLikeAshareFunds() {
        FundHolding holding = holding("天弘恒生科技指数(QDII)C", "QDII");
        LocalDate navDate = LocalDate.of(2026, 6, 24);

        assertThat(OfficialNavTiming.isDelayedOfficialNavFund(holding)).isFalse();
        assertThat(OfficialNavTiming.effectiveDate(holding, navDate, tradingCalendarService)).isEqualTo(navDate);
    }

    @Test
    void usMarketQdiiShouldUseNextTradingDayAsEffectiveDate() {
        FundHolding holding = holding("易方达纳斯达克100指数(QDII)美元", "QDII");
        LocalDate navDate = LocalDate.of(2026, 6, 24);

        assertThat(OfficialNavTiming.isDelayedOfficialNavFund(holding)).isTrue();
        assertThat(OfficialNavTiming.effectiveDate(holding, navDate, tradingCalendarService))
                .isEqualTo(tradingCalendarService.nextTradingDay(navDate));
    }

    @Test
    void qdiiTypeAloneShouldNotForceDelayedTiming() {
        FundHolding holding = holding("恒生科技ETF联接(QDII)", "QDII");

        assertThat(OfficialNavTiming.isDelayedOfficialNavFund(holding)).isFalse();
    }

    private FundHolding holding(String fundName, String fundType) {
        FundHolding holding = new FundHolding();
        holding.setFundName(fundName);
        holding.setFundType(fundType);
        return holding;
    }
}
