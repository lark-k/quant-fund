package com.lk.quantfund.vo.auth;

public record LoginVO(
        String tokenName,
        String tokenValue,
        long tokenTimeout,
        UserProfileVO user
) {
}

