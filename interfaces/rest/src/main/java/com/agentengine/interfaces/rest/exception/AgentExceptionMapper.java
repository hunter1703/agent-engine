package com.agentengine.interfaces.rest.exception;

import com.agentengine.engine.api.exception.AgentException;
import com.agentengine.engine.api.exception.ConfigurationException;
import com.agentengine.interfaces.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AgentExceptionMapper implements ExceptionMapper<AgentException> {

  @Override
  public Response toResponse(AgentException exception) {
    int status = 500;
    if (exception instanceof ConfigurationException) {
      status = 400;
    }
    return Response.status(status)
        .entity(new ErrorResponse(exception.getMessage(), exception.getClass().getSimpleName())).build();
  }
}
