package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Renders the field as a finite-choice selector (dropdown).
 *
 * <p>When {@code enumType} is specified, options are derived from that enum's constants (with
 * {@code UNKNOWN} filtered out). When omitted, options are inferred from the field's own enum type
 * via the generated JSON Schema.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiSelect {

  /** Sentinel used when no explicit enum source is configured. */
  enum NoEnum {
    SENTINEL
  }

  /**
   * Enum whose constants supply the selectable options. Leave unset to infer options from the
   * field's own enum type via the JSON Schema.
   */
  Class<? extends Enum<?>> enumType() default NoEnum.class;
}
