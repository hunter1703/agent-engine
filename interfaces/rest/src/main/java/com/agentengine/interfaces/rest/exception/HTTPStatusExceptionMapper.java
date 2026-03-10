package com.agentengine.interfaces.rest.exception;

import com.agentengine.engine.api.exception.AgentException;
import com.agentengine.engine.api.exception.AssetNotFoundException;
import com.agentengine.engine.api.exception.ConfigurationException;
import com.agentengine.engine.api.exception.DuplicateAssetException;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class HTTPStatusExceptionMapper implements ExceptionMapper<Throwable> {

  private static final Logger LOG = LoggerFactory.getLogger(HTTPStatusExceptionMapper.class);

  @Override
  public Response toResponse(Throwable exception) {
    String traceId = UUID.randomUUID().toString();
    LOG.error(
        "Request failed traceId={} class={}", traceId, exception.getClass().getName(), exception);

    if (exception instanceof WebApplicationException webEx) {
      int status = webEx.getResponse().getStatus();
      String message = webEx.getMessage();
      return Response.status(status)
          .entity(new ErrorResponse(String.valueOf(status), message, traceId))
          .build();
    }

    if (exception instanceof AgentException agentException) {
      final int status =
          switch (agentException) {
            case ConfigurationException ignored -> 400;
            case AssetNotFoundException ignored -> 404;
            case DuplicateAssetException ignored -> 409;
            default -> 500;
          };
      return Response.status(status)
          .entity(new ErrorResponse(String.valueOf(status), agentException.getMessage(), traceId))
          .build();
    }

    if (exception instanceof ValidationException) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              new ErrorResponse("400", "Constraint Violation: " + exception.getMessage(), traceId))
          .build();
    }

    if (exception instanceof IllegalArgumentException illegalArgumentException) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse("400", illegalArgumentException.getMessage(), traceId))
          .build();
    }

    if ("com.agentengine.engine.exceptions.JsonDeserializationException"
        .equals(exception.getClass().getName())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              new ErrorResponse(
                  "400", "JSON Deserialization Error: " + exception.getMessage(), traceId))
          .build();
    }

    if (exception instanceof StatusRuntimeException grpcEx) {
      final var code = grpcEx.getStatus().getCode();
      final int status =
          switch (code) {
            case INVALID_ARGUMENT -> 400;
            case NOT_FOUND -> 404;
            case ALREADY_EXISTS -> 409;
            case UNAUTHENTICATED -> 401;
            case PERMISSION_DENIED -> 403;
            case RESOURCE_EXHAUSTED -> 429;
            case UNAVAILABLE -> 503;
            default -> 500;
          };
      final String message = status == 500 ? "Internal Server Error" : grpcEx.getStatus().getDescription();
      return Response.status(status)
          .entity(new ErrorResponse(String.valueOf(status), message, traceId))
          .build();
    }

    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(new ErrorResponse("500", "Internal Server Error", traceId))
        .build();
  }

    public record ErrorResponse(String code, String message, String traceId) {}
}
