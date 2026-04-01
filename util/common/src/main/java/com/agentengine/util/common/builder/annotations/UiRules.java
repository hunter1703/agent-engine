package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Container annotation for repeatable {@link UiRule} declarations. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiRules {
    /**
     * Rules declared for the annotated field.
     *
     * @return builder rules
     */
    UiRule[] value();
}
