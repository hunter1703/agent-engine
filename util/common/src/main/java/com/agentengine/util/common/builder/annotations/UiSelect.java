package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Renders the field as a finite-choice selector (dropdown).
 *
 * <p>Options are derived from the supplied enum's constants, with {@code UNKNOWN} filtered out.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiSelect {
  /** Enum whose constants supply the selectable options. */
  Class<? extends Enum<?>> enumType();
}
