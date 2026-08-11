package com.lk.quantfund.aspect;

import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.RedisKeyConstants;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
@Order(25)
public class RateLimitAspect {

    private static final RedisScript<Long> INCREMENT_WITH_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if not current or ttl < 0 then
                redis.call('SET', KEYS[1], 1, 'PX', ARGV[1])
                return 1
            end
            return redis.call('INCR', KEYS[1])
            """, Long.class);

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
        Long count = redisTemplate.execute(
                INCREMENT_WITH_WINDOW_SCRIPT,
                List.of(redisKey),
                String.valueOf(rateLimit.windowSeconds() * 1000L)
        );
        if (count != null && count > rateLimit.maxRequests()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return joinPoint.proceed();
    }
}
