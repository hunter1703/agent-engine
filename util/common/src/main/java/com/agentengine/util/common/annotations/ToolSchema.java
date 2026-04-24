package com.agentengine.util.common.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Describes tool schemas for methods, parameters, and object fields. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface ToolSchema {
    String name() default "";

    String description() default "";

    boolean optional() default false;

    String[] enums() default {};
}
