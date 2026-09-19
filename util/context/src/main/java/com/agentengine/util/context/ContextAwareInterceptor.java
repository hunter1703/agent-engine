package com.agentengine.util.context;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

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
