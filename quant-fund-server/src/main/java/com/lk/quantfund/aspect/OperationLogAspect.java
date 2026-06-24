package com.lk.quantfund.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.entity.OperationLogEntity;
import com.lk.quantfund.service.OperationLogService;
import com.lk.quantfund.util.ClientIpUtil;
import com.lk.quantfund.util.SensitiveDataMaskUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(40)
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogService operationLogService;
    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogService operationLogService, HttpServletRequest request, ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        this.request = request;
        this.objectMapper = objectMapper;
    }

    @Around("@within(com.lk.quantfund.annotation.OperationLog) || @annotation(com.lk.quantfund.annotation.OperationLog)")
    public Object recordOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        OperationLog operationLog = AnnotationResolver.find(joinPoint, OperationLog.class);
        if (operationLog == null) {
            return joinPoint.proceed();
        }

        long start = System.currentTimeMillis();
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            saveLogSafely(joinPoint, operationLog, result, failure, System.currentTimeMillis() - start);
        }
    }

    private void saveLogSafely(ProceedingJoinPoint joinPoint, OperationLog operationLog, Object result, Throwable failure, long costTimeMs) {
        try {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setUserId(UserContext.isLogin() ? UserContext.getUserId() : null);
            entity.setModule(operationLog.module());
            entity.setAction(operationLog.action());
            entity.setBizType(operationLog.bizType());
            entity.setRequestMethod(request.getMethod());
            entity.setRequestUri(request.getRequestURI());
            entity.setRequestParams(toJson(SensitiveDataMaskUtil.mask(joinPoint.getArgs())));
            entity.setResponseResult(operationLog.saveResponse() ? toJson(SensitiveDataMaskUtil.mask(result)) : null);
            entity.setIp(ClientIpUtil.getClientIp(request));
            entity.setUserAgent(request.getHeader("User-Agent"));
            entity.setSuccess(failure == null ? 1 : 0);
            entity.setErrorMessage(failure == null ? null : SensitiveDataMaskUtil.maskMessage(failure.getMessage()));
            entity.setCostTimeMs(costTimeMs);
            LocalDateTime now = LocalDateTime.now();
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setDeleted(0);
            operationLogService.save(entity);
        } catch (Exception exception) {
            log.warn("Operation log save failed: {}", exception.getMessage());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "\"serialize_failed\"";
        }
    }
}

