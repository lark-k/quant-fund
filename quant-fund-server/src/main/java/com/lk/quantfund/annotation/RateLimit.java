package com.lk.quantfund.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String key() default "";

    int windowSeconds() default 60;

    int maxRequests() default 60;

    boolean userScoped() default true;
}

