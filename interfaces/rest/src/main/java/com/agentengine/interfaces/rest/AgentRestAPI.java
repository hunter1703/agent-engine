package com.agentengine.interfaces.rest;

import static com.agentengine.engine.api.AgentRequest.RequestType.STREAM_AGUI_EVENTS;
import static com.agentengine.engine.api.AgentRequest.RequestType.STREAM_RESPONSES;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static java.util.UUID.randomUUID;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.requests.ResponsesApiRequest;
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
public class AgentRestAPI {

  private static final Logger LOG = LoggerFactory.getLogger(AgentRestAPI.class);
  private final Map<RequestType, AgentRequestHandler<?>> handlers;
  private final AgentService agentService;

  @Inject
  public AgentRestAPI(final Instance<AgentRequestHandler<?>> handlers, AgentService agentService) {
    this.handlers =
        handlers != null
            ? handlers.stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        AgentRequestHandler::requestType, Function.identity()))
            : null;
    this.agentService = agentService;
  }

  @POST
  @Path("/invoke")
  @Operation(summary = "Invoke an agent")
  @APIResponse(
      responseCode = "200",
      description = "Final response from the agent",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = AgentResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid request parameters")
  @APIResponse(responseCode = "500", description = "Internal server error")
  @RunOnVirtualThread
  public AgentResponse invoke(@Valid final AgentRequest request) {
    LOG.debug(
        "Agent invocation request - agent_id={} session_id={}",
        request.getAgentId(),
        request.getSessionId());

    final RequestType requestType = RequestType.valueOf(request.getType());
    @SuppressWarnings("unchecked")
    final AgentRequestHandler<AgentResponse> handler =
        (AgentRequestHandler<AgentResponse>) handlerFor(requestType);
    return handler.handle(request);
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
  @Path("/responses")
  @Produces(SERVER_SENT_EVENTS)
  @RestStreamElementType(APPLICATION_JSON)
  @Operation(summary = "Stream agent responses")
  @APIResponse(
      responseCode = "200",
      description = "SSE event stream",
      content =
          @Content(
              mediaType = SERVER_SENT_EVENTS,
              schema = @Schema(implementation = BaseResponsesEventData.class)))
  @APIResponse(responseCode = "400", description = "Invalid request parameters")
  @APIResponse(responseCode = "500", description = "Internal server error")
  public Publisher<Map<String, Object>> responses(final ResponsesApiRequest request) {
    AgentRequest agentRequest = convertResponsesApiRequestToAgentRequest(request);

    @SuppressWarnings("unchecked")
    final AgentRequestHandler<Flowable<BaseResponsesEventData>> handler =
        (AgentRequestHandler<Flowable<BaseResponsesEventData>>) handlerFor(STREAM_RESPONSES);
    Flowable<BaseResponsesEventData> responseStream = handler.handle(agentRequest);

    return responseStream.map(JsonUtils::toMap);
  }

  @POST
  @Path("/agent")
  @Operation(summary = "Create an agent")
  @APIResponse(
      responseCode = "201",
      description = "Agent created",
      content = @Content(schema = @Schema(implementation = AgentConfig.class)))
  @APIResponse(responseCode = "409", description = "Agent already exists")
  public AgentConfig createAgent(final AgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    agentConfig.validate();
    return agentService.createAgent(agentConfig);
  }

  @PUT
  @Path("/agent/{agentId}")
  @Operation(summary = "Update an agent")
  @APIResponse(
      responseCode = "200",
      description = "Agent updated",
      content = @Content(schema = @Schema(implementation = AgentConfig.class)))
  @APIResponse(responseCode = "404", description = "Agent not found")
  public AgentConfig updateAgent(
      @PathParam("agentId") final String agentId, final AgentConfig agentConfig) {
    if (agentConfig == null || StringUtils.isBlank(agentId)) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    agentConfig.validate();
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

  private AgentRequestHandler<?> handlerFor(final RequestType requestType) {
    final AgentRequestHandler<?> handler = handlers.get(requestType);
    if (handler == null) {
      String errorMsg = "No handler registered for request type: " + requestType;
      LOG.error("Handler lookup failed - request_type={} error=\"{}\"", requestType, errorMsg);
      throw new WebApplicationException(errorMsg, 400);
    }
    return handler;
  }

  private AgentRequest convertResponsesApiRequestToAgentRequest(final ResponsesApiRequest request) {
    AgentRequest agentRequest = new AgentRequest();
    agentRequest.setAgentId(request.getModel());
    agentRequest.setSessionId(
        StringUtils.isBlank(request.getSessionId())
            ? randomUUID().toString()
            : request.getSessionId());
    agentRequest.setType(STREAM_RESPONSES.name());

    if (request.getInput() != null && !request.getInput().isEmpty()) {
      StringBuilder message = new StringBuilder();
      for (ResponsesApiRequest.InputMessage inputMsg : request.getInput()) {
        if (inputMsg.getContent() != null) {
          for (ResponsesApiRequest.ContentPart part : inputMsg.getContent()) {
            if ("text".equals(part.getType()) || "input_text".equals(part.getType())) {
              message.append(part.getText()).append("\n");
            }
          }
        }
      }
      agentRequest.setMessage(message.toString().trim());
    }

    return agentRequest;
  }
}
