package com.agentengine.interfaces.rest;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.utils.LoggingUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.models.AgentInfo;
import com.agentengine.interfaces.rest.models.ListAgentsResponse;
import com.agentengine.interfaces.rest.requests.ResponsesApiRequest;
import com.agentengine.interfaces.rest.responses.dtos.BaseResponsesEventData;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Agent", description = "Agent Management and Execution APIs")
@RunOnVirtualThread
public class AgentRestAPI {

  private static final Logger LOG = LoggerFactory.getLogger(AgentRestAPI.class);
  private final Map<RequestType, AgentRequestHandler<?>> handlers;
  private final ConfigRepository configRepository;

  @Inject
  public AgentRestAPI(final Instance<AgentRequestHandler<?>> handlers, ConfigRepository configRepository) {
    this.handlers = handlers.stream()
        .collect(Collectors.toUnmodifiableMap(AgentRequestHandler::requestType, Function.identity()));
    this.configRepository = configRepository;
  }

  @POST
  @Path("/invoke")
  @Operation(summary = "Invoke an agent", description = "Synchronously invoke the agent and get the final response.")
  @APIResponse(responseCode = "200", description = "Final response from the agent")
  public AgentResponse invoke(final AgentRequest request) {
    LOG.debug("Agent invocation request - agent_id={} session_id={}", request.getAgentId(), request.getSessionId());

    final RequestType requestType = RequestType.valueOf(request.getType());
    @SuppressWarnings("unchecked")
    final AgentRequestHandler<AgentResponse> handler = (AgentRequestHandler<AgentResponse>) handlerFor(requestType);
    return handler.handle(request);
  }

  @POST
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Blocking
  @Operation(summary = "Stream agent events", description = "Invoke the agent and stream events in AG-UI format.")
  @APIResponse(responseCode = "200", description = "SSE event stream in AG-UI format")
  public Publisher<BaseEvent> events(final AgentRequest request) {
    LOG.debug("Agent events streaming request - agent_id={} session_id={}", request.getAgentId(),
        request.getSessionId());

    @SuppressWarnings("unchecked")
    final AgentRequestHandler<Flowable<BaseEvent>> handler = (AgentRequestHandler<Flowable<BaseEvent>>) handlerFor(
        RequestType.STREAM_AGUI_EVENTS);
    return handler.handle(request);
  }

  @POST
  @Path("/responses")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Blocking
  @Operation(summary = "Stream agent responses", description = "Invoke the agent and stream events in Responses API format.")
  @APIResponse(responseCode = "200", description = "SSE event stream")
  public Publisher<Map<String, Object>> responses(final Map<String, Object> requestMap) {
    LOG.debug("Agent responses streaming request - requestMap={}", JsonUtils.toJson(requestMap));

    final ResponsesApiRequest request = JsonUtils.fromMap(requestMap, ResponsesApiRequest.class);
    AgentRequest agentRequest = convertResponsesApiRequestToAgentRequest(request);

    @SuppressWarnings("unchecked")
    final AgentRequestHandler<Flowable<BaseResponsesEventData>> handler = (AgentRequestHandler<Flowable<BaseResponsesEventData>>) handlerFor(
        RequestType.STREAM_RESPONSES);
    Flowable<BaseResponsesEventData> responseStream = handler.handle(agentRequest);

    return responseStream.map(JsonUtils::toMap);
  }

  @GET
  @Path("/agents")
  @Operation(summary = "List available agents", description = "Returns a list of available agents")
  @APIResponse(responseCode = "200", description = "List of available models", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ListAgentsResponse.class)))
  public ListAgentsResponse listModels() {
    List<AgentInfo> models = new ArrayList<>();

    // Add dynamic agents from the config repository
    List<String> agentIds = configRepository.listAgentConfigs();
    for (String agentId : agentIds) {
      models.add(new AgentInfo(agentId, "agent-engine"));
    }

    return new ListAgentsResponse(models);
  }

  @GET
  @Path("/models/{agent}")
  @Operation(summary = "Retrieve a specific agent", description = "Retrieves information about a specific agent.")
  @APIResponse(responseCode = "200", description = "Information about the requested model", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AgentInfo.class)))
  @APIResponse(responseCode = "404", description = "Model not found")
  public AgentInfo retrieveModel(@PathParam("agent") String agent) {
    try {
      if (configRepository.loadAgentConfig(agent) != null) {
        return new AgentInfo(agent, "agent-engine");
      }
    } catch (Exception e) {
    }

    throw new WebApplicationException("Agent not found: " + agent, 404);
  }

  @GET
  @Path("/agents")
  @Operation(summary = "List available agents", description = "Returns a list of available agents for the Agent Console.")
  @APIResponse(responseCode = "200", description = "List of available agents", content = @org.eclipse.microprofile.openapi.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ListAgentsResponse.class)))
  public ListAgentsResponse listAgents() {
    List<AgentInfo> agents = new ArrayList<>();

    List<String> agentIds = configRepository.listAgentConfigs();
    for (String agentId : agentIds) {
      AgentInfo info = new AgentInfo();
      info.setId(agentId);
      info.setOwnedBy("agent-engine");
      agents.add(info);
    }

    return new ListAgentsResponse(agents);
  }

  @GET
  @Path("/agents/{agentId}")
  @Operation(summary = "Retrieve a specific agent", description = "Retrieves information about a specific agent for the Agent Console.")
  @APIResponse(responseCode = "200", description = "Information about the requested agent", content = @org.eclipse.microprofile.openapi.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AgentInfo.class)))
  @APIResponse(responseCode = "404", description = "Agent not found")
  public AgentInfo retrieveAgent(@PathParam("agentId") String agentId) {
    try {
      if (configRepository.loadAgentConfig(agentId) != null) {
        AgentInfo info = new AgentInfo();
        info.setId(agentId);
        info.setOwnedBy("agent-engine");
        return info;
      }
    } catch (Exception e) {
    }

    throw new WebApplicationException("Agent not found: " + agentId, 404);
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
        StringUtils.isBlank(request.getSessionId()) ? UUID.randomUUID().toString() : request.getSessionId());
    agentRequest.setType(RequestType.STREAM_RESPONSES.name());

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
