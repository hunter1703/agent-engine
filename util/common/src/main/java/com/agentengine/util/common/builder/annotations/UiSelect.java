package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Renders the field as a finite-choice selector (dropdown).
 *
 * <p>Options come from either {@link #enumType()} (its constants, with {@code UNKNOWN} filtered
 * out) or {@link #optionsResource()} (a classpath JSON resource holding a flat array of option
 * values) — set exactly one. Prefer {@link #optionsResource()} for a list that's really reference
 * data a non-developer might add to, rather than a fixed set the code itself distinguishes
 * between.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiSelect {
  /** Enum whose constants supply the selectable options. Unset when using {@link #optionsResource()}. */
  Class<? extends Enum<?>> enumType() default NoEnum.class;

  /** Classpath JSON resource holding a flat array of option values. Unset when using {@link #enumType()}. */
  String optionsResource() default "";

  /** The default of {@link #enumType()} when options instead come from {@link #optionsResource()}. */
  enum NoEnum {}
}
