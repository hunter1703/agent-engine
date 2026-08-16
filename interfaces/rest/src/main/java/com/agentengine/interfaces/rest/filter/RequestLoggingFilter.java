package com.agentengine.interfaces.rest.filter;

import io.opentelemetry.api.trace.Span;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class RequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
  private static final String TRACE_ID_HEADER = "X-Trace-ID";

  @Override
  public void filter(final ContainerRequestContext requestContext) {
    LOG.debug(
        "Request received method={} path={}",
        requestContext.getMethod(),
        requestContext.getUriInfo().getPath());
  }

  @Override
  public void filter(
      final ContainerRequestContext requestContext,
      final ContainerResponseContext responseContext) {
    final String traceId = Span.current().getSpanContext().getTraceId();
    if (Span.current().getSpanContext().isValid()) {
      responseContext.getHeaders().add(TRACE_ID_HEADER, traceId);
    }
    LOG.debug("Request completed status={}", responseContext.getStatus());
  }
}
