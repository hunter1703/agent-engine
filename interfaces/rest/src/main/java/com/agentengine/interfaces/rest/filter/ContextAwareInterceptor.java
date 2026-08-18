package com.agentengine.interfaces.rest.filter;

import com.agentengine.util.common.context.Context;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Re-enters the {@link Context} parked by {@link AuthFilter} in {@link RequestContextProvider},
 * wrapping the resource method invocation — the one point in the request pipeline that owns the
 * call as a single block — so {@code Context.current()} is visible for its whole body.
 */
@ContextAware
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class ContextAwareInterceptor {

  private final RequestContextProvider requestContextProvider;

  @Inject
  public ContextAwareInterceptor(final RequestContextProvider requestContextProvider) {
    this.requestContextProvider = requestContextProvider;
  }

  @AroundInvoke
  public Object aroundInvoke(final InvocationContext invocationContext) throws Exception {
    final Context context = requestContextProvider.get();
    if (context == null) {
      return invocationContext.proceed();
    }
    return context.call(invocationContext::proceed);
  }
}
