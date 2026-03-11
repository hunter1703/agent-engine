package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a reusable builder preset for a configuration type.
 *
 * <p>Each preset contributes a labeled partial configuration value that the UI can offer as a
 * starting point.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(UiPresets.class)
public @interface UiPreset {
  /**
   * Stable preset identifier used by the UI and API contract.
   *
   * @return preset id
   */
  String id();

  /**
   * Human-readable preset label.
   *
   * @return preset label
   */
  String label();

  /**
   * Optional description that explains when the preset should be used.
   *
   * @return preset description
   */
  String description() default "";

  /**
   * Whether this preset should be treated as the default suggestion.
   *
   * @return {@code true} when the preset is the default
   */
  boolean isDefault() default false;

  /**
   * Partial preset configuration encoded as a JSON object string.
   *
   * @return JSON preset payload applied when the preset is selected
   */
  String preset() default "{}";
}
