package com.lk.quantfund.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lk.quantfund.annotation.DataScope;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.service.ResourceOwnerService;
import java.lang.reflect.Method;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class DataScopeAspectTest {

    private final ResourceOwnerService resourceOwnerService = mock(ResourceOwnerService.class);
    private final DataScopeAspect aspect = new DataScopeAspect(resourceOwnerService);

    @Test
    void shouldProceedWhenCurrentUserOwnsResource() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor("readHolding", new Object[] {7L}, new String[] {"holdingId"});
        when(resourceOwnerService.findOwnerUserId(ResourceType.FUND_HOLDING, 7L)).thenReturn(Optional.of(42L));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result;
        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            result = aspect.checkDataScope(joinPoint);
        }

        assertThat(result).isEqualTo("ok");
        verify(resourceOwnerService).findOwnerUserId(ResourceType.FUND_HOLDING, 7L);
    }

    @Test
    void shouldRejectWhenResourceBelongsToAnotherUser() {
        ProceedingJoinPoint joinPoint = joinPointFor("readHolding", new Object[] {7L}, new String[] {"holdingId"});
        when(resourceOwnerService.findOwnerUserId(ResourceType.FUND_HOLDING, 7L)).thenReturn(Optional.of(99L));

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            assertThatThrownBy(() -> aspect.checkDataScope(joinPoint))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Test
    void shouldRejectWhenResourceDoesNotExist() {
        ProceedingJoinPoint joinPoint = joinPointFor("deleteTrade", new Object[] {"18"}, new String[] {"id"});
        when(resourceOwnerService.findOwnerUserId(ResourceType.TRADE_RECORD, 18L)).thenReturn(Optional.empty());

        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(42L);
            assertThatThrownBy(() -> aspect.checkDataScope(joinPoint))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }
    }

    @Test
    void shouldSkipOwnerLookupForCreateOperation() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPointFor("createHolding", new Object[] {}, new String[] {});
        when(joinPoint.proceed()).thenReturn("created");

        assertThat(aspect.checkDataScope(joinPoint)).isEqualTo("created");
        verify(resourceOwnerService, never()).findOwnerUserId(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldRejectWhenIdParameterIsMissing() {
        ProceedingJoinPoint joinPoint = joinPointFor("readHolding", new Object[] {7L}, new String[] {"id"});

        assertThatThrownBy(() -> aspect.checkDataScope(joinPoint))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    private static ProceedingJoinPoint joinPointFor(String methodName, Object[] args, String[] parameterNames) {
        try {
            TestTarget target = new TestTarget();
            Method method = TestTarget.class.getMethod(methodName, parameterTypes(args.length));
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            MethodSignature signature = mock(MethodSignature.class);
            when(signature.getMethod()).thenReturn(method);
            when(signature.getParameterNames()).thenReturn(parameterNames);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(joinPoint.getTarget()).thenReturn(target);
            when(joinPoint.getArgs()).thenReturn(args);
            return joinPoint;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Class<?>[] parameterTypes(int argCount) {
        if (argCount == 0) {
            return new Class<?>[] {};
        }
        Class<?>[] types = new Class<?>[argCount];
        for (int i = 0; i < argCount; i++) {
            types[i] = Object.class;
        }
        return types;
    }

    static class TestTarget {
        @DataScope(resourceType = ResourceType.FUND_HOLDING, idParam = "holdingId", operation = DataOperation.READ)
        public Object readHolding(Object holdingId) {
            return "ok";
        }

        @DataScope(resourceType = ResourceType.TRADE_RECORD, operation = DataOperation.DELETE)
        public Object deleteTrade(Object id) {
            return "deleted";
        }

        @DataScope(resourceType = ResourceType.FUND_HOLDING, operation = DataOperation.CREATE)
        public Object createHolding() {
            return "created";
        }
    }
}
