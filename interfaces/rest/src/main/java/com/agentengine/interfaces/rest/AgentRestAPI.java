package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.dto.ResumeSessionRequest;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.util.common.StringUtils;
import com.agui.core.event.BaseEvent;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.reactivestreams.Publisher;

@Path("/v1/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Agent", description = "Agent Management and Execution APIs")
@RunOnVirtualThread
public class AgentRestAPI {
  private final Map<RequestType, AgentRequestHandler<?, ?>> handlers;
  private final AgentService agentService;
  private final SessionService sessionService;

  @Inject
  public AgentRestAPI(final Instance<AgentRequestHandler<?, ?>> handlers, final AgentService agentService,
      final SessionService sessionService) {
    this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(AgentRequestHandler::requestType, Function.identity()));
    this.agentService = agentService;
    this.sessionService = sessionService;
  }

  @POST
  @Path("/events")
  @Produces(SERVER_SENT_EVENTS)
  @RestStreamElementType(APPLICATION_JSON)
  @Operation(summary = "Stream agent events")
  @APIResponse(responseCode = "200", description = "SSE event stream in AG-UI format", content = @Content(mediaType = SERVER_SENT_EVENTS, schema = @Schema(implementation = BaseEvent.class)))
  @APIResponse(responseCode = "400", description = "Invalid request parameters")
  @APIResponse(responseCode = "404", description = "Agent not found")
  @APIResponse(responseCode = "500", description = "Internal server error")
  public Publisher<BaseEvent> events(@Valid final AgentRequest request) {
    if (agentService.getAgent(request.getAgentId()).isEmpty()) {
      throw new WebApplicationException("Agent not found: " + request.getAgentId(), 404);
    }
    final AgentRequestHandler<AgentRequest, Publisher<BaseEvent>> handler = getHandler(RequestType.STREAM_AGUI_EVENTS);
    return handler.handle(request);
  }

  @POST
  @Path("/")
  @Operation(summary = "Create an agent")
  @APIResponse(responseCode = "201", description = "Agent created", content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  @APIResponse(responseCode = "409", description = "Agent already exists")
  public BaseAgentConfig createAgent(final BaseAgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    return agentService.createAgent(agentConfig);
  }

  @POST
  @Path("/upsert")
  @Operation(summary = "Upsert an agent")
  @APIResponse(responseCode = "200", description = "Agent created or updated", content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  public BaseAgentConfig upsertAgent(final BaseAgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    if (StringUtils.isBlank(agentConfig.getId())) {
      throw new WebApplicationException("Agent ID is required", 400);
    }
    return agentService.saveAgent(agentConfig);
  }

  @PUT
  @Path("/{agentId}")
  @Operation(summary = "Update an agent")
  @APIResponse(responseCode = "200", description = "Agent updated", content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  @APIResponse(responseCode = "400", description = "Path agentId must match payload id")
  @APIResponse(responseCode = "404", description = "Agent not found")
  public BaseAgentConfig updateAgent(@PathParam("agentId") final String agentId, final BaseAgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    if (StringUtils.isBlank(agentId)) {
      throw new WebApplicationException("Agent ID is required", 400);
    }
    if (StringUtils.isNotBlank(agentConfig.getId()) && !agentId.equals(agentConfig.getId())) {
      throw new WebApplicationException("Path agentId must match payload id", 400);
    }
    return agentService.updateAgent(agentId, agentConfig);
  }

  @DELETE
  @Path("/{agentId}")
  @Operation(summary = "Delete an agent")
  @APIResponse(responseCode = "204", description = "Agent deleted")
  @APIResponse(responseCode = "404", description = "Agent not found")
  public boolean deleteAgent(@PathParam("agentId") final String agentId) {
    if (StringUtils.isBlank(agentId)) {
      throw new WebApplicationException("Agent ID is required", 400);
    }
    final boolean deleted = agentService.deleteAgent(agentId);
    if (!deleted) {
      throw new WebApplicationException("Agent not found", 404);
    }
    return true;
  }

  @POST
  @Path("/session/resume/events")
  @Produces(SERVER_SENT_EVENTS)
  @RestStreamElementType(APPLICATION_JSON)
  @Operation(summary = "Resume a paused session and stream events")
  @APIResponse(responseCode = "200", description = "SSE event stream in AG-UI format", content = @Content(mediaType = SERVER_SENT_EVENTS, schema = @Schema(implementation = BaseEvent.class)))
  @APIResponse(responseCode = "400", description = "Invalid resume payload")
  @APIResponse(responseCode = "404", description = "Session not found")
  @APIResponse(responseCode = "408", description = "Pending confirmation timed out")
  public Publisher<BaseEvent> resumeEvents(@Valid @NotNull final ResumeSessionRequest resumeRequest) {
    final AgentRequestHandler<ResumeSessionRequest, Publisher<BaseEvent>> handler = getHandler(RequestType.RESUME_SESSION);
    return handler.handle(resumeRequest);
  }

  @DELETE
  @Path("/session/{sessionId}")
  @Operation(summary = "Delete a session")
  @APIResponse(responseCode = "204", description = "Session deleted")
  @APIResponse(responseCode = "404", description = "Session not found")
  public void deleteSession(@PathParam("sessionId") final String sessionId) {
    if (StringUtils.isBlank(sessionId)) {
      throw new WebApplicationException("Session ID is required", 400);
    }
    sessionService.deleteSession(sessionId);
  }

  @SuppressWarnings("unchecked")
  private <Request extends AgentRequest, Response> AgentRequestHandler<Request, Response> getHandler(final RequestType type) {
    final AgentRequestHandler<?, ?> handler = handlers.get(type);
    if (handler == null) {
      throw new WebApplicationException("No handler registered for request type: " + type, 400);
    }
    return (AgentRequestHandler<Request, Response>) handler;
  }
}
