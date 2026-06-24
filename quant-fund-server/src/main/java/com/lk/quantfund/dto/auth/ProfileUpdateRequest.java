package com.lk.quantfund.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 64, message = "昵称长度不能超过 64")
        String nickname,

        @Size(max = 32, message = "手机号长度不能超过 32")
        String phone,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,

        @Size(max = 512, message = "头像地址长度不能超过 512")
        String avatar
) {
}

