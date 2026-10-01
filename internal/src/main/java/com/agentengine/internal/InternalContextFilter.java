package com.agentengine.internal;

import com.agentengine.util.context.Context;
import com.agentengine.util.context.RequestContextProvider;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

/** Runs every internal request as the system, unless an endpoint binds a context of its own. */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class InternalContextFilter implements ContainerRequestFilter {

  private final RequestContextProvider requestContextProvider;

  @Inject
  public InternalContextFilter(final RequestContextProvider requestContextProvider) {
    this.requestContextProvider = requestContextProvider;
  }

  @Override
  public void filter(final ContainerRequestContext requestContext) {
    requestContextProvider.set(Context.asSystemCustomer());
  }
}
