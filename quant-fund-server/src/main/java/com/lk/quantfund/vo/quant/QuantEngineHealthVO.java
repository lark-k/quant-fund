package com.lk.quantfund.vo.quant;

public record QuantEngineHealthVO(
        String status,
        String service,
        String modelVersion,
        boolean enabled
) {
}
