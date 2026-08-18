package com.agentengine.interfaces.rest.filter;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.context.Context;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final RequestContextProvider requestContextProvider;

  @Inject
  public AuthFilter(final RequestContextProvider requestContextProvider) {
    this.requestContextProvider = requestContextProvider;
  }

  @Override
  public void filter(final ContainerRequestContext requestContext) {
    final String headerRequestId = requestContext.getHeaderString(REQUEST_ID_HEADER);
    final String requestId =
        StringUtils.isNotBlank(headerRequestId) ? headerRequestId : UUID.randomUUID().toString();
    requestContextProvider.set(new Context(requestId));
  }

  @Override
  public void filter(
      final ContainerRequestContext requestContext,
      final ContainerResponseContext responseContext) {
    responseContext.getHeaders().add(REQUEST_ID_HEADER, requestContextProvider.get().requestId());
  }
}
