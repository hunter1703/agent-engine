package com.agentengine.interfaces.rest.exception;

import com.agentengine.interfaces.rest.dto.ErrorResponse;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Provider
@RegisterForReflection
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionMapper.class);

  @Override
  public Response toResponse(Throwable exception) {
    String traceId = UUID.randomUUID().toString();
    LOG.error("Request failed traceId={} class={}", traceId, exception.getClass().getName(), exception);

    if (exception instanceof WebApplicationException webEx) {
      int status = webEx.getResponse().getStatus();
      String message = webEx.getMessage();
      return Response.status(status).entity(new ErrorResponse(String.valueOf(status), message, traceId)).build();
    }

    if (exception instanceof jakarta.validation.ValidationException) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse("400", "Constraint Violation: " + exception.getMessage(), traceId)).build();
    }

    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(new ErrorResponse("500", "Internal Server Error", traceId)).build();
  }
}
