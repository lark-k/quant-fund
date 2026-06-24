package com.lk.quantfund.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.RedisKeyConstants;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
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

class RateLimitAspectTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final RateLimitAspect aspect = new RateLimitAspect(redisTemplate, request);

    @Test
    void limitShouldProceedAndSetWindowOnFirstRequest() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor("limited");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeyConstants.rateLimitKey("42", "unit:test"))).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result;
        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            result = aspect.limit(joinPoint);
        }

        assertThat(result).isEqualTo("ok");
        verify(redisTemplate).expire(RedisKeyConstants.rateLimitKey("42", "unit:test"), Duration.ofSeconds(10));
    }

    @Test
    void limitShouldRejectWhenRequestCountExceedsLimit() {
        ProceedingJoinPoint joinPoint = joinPointFor("limited");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeyConstants.rateLimitKey("42", "unit:test"))).thenReturn(3L);

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
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeyConstants.rateLimitKey("42", "/api/unit"))).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            assertThat(aspect.limit(joinPoint)).isEqualTo("ok");
        }

        verify(valueOperations).increment(eq(RedisKeyConstants.rateLimitKey("42", "/api/unit")));
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
