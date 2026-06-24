package com.lk.quantfund.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "账号标识不能为空")
        @Size(max = 128, message = "账号标识长度不能超过 128")
        String account,

        @NotBlank(message = "验证码不能为空")
        @Size(max = 16, message = "验证码长度不能超过 16")
        String verifyCode
) {
}

