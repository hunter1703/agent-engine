package com.agentengine.api;

import com.agentengine.api.handlers.AgentRequestHandler;
import com.agentengine.engine.client.AgentRequest;
import com.agentengine.engine.client.AgentRequest.RequestType;
import com.agentengine.engine.events.AgentEvent;
import com.agentengine.commons.utils.StringUtils;
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
  private final Map<RequestType, AgentRequestHandler> handlers;

  @Inject
  public AgentRestAPI(final Instance<AgentRequestHandler> handlers) {
    this.handlers = handlers.stream()
        .collect(Collectors.toUnmodifiableMap(AgentRequestHandler::requestType, Function.identity()));
  }

  @POST
  @Path("/invoke")
  @Operation(summary = "Invoke an agent", description = "Invoke the agent or build its prompt.")
  @APIResponse(responseCode = "200", description = "Invoke response or prompt response", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(oneOf = {
      InvokeResponse.class, PromptResponse.class})))
  public AgentResponse invoke(final AgentRequest request) {
    request.setSessionId(getOrCreateSession(request.getSessionId()));
    return handlers.get(RequestType.valueOf(request.getType())).handle(request);
  }

  @POST
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Blocking
  @Operation(summary = "Stream agent events", description = "Invoke the agent and stream events.")
  @APIResponse(responseCode = "200", description = "SSE event stream", content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
  public Multi<AgentEvent> events(final AgentRequest request) {
    request.setSessionId(getOrCreateSession(request.getSessionId()));
    return handlers.get(RequestType.STREAMING_INVOKE_AGENT).handle(request);
  }

  private static String getOrCreateSession(final String sessionId) {
    if (StringUtils.isNotBlank(sessionId)) {
      return sessionId;
    }
    return UUID.randomUUID().toString();
  }
}
