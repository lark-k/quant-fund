package com.lk.quantfund.util;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SensitiveDataMaskUtil {

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "oldpassword",
            "newpassword",
            "passwordhash",
            "token",
            "tokenvalue",
            "authorization",
            "secret",
            "apikey",
            "api_key"
    );

    private SensitiveDataMaskUtil() {
    }

    public static Object mask(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof CharSequence text) {
            return maskMessage(text.toString());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> masked.put(String.valueOf(key), isSensitiveKey(String.valueOf(key)) ? MASK : mask(mapValue)));
            return masked;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(SensitiveDataMaskUtil::mask).toList();
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] masked = new Object[length];
            for (int i = 0; i < length; i++) {
                masked[i] = mask(Array.get(value, i));
            }
            return masked;
        }
        if (value.getClass().isRecord()) {
            return maskRecord(value);
        }
        return value.getClass().getSimpleName();
    }

    public static String maskMessage(String message) {
        if (message == null) {
            return null;
        }
        String masked = message;
        for (String key : SENSITIVE_KEYS) {
            masked = masked.replaceAll("(?i)(" + key + "\\s*[=:]\\s*)[^,;\\s}]+", "$1" + MASK);
        }
        return masked;
    }

    private static Map<String, Object> maskRecord(Object record) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(record);
                masked.put(component.getName(), isSensitiveKey(component.getName()) ? MASK : mask(value));
            } catch (ReflectiveOperationException exception) {
                masked.put(component.getName(), "unreadable");
            }
        }
        return masked;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized);
    }
}
