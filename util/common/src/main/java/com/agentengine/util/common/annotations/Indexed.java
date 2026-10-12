package com.agentengine.util.common.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Indexed {

  // if the field is a complex object, and type is FileDetails, then the file's content would be
  // vectorized
  // all other complex types would be ignored
  boolean vector() default false;

  String name() default "";
}
