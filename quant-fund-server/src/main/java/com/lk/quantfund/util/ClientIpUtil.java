package com.lk.quantfund.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.StringUtils;

public final class ClientIpUtil {

    private static final List<String> IP_HEADERS = List.of(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    );

    private ClientIpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}

