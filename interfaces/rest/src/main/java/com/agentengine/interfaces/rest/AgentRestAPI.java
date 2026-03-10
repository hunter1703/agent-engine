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
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.interfaces.rest.contracts.BuilderDefinitionService;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.requests.ResponsesApiRequest;
import com.agentengine.interfaces.rest.requests.ResumeSessionRequest;
import com.agentengine.interfaces.rest.responses.dtos.BaseResponsesEventData;
import com.agui.core.event.BaseEvent;
import com.agentengine.util.builder.BuilderMode;
import com.agentengine.util.builder.BuilderDefinitionUtils;
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
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
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
  private static final long CONFIRMATION_TIMEOUT_MILLIS = Duration.ofMinutes(15).toMillis();
  private final Map<RequestType, AgentRequestHandler<?>> handlers;
  private final AgentService agentService;
  private final SessionService sessionService;
  private final BuilderDefinitionService builderDefinitionService;

  @Inject
  public AgentRestAPI(
      final Instance<AgentRequestHandler<?>> handlers,
      final AgentService agentService,
      final SessionService sessionService,
      final BuilderDefinitionService builderDefinitionService) {
    this.handlers =
        handlers != null
            ? handlers.stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        AgentRequestHandler::requestType, Function.identity()))
            : null;
    this.agentService = agentService;
    this.sessionService = sessionService;
    this.builderDefinitionService = builderDefinitionService;
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
  @APIResponse(responseCode = "404", description = "Agent not found")
  @APIResponse(responseCode = "500", description = "Internal server error")
  public Publisher<BaseEvent> events(@Valid final AgentRequest request) {
    if (agentService.getAgent(request.getAgentId()).isEmpty()) {
      throw new WebApplicationException("Agent not found: " + request.getAgentId(), 404);
    }
    return doEvents(request);
  }

  // used by resumeEvents to avoid duplicate getAgent() call
  private Publisher<BaseEvent> doEvents(final AgentRequest request) {
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
    final BaseAgentConfig sanitizedConfig =
        BuilderDefinitionUtils.sanitize(
            builderDefinitionService.getDefinition("agent"),
            BuilderMode.CREATE,
            agentConfig,
            BaseAgentConfig.class);
    if (StringUtils.isBlank(sanitizedConfig.getType())) {
      throw new WebApplicationException("Agent type is required", 400);
    }
    if (requiresModelId(sanitizedConfig) && StringUtils.isBlank(sanitizedConfig.getModelId())) {
      throw new WebApplicationException("Agent type and modelId are required", 400);
    }
    validateOrchestratorSubAgentsExist(sanitizedConfig);
    return agentService.createAgent(sanitizedConfig);
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
    if (StringUtils.isBlank(agentConfig.getId())) {
      throw new WebApplicationException("Agent ID is required", 400);
    }
    if (StringUtils.isBlank(agentConfig.getType())) {
      throw new WebApplicationException("Agent type is required", 400);
    }
    if (requiresModelId(agentConfig) && StringUtils.isBlank(agentConfig.getModelId())) {
      throw new WebApplicationException("Agent type and modelId are required", 400);
    }
    validateOrchestratorSubAgentsExist(agentConfig);
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
    if (agentConfig == null) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    if (StringUtils.isBlank(agentId)) {
      throw new WebApplicationException("Agent ID is required", 400);
    }
    if (StringUtils.isNotBlank(agentConfig.getId()) && !agentId.equals(agentConfig.getId())) {
      throw new WebApplicationException("Path agentId must match payload id", 400);
    }
    final BaseAgentConfig sanitizedConfig =
        BuilderDefinitionUtils.sanitize(
            builderDefinitionService.getDefinition("agent"),
            BuilderMode.EDIT,
            agentConfig,
            BaseAgentConfig.class);
    if (StringUtils.isBlank(sanitizedConfig.getType())) {
      throw new WebApplicationException("Agent type is required", 400);
    }
    if (requiresModelId(sanitizedConfig) && StringUtils.isBlank(sanitizedConfig.getModelId())) {
      throw new WebApplicationException("Agent type and modelId are required", 400);
    }
    validateOrchestratorSubAgentsExist(sanitizedConfig);
    return agentService.updateAgent(agentId, sanitizedConfig);
  }

  @DELETE
  @Path("/agent/{agentId}")
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
  @APIResponse(responseCode = "410", description = "Session expired")
  public Publisher<BaseEvent> resumeEvents(
      @PathParam("sessionId") final String sessionId,
      @Valid final ResumeSessionRequest resumeRequest) {
    final var session =
        sessionService
            .getSession(sessionId)
            .orElseThrow(() -> new WebApplicationException("Session not found", 404));
    if (isExpired(session.getSessionInfo() == null ? null : session.getSessionInfo().getState())) {
      throw new WebApplicationException("Session expired", 410);
    }
    if (isConfirmationTimedOut(session.getSessionInfo() == null ? null : session.getSessionInfo().getState())) {
      throw new WebApplicationException("Confirmation timed out", 408);
    }
    final AgentRequest request = new AgentRequest();
    request.setType(STREAM_AGUI_EVENTS.name());
    request.setAgentId(session.getAgentId());
    request.setSessionId(sessionId);
    request.setMessage(resumeRequest.getMessage());
    return doEvents(request);
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

  private static boolean requiresModelId(final BaseAgentConfig agentConfig) {
    return agentConfig == null || !"orchestrator".equalsIgnoreCase(agentConfig.getType());
  }

  private static boolean isExpired(final Map<String, Object> state) {
    if (state == null || state.isEmpty()) {
      return false;
    }
    final Object expiresAt = state.get("expiresAt");
    if (expiresAt == null) {
      return false;
    }
    final Instant expiry = parseExpiry(expiresAt);
    return expiry != null && expiry.isBefore(Instant.now());
  }

  private static Instant parseExpiry(final Object expiresAt) {
    if (expiresAt instanceof Number value) {
      return Instant.ofEpochMilli(value.longValue());
    }
    if (expiresAt instanceof String text && StringUtils.isNotBlank(text)) {
      try {
        return Instant.parse(text);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static boolean isConfirmationTimedOut(final Map<String, Object> state) {
    if (state == null || state.isEmpty()) {
      return false;
    }
    final Map<String, Object> sessionState = extractSessionState(state);
    if (sessionState == null) {
      return false;
    }
    final Object paused = sessionState.get("paused");
    final Object pauseReason = sessionState.get("pauseReason");
    if (!(paused instanceof Boolean isPaused)
        || !isPaused
        || !(pauseReason instanceof String reason)
        || !"tool_confirmation".equalsIgnoreCase(reason)) {
      return false;
    }
    final Object requestedAt = sessionState.get("pauseRequestedAt");
    if (!(requestedAt instanceof Number requestedAtValue)) {
      return false;
    }
    final long ageMillis = System.currentTimeMillis() - requestedAtValue.longValue();
    return ageMillis > CONFIRMATION_TIMEOUT_MILLIS;
  }

  private static Map<String, Object> extractSessionState(final Map<String, Object> state) {
    return CollectionUtils.getMapFromMap(state, "sessionState");
  }

  private void validateOrchestratorSubAgentsExist(final BaseAgentConfig agentConfig) {
    if (agentConfig == null
        || !"orchestrator".equalsIgnoreCase(agentConfig.getType())
        || agentConfig.getSubAgentIds() == null) {
      return;
    }
    final List<String> missingSubAgents =
        agentConfig.getSubAgentIds().stream()
            .filter(StringUtils::isNotBlank)
            .filter(subAgentId -> agentService.getAgent(subAgentId).isEmpty())
            .toList();
    if (!missingSubAgents.isEmpty()) {
      throw new WebApplicationException(
          "Sub-agent(s) not found: " + String.join(", ", missingSubAgents), 400);
    }
  }

}
