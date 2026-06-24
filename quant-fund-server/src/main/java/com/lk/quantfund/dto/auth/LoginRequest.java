package com.lk.quantfund.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过 64")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 128, message = "密码长度不能超过 128")
        String password
) {
}

