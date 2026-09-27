package com.agentengine.util.agents.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes the schema of a {@link ToolConstructor} parameter used to configure a tool instance.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {
  String name() default "";

  String description() default "";

  boolean optional() default false;

  String[] enums() default {};
}
