package com.lk.quantfund.aspect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

final class AnnotationResolver {

    private AnnotationResolver() {
    }

    static <A extends Annotation> A find(ProceedingJoinPoint joinPoint, Class<A> annotationType) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        A methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        Class<?> targetClass = joinPoint.getTarget().getClass();
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, annotationType);
    }
}

