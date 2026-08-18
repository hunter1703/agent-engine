package com.agentengine.interfaces.rest.filter;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a REST resource class or method whose body — and anything it calls synchronously — should
 * see the request's {@link com.agentengine.util.common.context.Context} via {@code
 * Context.current()}. Applied by {@link ContextAwareInterceptor}.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ContextAware {}
