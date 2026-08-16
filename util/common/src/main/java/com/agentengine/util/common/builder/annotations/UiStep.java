package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines a step in the multi-step form wizard.
 *
 * <p>A step represents a major grouping of related fields in the form. Each step can contain one or
 * more sections.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface UiStep {
  /**
   * Unique identifier for the step.
   *
   * <p>This ID must match the step IDs used in {@link UiField#step()}.
   *
   * @return step identifier
   */
  String id();

  /**
   * Human-readable label for the step.
   *
   * <p>This label is displayed in the step indicator/progress bar.
   *
   * @return step label
   */
  String label();

  /**
   * Optional description of the step.
   *
   * @return step description
   */
  String description() default "";

  /**
   * Sort order for the step.
   *
   * <p>Steps are displayed in ascending order. Lower values appear first.
   *
   * @return sort order
   */
  int order();

  /**
   * Optional sections within this step.
   *
   * <p>If a step has multiple sections, they will be rendered as separate groups within the step.
   * If empty or contains only one section, no section headers are shown.
   *
   * @return array of section definitions
   */
  UiSection[] sections() default {};
}
