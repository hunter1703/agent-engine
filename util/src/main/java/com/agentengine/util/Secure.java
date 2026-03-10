package com.agentengine.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a field contains sensitive data and should be encrypted when persisted.
 * This annotation is strictly for persistence-layer encryption and does not affect in-memory structures.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Secure {}
