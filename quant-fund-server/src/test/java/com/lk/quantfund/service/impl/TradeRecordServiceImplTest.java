package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.lk.quantfund.dto.trade.TradeRecordRequest;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.service.PortfolioAccountService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TradeRecordServiceImplTest {

    private final TradeRecordServiceImpl tradeRecordService = new TradeRecordServiceImpl(
            mock(TradeRecordMapper.class),
            mock(FundHoldingMapper.class),
            mock(PortfolioAccountMapper.class),
            mock(PortfolioAccountService.class)
    );

    @Test
    void createShouldRejectMissingTradeType() {
        TradeRecordRequest request = new TradeRecordRequest(
                1L,
                null,
                "000001",
                "Mock Fund",
                null,
                null,
                new BigDecimal("100.00"),
                null,
                new BigDecimal("1.0000"),
                BigDecimal.ZERO,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> tradeRecordService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("trade type is required");
    }
}

