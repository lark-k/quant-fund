package com.lk.quantfund.aspect;

import cn.dev33.satoken.stp.StpUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(10)
public class RequireLoginAspect {

    @Around("@within(com.lk.quantfund.annotation.RequireLogin) || @annotation(com.lk.quantfund.annotation.RequireLogin)")
    public Object checkLogin(ProceedingJoinPoint joinPoint) throws Throwable {
        StpUtil.checkLogin();
        return joinPoint.proceed();
    }
}

