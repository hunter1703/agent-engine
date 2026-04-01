package com.agentengine.interfaces.rest.exception;

import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class HTTPStatusExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(HTTPStatusExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webEx) {
            int status = webEx.getResponse().getStatus();
            String message = webEx.getMessage();
            return Response.status(status)
                    .entity(new ErrorResponse(String.valueOf(status), message))
                    .build();
        }

        if (exception instanceof AssetNotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("404", exception.getMessage()))
                    .build();
        }

        if (exception instanceof DuplicateAssetException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("409", exception.getMessage()))
                    .build();
        }

        if (exception instanceof ConfigurationException configurationException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("400", configurationException.getMessage()))
                    .build();
        }

        if (exception instanceof ValidationException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("400", "Constraint Violation: " + exception.getMessage()))
                    .build();
        }

        if (exception instanceof IllegalArgumentException illegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("400", illegalArgumentException.getMessage()))
                    .build();
        }

        if (exception instanceof StatusRuntimeException grpcEx) {
            final Status.Code code = grpcEx.getStatus().getCode();
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
            final String message =
                    status == 500 ? "Internal Server Error" : grpcEx.getStatus().getDescription();
            return Response.status(status)
                    .entity(new ErrorResponse(String.valueOf(status), message))
                    .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("500", "Internal Server Error"))
                .build();
    }

    public record ErrorResponse(String code, String message) {}
}
