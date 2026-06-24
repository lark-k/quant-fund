package com.lk.quantfund.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 64, message = "用户名长度必须在 4 到 64 之间")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "用户名只能包含字母、数字、下划线和短横线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度必须在 8 到 64 之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称长度不能超过 64")
        String nickname,

        @Size(max = 32, message = "手机号长度不能超过 32")
        String phone,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email
) {
}

