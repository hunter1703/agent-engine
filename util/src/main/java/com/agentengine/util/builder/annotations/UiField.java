package com.agentengine.util.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for inclusion in the builder definition and supplies presentation metadata.
 *
 * <p>A field is only exposed in the builder if it carries this annotation or a widget annotation
 * ({@link UiText}, {@link UiBoolean}, {@link UiNumber}, {@link UiSelect}, {@link UiLookup},
 * {@link UiDynamicSchema}). Without at least one of these, the field is excluded entirely.
 *
 * <p>When present without a widget annotation, the field is rendered schema-driven (no explicit
 * widget) — the UI uses the JSON Schema shape to decide how to display it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiField {

  /** Human-readable label. Defaults to the humanized field name when blank. */
  String label() default "";

  /** Relative ordering within the section. Lower values render first. */
  int order() default 100;

  /** When true the UI may collapse or de-emphasize the field by default. */
  boolean advanced() default false;

  /**
   * Overrides the step that this field belongs to. When blank, inherits from {@link UiGroup} on
   * the declaring class, or falls back to {@code "general"}.
   */
  String step() default "";

  /**
   * Overrides the section within the step. When blank, inherits from {@link UiGroup} on the
   * declaring class, or falls back to {@code "general"}.
   */
  String section() default "";
}
