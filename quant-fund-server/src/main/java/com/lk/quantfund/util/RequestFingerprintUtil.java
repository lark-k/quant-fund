package com.lk.quantfund.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestFingerprintUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RequestFingerprintUtil() {
    }

    public static String fingerprint(HttpServletRequest request, Object[] args) {
        String payload = request.getMethod() + "|" + request.getRequestURI() + "|" + safeArgs(args);
        return sha256(payload);
    }

    private static String safeArgs(Object[] args) {
        try {
            return OBJECT_MAPPER.writeValueAsString(SensitiveDataMaskUtil.mask(filterServletObjects(args)));
        } catch (JsonProcessingException exception) {
            return "unserializable";
        }
    }

    private static Object[] filterServletObjects(Object[] args) {
        if (args == null) {
            return new Object[0];
        }
        Object[] filtered = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            filtered[i] = (arg instanceof ServletRequest || arg instanceof ServletResponse) ? arg.getClass().getName() : arg;
        }
        return filtered;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}

