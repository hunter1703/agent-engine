package com.agentengine.interfaces.rest;

import static com.agentengine.engine.api.AgentRequest.RequestType.STREAM_AGUI_EVENTS;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static java.util.UUID.randomUUID;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.requests.ResponsesApiRequest;
import com.agentengine.interfaces.rest.requests.ResumeSessionRequest;
import com.agentengine.interfaces.rest.responses.dtos.BaseResponsesEventData;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/v1/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Agent", description = "Agent Management and Execution APIs")
@RunOnVirtualThread
public class AgentRestAPI {

  private static final Logger LOG = LoggerFactory.getLogger(AgentRestAPI.class);
  private final Map<RequestType, AgentRequestHandler<?>> handlers;
  private final AgentService agentService;
  private final SessionService sessionService;

  @Inject
  public AgentRestAPI(final Instance<AgentRequestHandler<?>> handlers, AgentService agentService, SessionService sessionService) {
    this.handlers =
        handlers != null
            ? handlers.stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        AgentRequestHandler::requestType, Function.identity()))
            : null;
    this.agentService = agentService;
    this.sessionService = sessionService;
  }

  @POST
  @Path("/events")
  @Produces(SERVER_SENT_EVENTS)
  @RestStreamElementType(APPLICATION_JSON)
  @Operation(summary = "Stream agent events")
  @APIResponse(
      responseCode = "200",
      description = "SSE event stream in AG-UI format",
      content =
          @Content(
              mediaType = SERVER_SENT_EVENTS,
              schema = @Schema(implementation = BaseEvent.class)))
  @APIResponse(responseCode = "400", description = "Invalid request parameters")
  @APIResponse(responseCode = "500", description = "Internal server error")
  public Publisher<BaseEvent> events(@Valid final AgentRequest request) {
    LOG.debug(
        "Agent events streaming request - agent_id={} session_id={}",
        request.getAgentId(),
        request.getSessionId());

    @SuppressWarnings("unchecked")
    final AgentRequestHandler<Flowable<BaseEvent>> handler =
        (AgentRequestHandler<Flowable<BaseEvent>>) handlerFor(STREAM_AGUI_EVENTS);
    return handler.handle(request);
  }

  @POST
  @Path("/agent")
  @Operation(summary = "Create an agent")
  @APIResponse(
      responseCode = "201",
      description = "Agent created",
      content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  @APIResponse(responseCode = "409", description = "Agent already exists")
  public BaseAgentConfig createAgent(final BaseAgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    return agentService.createAgent(agentConfig);
  }

  @POST
  @Path("/agent/upsert")
  @Operation(summary = "Upsert an agent")
  @APIResponse(
      responseCode = "200",
      description = "Agent created or updated",
      content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  public BaseAgentConfig upsertAgent(final BaseAgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    return agentService.saveAgent(agentConfig);
  }

  @PUT
  @Path("/agent/{agentId}")
  @Operation(summary = "Update an agent")
  @APIResponse(
      responseCode = "200",
      description = "Agent updated",
      content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  @APIResponse(responseCode = "404", description = "Agent not found")
  public BaseAgentConfig updateAgent(
      @PathParam("agentId") final String agentId, final BaseAgentConfig agentConfig) {
    if (agentConfig == null || StringUtils.isBlank(agentId)) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    return agentService.updateAgent(agentConfig);
  }

  @DELETE
  @Path("/agent/{agentId}")
  @Operation(summary = "Delete an agent")
  @APIResponse(responseCode = "204", description = "Agent deleted")
  @APIResponse(responseCode = "404", description = "Agent not found")
  public boolean deleteAgent(@PathParam("agentId") final String agentId) {
    return agentService.deleteAgent(agentId);
  }

  @POST
  @Path("/session/{sessionId}/resume/events")
  @Produces(SERVER_SENT_EVENTS)
  @RestStreamElementType(APPLICATION_JSON)
  @Operation(summary = "Resume a paused session and stream events")
  @APIResponse(
      responseCode = "200",
      description = "SSE event stream in AG-UI format",
      content =
          @Content(
              mediaType = SERVER_SENT_EVENTS,
              schema = @Schema(implementation = BaseEvent.class)))
  @APIResponse(responseCode = "404", description = "Session not found")
  public Publisher<BaseEvent> resumeEvents(
      @PathParam("sessionId") final String sessionId,
      @Valid final ResumeSessionRequest resumeRequest) {
    final var session =
        sessionService
            .getSession(sessionId)
            .orElseThrow(() -> new WebApplicationException("Session not found", 404));
    final AgentRequest request = new AgentRequest();
    request.setType(STREAM_AGUI_EVENTS.name());
    request.setAgentId(session.getAgentId());
    request.setSessionId(sessionId);
    request.setMessage(resumeRequest.getMessage());
    return events(request);
  }

  @DELETE
  @Path("/session/{sessionId}")
  @Operation(summary = "Delete a session")
  @APIResponse(responseCode = "204", description = "Session deleted")
  @APIResponse(responseCode = "404", description = "Session not found")
  public void deleteSession(@PathParam("sessionId") final String sessionId) {
    sessionService.deleteSession(sessionId);
  }

  private AgentRequestHandler<?> handlerFor(final RequestType requestType) {
    final AgentRequestHandler<?> handler = handlers.get(requestType);
    if (handler == null) {
      String errorMsg = "No handler registered for request type: " + requestType;
      LOG.error("Handler lookup failed - request_type={} error=\"{}\"", requestType, errorMsg);
      throw new WebApplicationException(errorMsg, 400);
    }
    return handler;
  }
}
