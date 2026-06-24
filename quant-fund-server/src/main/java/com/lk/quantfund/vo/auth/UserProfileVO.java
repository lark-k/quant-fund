package com.lk.quantfund.vo.auth;

import java.time.LocalDateTime;

public record UserProfileVO(
        Long id,
        String username,
        String nickname,
        String phone,
        String email,
        String avatar,
        String role,
        String status,
        String riskLevel,
        LocalDateTime lastLoginTime
) {
}

