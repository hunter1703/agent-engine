package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Renders the field as an async catalog lookup selector.
 *
 * <p>On collection fields ({@code List}, {@code Set}, arrays) the selection mode is automatically
 * set to {@code "multiple"}; on scalar fields it is {@code "single"}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiLookup {

  /** Asset type to query through the resource catalog API. */
  String assetType();
}
