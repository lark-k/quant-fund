package com.lk.quantfund.service.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lk.quantfund.datasource.model.FundThemeDTO;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundValuationServiceTest {

    @Test
    void shouldUseMappedThemesForActiveFundsInsteadOfGenericActiveEquity() {
        FundQueryService fundQueryService = mock(FundQueryService.class);
        when(fundQueryService.getRelatedThemes("016874")).thenReturn(List.of(
                theme("016874", "光通信", "34"),
                theme("016874", "算力租赁", "33"),
                theme("016874", "存储芯片", "33")
        ));
        FundValuationService service = service(fundQueryService);

        FundValuationResult result = service.estimate("016874", "广发远见智选混合C", "MIXED", new BigDecimal("4.52"));

        assertThat(result.themeName()).isEqualTo("光通信/算力租赁/存储芯片");
        assertThat(result.themeRate()).isEqualByComparingTo("4.52");
        assertThat(result.themeName()).doesNotContain("主动权益");
    }

    @Test
    void shouldUsePcbAndCpoForCaitongGrowth() {
        FundQueryService fundQueryService = mock(FundQueryService.class);
        when(fundQueryService.getRelatedThemes("021528")).thenReturn(List.of(
                theme("021528", "PCB", "55"),
                theme("021528", "CPO", "45")
        ));
        FundValuationService service = service(fundQueryService);

        FundValuationResult result = service.estimate("021528", "财通成长优选混合C", "MIXED", new BigDecimal("2.53"));

        assertThat(result.themeName()).isEqualTo("PCB/CPO");
        assertThat(result.themeRate()).isEqualByComparingTo("2.53");
    }

    @Test
    void shouldUseOverseasFundForYifangdaGlobalGrowth() {
        FundQueryService fundQueryService = mock(FundQueryService.class);
        when(fundQueryService.getRelatedThemes("012922")).thenReturn(List.of(
                theme("012922", "海外基金", "100")
        ));
        FundValuationService service = service(fundQueryService);

        FundValuationResult result = service.estimate("012922", "易方达全球成长精选混合(QDII)人民币C", "QDII", new BigDecimal("-7.81"));

        assertThat(result.themeName()).isEqualTo("海外基金");
        assertThat(result.themeRate()).isEqualByComparingTo("-7.81");
    }

    private FundThemeDTO theme(String fundCode, String name, String weight) {
        return new FundThemeDTO(fundCode, name, "FUND_THEME_MAPPING", new BigDecimal(weight), null, "TEST");
    }

    private FundValuationService service(FundQueryService fundQueryService) {
        return new FundValuationService(fundQueryService, new TradingCalendarService(new QuantFundProperties()));
    }
}
