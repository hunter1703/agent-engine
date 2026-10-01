package com.agentengine.util.common.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An entity whose access is controlled: by the grants on it and by roles on every asset. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Permissioned {

  /** The class its grants and roles name. */
  String assetClass();
}
