package com.lk.quantfund.enums;

public enum ErrorCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "登录状态无效或已过期"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    REPEAT_SUBMIT(409, "请勿重复提交"),
    RATE_LIMITED(429, "请求过于频繁"),
    BUSINESS_ERROR(10000, "业务处理失败"),
    EXTERNAL_API_ERROR(11000, "外部数据源调用失败"),
    AI_RESPONSE_INVALID(12000, "AI 返回格式不合法"),
    INTERNAL_ERROR(500, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

