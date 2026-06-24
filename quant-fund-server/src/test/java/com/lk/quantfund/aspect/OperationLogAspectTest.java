package com.lk.quantfund.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.entity.OperationLogEntity;
import com.lk.quantfund.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class OperationLogAspectTest {

    private final OperationLogService operationLogService = mock(OperationLogService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final OperationLogAspect aspect = new OperationLogAspect(operationLogService, request, new ObjectMapper());

    @Test
    void shouldMaskSensitiveRequestResponseAndFailureMessage() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor(
                "saveResponse",
                new Object[] {new LoginLikeRequest("quant", "plain-password", "deepseek-secret")}
        );
        whenRequestMeta();
        when(joinPoint.proceed()).thenReturn(Map.of("tokenValue", "server-token", "nickname", "Quant User"));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::isLogin).thenReturn(true);
            userContext.when(UserContext::getUserId).thenReturn(42L);
            assertThat(aspect.recordOperation(joinPoint)).isEqualTo(Map.of("tokenValue", "server-token", "nickname", "Quant User"));
        }

        OperationLogEntity entity = capturedLog();
        assertThat(entity.getUserId()).isEqualTo(42L);
        assertThat(entity.getModule()).isEqualTo("auth");
        assertThat(entity.getAction()).isEqualTo("login");
        assertThat(entity.getBizType()).isEqualTo("AUTH");
        assertThat(entity.getRequestParams()).contains("\"password\":\"***\"", "\"apiKey\":\"***\"");
        assertThat(entity.getRequestParams()).doesNotContain("plain-password", "deepseek-secret");
        assertThat(entity.getResponseResult()).contains("\"tokenValue\":\"***\"", "\"nickname\":\"Quant User\"");
        assertThat(entity.getResponseResult()).doesNotContain("server-token");
        assertThat(entity.getSuccess()).isEqualTo(1);
    }

    @Test
    void shouldRecordMaskedFailureAndRethrowBusinessException() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor("saveResponse", new Object[] {});
        whenRequestMeta();
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("password=plain token=secret"));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::isLogin).thenReturn(false);
            assertThatThrownBy(() -> aspect.recordOperation(joinPoint))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("password=plain token=secret");
        }

        OperationLogEntity entity = capturedLog();
        assertThat(entity.getUserId()).isNull();
        assertThat(entity.getSuccess()).isEqualTo(0);
        assertThat(entity.getErrorMessage()).isEqualTo("password=*** token=***");
    }

    @Test
    void shouldNotBlockBusinessResultWhenLogSaveFails() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor("withoutResponse", new Object[] {"password=plain"});
        whenRequestMeta();
        when(joinPoint.proceed()).thenReturn("ok");
        doThrow(new IllegalStateException("database unavailable")).when(operationLogService).save(Mockito.any());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::isLogin).thenReturn(false);
            assertThat(aspect.recordOperation(joinPoint)).isEqualTo("ok");
        }
    }

    private void whenRequestMeta() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getHeader("User-Agent")).thenReturn("unit-test");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    private OperationLogEntity capturedLog() {
        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(operationLogService).save(captor.capture());
        return captor.getValue();
    }

    private static ProceedingJoinPoint joinPointFor(String methodName, Object[] args) {
        try {
            TestTarget target = new TestTarget();
            Method method = TestTarget.class.getMethod(methodName);
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            MethodSignature signature = mock(MethodSignature.class);
            when(signature.getMethod()).thenReturn(method);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(joinPoint.getTarget()).thenReturn(target);
            when(joinPoint.getArgs()).thenReturn(args);
            return joinPoint;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record LoginLikeRequest(String username, String password, String apiKey) {
    }

    static class TestTarget {
        @OperationLog(module = "auth", action = "login", bizType = "AUTH", saveResponse = true)
        public Object saveResponse() {
            return "ok";
        }

        @OperationLog(module = "auth", action = "logout")
        public Object withoutResponse() {
            return "ok";
        }
    }
}
