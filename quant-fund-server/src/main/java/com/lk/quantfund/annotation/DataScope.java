package com.lk.quantfund.annotation;

import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ResourceType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    ResourceType resourceType();

    String idParam() default "id";

    DataOperation operation() default DataOperation.READ;
}

