package com.lk.quantfund.aspect;

import com.lk.quantfund.annotation.DataScope;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.service.ResourceOwnerService;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(20)
public class DataScopeAspect {

    private final ResourceOwnerService resourceOwnerService;

    public DataScopeAspect(ResourceOwnerService resourceOwnerService) {
        this.resourceOwnerService = resourceOwnerService;
    }

    @Around("@within(com.lk.quantfund.annotation.DataScope) || @annotation(com.lk.quantfund.annotation.DataScope)")
    public Object checkDataScope(ProceedingJoinPoint joinPoint) throws Throwable {
        DataScope dataScope = AnnotationResolver.find(joinPoint, DataScope.class);
        if (dataScope == null) {
            return joinPoint.proceed();
        }
        if (dataScope.operation() == DataOperation.CREATE) {
            return joinPoint.proceed();
        }

        Long resourceId = resolveResourceId(joinPoint, dataScope.idParam());
        Long currentUserId = UserContext.getUserId();
        Optional<Long> ownerUserId = resourceOwnerService.findOwnerUserId(dataScope.resourceType(), resourceId);
        if (ownerUserId.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!ownerUserId.get().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return joinPoint.proceed();
    }

    private Long resolveResourceId(ProceedingJoinPoint joinPoint, String idParam) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterNames.length; i++) {
            if (idParam.equals(parameterNames[i])) {
                return toLong(args[i], idParam);
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少数据权限校验参数: " + idParam);
    }

    private Long toLong(Object value, String paramName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据权限参数格式不合法: " + paramName);
        }
    }
}
