package com.agentengine.interfaces.rest.filter;

import com.agentengine.util.common.context.Context;
import jakarta.enterprise.context.RequestScoped;

/**
 * Bridges the {@link Context} resolved by {@link AuthFilter} — which sets it up in one method and
 * tears it down in another, so it can't express the request as a single block — to {@link
 * ContextAwareInterceptor}, which does own the resource method invocation as one block and
 * re-enters {@code Context} there.
 */
@RequestScoped
public class RequestContextProvider {

  private Context context;

  public void set(final Context context) {
    this.context = context;
  }

  public Context get() {
    return context;
  }
}
