package com.lk.quantfund.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.lk.quantfund.enums.FundType;
import org.junit.jupiter.api.Test;

class FundClassificationServiceTest {

    private final FundClassificationService service = new FundClassificationService();

    @Test
    void classifyShouldDetectEtfLinkBeforeEtf() {
        String result = service.classify("沪深300ETF联接A", null, false, null);

        assertThat(result).isEqualTo(FundType.ETF_LINK.name());
    }

    @Test
    void classifyShouldUseTrackingIndexAsIndexSignal() {
        String result = service.classify("中证红利A", "UNKNOWN", false, "中证红利指数");

        assertThat(result).isEqualTo(FundType.INDEX.name());
    }

    @Test
    void classifyShouldKeepActiveEquityWhenActiveFund() {
        String result = service.classify("主动精选混合", null, true, null);

        assertThat(result).isEqualTo(FundType.ACTIVE_EQUITY.name());
    }
}

