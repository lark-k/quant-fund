package com.lk.quantfund.datasource.model;

public record FundSearchResultDTO(
        String fundCode,
        String fundName,
        String fundType,
        String pinyin,
        String sourceName
) {
}

