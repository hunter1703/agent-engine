package com.agentengine.engine.exceptions;

import jakarta.ws.rs.core.Response;

public class JsonDeserializationException extends RuntimeException {
    public JsonDeserializationException(String message) {
        super(message);
    }

    public JsonDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public Response toResponse() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(STR."{\"error\":\"\{getMessage()}\"}")
                .build();
    }
}