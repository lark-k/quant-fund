package com.lk.quantfund.vo.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record MarketSessionStatusVO(
        String primaryStatusText,
        Boolean trading,
        LocalDateTime updateTime,
        List<MarketSessionItemVO> markets
) {
}
