package com.agentengine.interfaces.rest.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Assigns a {@code requestId} to every incoming request and stores it in the logging MDC for the
 * lifetime of the request, so that all downstream logging (including outbound microservice calls in
 * {@code MicroServiceInvocationHandler}) can be correlated back to it.
 */
@Provider
@PreMatching
@Priority(Priorities.HEADER_DECORATOR)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String REQUEST_ID_MDC_KEY = "requestId";

  @Override
  public void filter(final ContainerRequestContext requestContext) {
    final String requestId = requestContext.getHeaderString(REQUEST_ID_HEADER);
    MDC.put(REQUEST_ID_MDC_KEY, requestId != null ? requestId : UUID.randomUUID().toString());
  }

  @Override
  public void filter(
      final ContainerRequestContext requestContext,
      final ContainerResponseContext responseContext) {
    responseContext.getHeaders().add(REQUEST_ID_HEADER, MDC.get(REQUEST_ID_MDC_KEY));
    MDC.remove(REQUEST_ID_MDC_KEY);
  }
}
