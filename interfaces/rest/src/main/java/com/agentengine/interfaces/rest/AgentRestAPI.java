package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import com.agentengine.interfaces.rest.dto.PromptResponse;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agui.core.event.BaseEvent;
import com.agentengine.engine.api.utils.StringUtils;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.resteasy.reactive.RestStreamElementType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@Tag(name = "Agent")
public class AgentRestAPI {
  private final Map<RequestType, AgentRequestHandler<?>> handlers;

  @Inject
  public AgentRestAPI(final Instance<AgentRequestHandler<?>> handlers) {
    this.handlers = handlers.stream()
        .collect(Collectors.toUnmodifiableMap(AgentRequestHandler::requestType, Function.identity()));
  }

  @POST
  @Path("/invoke")
  @Operation(summary = "Invoke an agent", description = "Invoke the agent or build its prompt.")
  @APIResponse(responseCode = "200", description = "Invoke response or prompt response", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(oneOf = {
      InvokeResponse.class, PromptResponse.class})))
  public AgentResponse invoke(final AgentRequest request) {
    final AgentRequest effectiveRequest = request.withSessionId(getOrCreateSession(request.getSessionId()));
    return (AgentResponse) handlerFor(RequestType.valueOf(effectiveRequest.getType())).handle(effectiveRequest);
  }

  @POST
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Blocking
  @Operation(summary = "Stream agent events", description = "Invoke the agent and stream events.")
  @APIResponse(responseCode = "200", description = "SSE event stream", content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
  @SuppressWarnings("unchecked")
  public Multi<BaseEvent> events(final AgentRequest request) {
    final AgentRequest effectiveRequest = request.withSessionId(getOrCreateSession(request.getSessionId()));
    return (Multi<BaseEvent>) handlerFor(RequestType.STREAMING_INVOKE_AGENT).handle(effectiveRequest);
  }

  private AgentRequestHandler<?> handlerFor(final RequestType requestType) {
    final AgentRequestHandler<?> handler = handlers.get(requestType);
    if (handler == null) {
      throw new IllegalArgumentException(STR."No handler registered for request type: \{requestType}");
    }
    return handler;
  }

  private static String getOrCreateSession(final String sessionId) {
    if (StringUtils.isNotBlank(sessionId)) {
      return sessionId;
    }
    return UUID.randomUUID().toString();
  }
}
