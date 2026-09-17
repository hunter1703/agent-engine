package com.agentengine.util.common.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Describes the schema of a tool call argument: an execute-method parameter or object field. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.METHOD,
  ElementType.PARAMETER,
  ElementType.FIELD,
  ElementType.RECORD_COMPONENT
})
public @interface ToolArg {
  String name() default "";

  String description() default "";

  boolean optional() default false;

  String[] enums() default {};
}
