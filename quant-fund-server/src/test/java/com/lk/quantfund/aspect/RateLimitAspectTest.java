package com.lk.quantfund.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.RedisKeyConstants;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RateLimitAspectTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final RateLimitAspect aspect = new RateLimitAspect(redisTemplate, request);

    @Test
    void limitShouldProceedWhenAtomicCounterIsWithinWindow() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor("limited");
        mockCounter("42", "unit:test", "10000", 1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result;
        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            result = aspect.limit(joinPoint);
        }

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void limitShouldRejectWhenRequestCountExceedsLimit() {
        ProceedingJoinPoint joinPoint = joinPointFor("limited");
        mockCounter("42", "unit:test", "10000", 3L);

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            assertThatThrownBy(() -> aspect.limit(joinPoint))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RATE_LIMITED);
        }
    }

    @Test
    void limitShouldUseRequestUriWhenBusinessKeyIsBlank() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor("uriLimited");
        when(request.getRequestURI()).thenReturn("/api/unit");
        mockCounter("42", "/api/unit", "10000", 1L);
        when(joinPoint.proceed()).thenReturn("ok");

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            assertThat(aspect.limit(joinPoint)).isEqualTo("ok");
        }

    }

    @SuppressWarnings("unchecked")
    private void mockCounter(String scope, String businessKey, String windowMillis, Long result) {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(RedisKeyConstants.rateLimitKey(scope, businessKey))),
                eq(windowMillis)
        )).thenReturn(result);
    }

    private static ProceedingJoinPoint joinPointFor(String methodName) {
        try {
            TestTarget target = new TestTarget();
            Method method = TestTarget.class.getMethod(methodName);
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
        @RateLimit(key = "unit:test", windowSeconds = 10, maxRequests = 2)
        public Object limited() {
            return "ok";
        }

        @RateLimit(windowSeconds = 10, maxRequests = 2)
        public Object uriLimited() {
            return "ok";
        }
    }
}
