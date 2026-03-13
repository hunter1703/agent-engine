package com.agentengine.interfaces.rest.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Provider
public class RequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
  private static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final String MDC_KEY = "requestId";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String requestId = requestContext.getHeaderString(REQUEST_ID_HEADER);
    if (requestId == null || requestId.isEmpty()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(MDC_KEY, requestId);
    LOG.debug("Request received method={} path={} requestId={}", requestContext.getMethod(), requestContext.getUriInfo().getPath(),
        requestId);
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    String requestId = MDC.get(MDC_KEY);
    if (requestId != null) {
      responseContext.getHeaders().add(REQUEST_ID_HEADER, requestId);
    }
    LOG.debug("Request completed status={} requestId={}", responseContext.getStatus(), requestId);
    MDC.remove(MDC_KEY);
  }
}
