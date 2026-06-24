package com.lk.quantfund.aspect;

import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.RedisKeyConstants;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.util.RequestFingerprintUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(30)
public class RepeatSubmitAspect {

    private final StringRedisTemplate redisTemplate;
    private final HttpServletRequest request;

    public RepeatSubmitAspect(StringRedisTemplate redisTemplate, HttpServletRequest request) {
        this.redisTemplate = redisTemplate;
        this.request = request;
    }

    @Around("@within(com.lk.quantfund.annotation.RepeatSubmit) || @annotation(com.lk.quantfund.annotation.RepeatSubmit)")
    public Object preventRepeatSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        RepeatSubmit repeatSubmit = AnnotationResolver.find(joinPoint, RepeatSubmit.class);
        if (repeatSubmit == null) {
            return joinPoint.proceed();
        }
        String scope = repeatSubmit.userScoped() ? String.valueOf(UserContext.getUserId()) : "global";
        String fingerprint = RequestFingerprintUtil.fingerprint(request, joinPoint.getArgs());
        String redisKey = RedisKeyConstants.repeatSubmitKey(scope, fingerprint);
        Boolean absent = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofSeconds(repeatSubmit.intervalSeconds()));
        if (!Boolean.TRUE.equals(absent)) {
            throw new BusinessException(ErrorCode.REPEAT_SUBMIT);
        }
        return joinPoint.proceed();
    }
}

