package com.lk.quantfund.aspect;

import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.RedisKeyConstants;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
@Order(25)
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;
    private final HttpServletRequest request;

    public RateLimitAspect(StringRedisTemplate redisTemplate, HttpServletRequest request) {
        this.redisTemplate = redisTemplate;
        this.request = request;
    }

    @Around("@within(com.lk.quantfund.annotation.RateLimit) || @annotation(com.lk.quantfund.annotation.RateLimit)")
    public Object limit(ProceedingJoinPoint joinPoint) throws Throwable {
        RateLimit rateLimit = AnnotationResolver.find(joinPoint, RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }
        String scope = rateLimit.userScoped() ? String.valueOf(UserContext.getUserId()) : "global";
        String businessKey = StringUtils.hasText(rateLimit.key()) ? rateLimit.key() : request.getRequestURI();
        String redisKey = RedisKeyConstants.rateLimitKey(scope, businessKey);
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(rateLimit.windowSeconds()));
        }
        if (count != null && count > rateLimit.maxRequests()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return joinPoint.proceed();
    }
}

