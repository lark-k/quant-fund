package com.lk.quantfund.common;

import java.util.List;

public record PageResponse<T>(
        long pageNo,
        long pageSize,
        long total,
        List<T> records
) {

    public static <T> PageResponse<T> of(long pageNo, long pageSize, long total, List<T> records) {
        return new PageResponse<>(pageNo, pageSize, total, records);
    }
}

