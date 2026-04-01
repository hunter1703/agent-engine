package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares default step, section, and ordering metadata for all builder fields within a type. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface UiGroup {
    /** Default step id for fields declared by the annotated type. */
    String step() default "general";

    /** Default section id for fields declared by the annotated type. */
    String section() default "general";

    /**
     * Default ordering weight for fields declared by the annotated type.
     *
     * @return sort order where lower values render first
     */
    int order() default 100;
}
