package com.agentengine.engine.api.tools.annotations;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Stereotype;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a CDI-managed tool eligible for auto-discovery. */
@Documented
@Dependent
@Retention(RetentionPolicy.RUNTIME)
@Stereotype
@Target(ElementType.TYPE)
public @interface DiscoverableTool {}
