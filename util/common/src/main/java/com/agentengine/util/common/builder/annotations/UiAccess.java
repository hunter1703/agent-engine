package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares mode-specific access rules for a builder field.
 *
 * <p>
 * The generated builder definition uses these values to control whether a field
 * is hidden, read-only, editable, or required in create, edit, and view flows.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiAccess {
  /**
   * Access level to apply when rendering or validating the field in create mode.
   *
   * @return create-mode access level
   */
  UiAccessLevel create() default UiAccessLevel.EDITABLE;

  /**
   * Access level to apply when rendering or validating the field in edit mode.
   *
   * @return edit-mode access level
   */
  UiAccessLevel edit() default UiAccessLevel.EDITABLE;

  /**
   * Access level to apply when rendering the field in view mode.
   *
   * @return view-mode access level
   */
  UiAccessLevel view() default UiAccessLevel.READ_ONLY;
}
