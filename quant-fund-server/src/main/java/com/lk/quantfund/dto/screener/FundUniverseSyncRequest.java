package com.lk.quantfund.dto.screener;

public record FundUniverseSyncRequest(
        String category,
        Boolean forceRefresh
) {
}
