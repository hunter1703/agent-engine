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
  private static final String START_TIME_KEY = "start-time";
  private static final String PROCESSING_TIME_HEADER = "X-Backend-Processing-Time-Ms";

  @Override
  public void filter(final ContainerRequestContext requestContext) {
    requestContext.setProperty(START_TIME_KEY, System.nanoTime());
    LOG.debug(
        "Request received method={} path={}",
        requestContext.getMethod(),
        requestContext.getUriInfo().getPath());
  }

  @Override
  public void filter(
      final ContainerRequestContext requestContext,
      final ContainerResponseContext responseContext) {

    final Long startTime = (Long) requestContext.getProperty(START_TIME_KEY);
    final long duration = startTime != null ? System.nanoTime() - startTime : -1;

    if (duration >= 0) {
      responseContext
          .getHeaders()
          .add(PROCESSING_TIME_HEADER, String.valueOf(duration / 1_000_000));
    }

    final String traceId = Span.current().getSpanContext().getTraceId();
    if (Span.current().getSpanContext().isValid()) {
      responseContext.getHeaders().add(TRACE_ID_HEADER, traceId);
    }

    final long durationMs = duration >= 0 ? (duration / 1_000_000) : -1;
    LOG.debug("Request completed status={} durationMs={}", responseContext.getStatus(), durationMs);
  }
}
