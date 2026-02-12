package com.agentengine.engine.exceptions;

import jakarta.ws.rs.core.Response;

public class JsonSerializationException extends RuntimeException {
    public JsonSerializationException(String message) {
        super(message);
    }

    public JsonSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public Response toResponse() {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\":\"" + getMessage() + "\"}")
                .build();
    }
}