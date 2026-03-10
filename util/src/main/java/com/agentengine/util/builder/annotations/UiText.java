package com.agentengine.util.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Renders the field as a text input. Set {@code multiline = true} for a textarea. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiText {

  /** When true, renders as a multi-line textarea instead of a single-line input. */
  boolean multiline() default false;

  /** Visible row count for multi-line rendering. Ignored when {@code multiline = false}. */
  int rows() default 4;
}
