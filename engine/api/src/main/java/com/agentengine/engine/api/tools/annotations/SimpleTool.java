package com.agentengine.engine.api.tools.annotations;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Stereotype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Dependent
@Retention(RetentionPolicy.RUNTIME)
@Stereotype
@Target(ElementType.TYPE)
/*
 * Marker annotation for simple tools. A simple tool is a tool that can be instantiated by the framework without any additional configuration. It must have a no-args constructor.
 */
public @interface SimpleTool {
}
