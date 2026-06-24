package com.lk.quantfund.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.RedisKeyConstants;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.util.RequestFingerprintUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RepeatSubmitAspectTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final RepeatSubmitAspect aspect = new RepeatSubmitAspect(redisTemplate, request);

    @Test
    void preventRepeatSubmitShouldProceedWhenFingerprintIsAbsent() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor();
        Object[] args = new Object[] {"payload"};
        String redisKey = redisKey(args);
        when(joinPoint.getArgs()).thenReturn(args);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(redisKey, "1", Duration.ofSeconds(7))).thenReturn(Boolean.TRUE);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result;
        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            result = aspect.preventRepeatSubmit(joinPoint);
        }

        assertThat(result).isEqualTo("ok");
        verify(valueOperations).setIfAbsent(eq(redisKey), eq("1"), eq(Duration.ofSeconds(7)));
    }

    @Test
    void preventRepeatSubmitShouldRejectDuplicateFingerprint() {
        ProceedingJoinPoint joinPoint = joinPointFor();
        Object[] args = new Object[] {"payload"};
        String redisKey = redisKey(args);
        when(joinPoint.getArgs()).thenReturn(args);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(redisKey, "1", Duration.ofSeconds(7))).thenReturn(Boolean.FALSE);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            assertThatThrownBy(() -> aspect.preventRepeatSubmit(joinPoint))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPEAT_SUBMIT);
        }
    }

    private String redisKey(Object[] args) {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/unit");
        String fingerprint = RequestFingerprintUtil.fingerprint(request, args);
        return RedisKeyConstants.repeatSubmitKey("42", fingerprint);
    }

    private static ProceedingJoinPoint joinPointFor() {
        try {
            TestTarget target = new TestTarget();
            Method method = TestTarget.class.getMethod("submit");
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            MethodSignature signature = mock(MethodSignature.class);
            when(signature.getMethod()).thenReturn(method);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(joinPoint.getTarget()).thenReturn(target);
            return joinPoint;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static class TestTarget {
        @RepeatSubmit(intervalSeconds = 7)
        public Object submit() {
            return "ok";
        }
    }
}
