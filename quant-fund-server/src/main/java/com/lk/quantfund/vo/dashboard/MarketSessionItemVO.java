package com.lk.quantfund.vo.dashboard;

public record MarketSessionItemVO(
        String market,
        String statusText,
        Boolean trading
) {
}
