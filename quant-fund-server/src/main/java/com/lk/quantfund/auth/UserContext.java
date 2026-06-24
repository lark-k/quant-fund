package com.lk.quantfund.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;

public final class UserContext {

    private UserContext() {
    }

    public static Long getUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(String.valueOf(loginId));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录身份格式不合法");
        }
    }

    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    public static void checkLogin() {
        StpUtil.checkLogin();
    }
}

