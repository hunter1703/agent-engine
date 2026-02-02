package com.agentengine.interfaces.rest;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.utils.LoggingUtils;
import com.agentengine.interfaces.rest.config.ResponsesApiConfig;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import com.agentengine.interfaces.rest.dto.PromptResponse;
import com.agentengine.interfaces.rest.requests.ResponsesApiRequest;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.responses.ResponsesApiEvent;
import com.agentengine.interfaces.rest.responses.ResponsesApiMapper;
import com.agui.core.event.BaseEvent;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.WebApplicationException;

import java.util.Arrays;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Path("/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@Tag(name = "Agent")
public class AgentRestAPI {
  private static final Logger LOG = LoggerFactory.getLogger(AgentRestAPI.class);
  private final Map<RequestType, AgentRequestHandler<?>> handlers;
  private final ResponsesApiMapper responsesApiMapper;
  private final ResponsesApiConfig responsesApiConfig;

  @Inject
  public AgentRestAPI(final Instance<AgentRequestHandler<?>> handlers, ResponsesApiMapper responsesApiMapper,
      ResponsesApiConfig responsesApiConfig) {
    this.handlers = handlers.stream()
        .collect(Collectors.toUnmodifiableMap(AgentRequestHandler::requestType, Function.identity()));
    this.responsesApiMapper = responsesApiMapper;
    this.responsesApiConfig = responsesApiConfig;
  }

  @POST
  @Path("/invoke")
  @Operation(summary = "Invoke an agent", description = "Invoke the agent or build its prompt.")
  @APIResponse(responseCode = "200", description = "Invoke response or prompt response", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(oneOf = {
      InvokeResponse.class, PromptResponse.class})))
  public AgentResponse invoke(final AgentRequest request) {
    // Generate or retrieve trace ID for this request
    String traceId = LoggingUtils.getOrCreateTraceId();

    LOG.info("Agent invocation started - trace_id={} agent_id={} session_id={}", traceId, request.getAgentId(),
        request.getSessionId());
    LOG.debug("Agent invocation request details - trace_id={} agent_config_path=\"{}\" message_length={}", traceId,
        request.getAgentConfigPath(), request.getMessage() != null ? request.getMessage().length() : 0);

    try {
      final AgentRequest effectiveRequest = request.withSessionId(getOrCreateSession(request.getSessionId()));
      MDC.put("session_id", effectiveRequest.getSessionId());
      AgentResponse response = (AgentResponse) handlerFor(RequestType.valueOf(effectiveRequest.getType()))
          .handle(effectiveRequest);

      LOG.info("Agent invocation completed - trace_id={} agent_id={} session_id={} outcome=success", traceId,
          request.getAgentId(), request.getSessionId());
      LOG.debug("Agent invocation response details - trace_id={} response_type=\"{}\"", traceId,
          response != null ? response.getClass().getSimpleName() : "null");

      return response;
    } catch (Exception e) {
      LOG.error("Agent invocation failed - trace_id={} agent_id={} session_id={} outcome=failure error=\"{}\"", traceId,
          request.getAgentId(), request.getSessionId(), e.getMessage(), e);
      throw e;
    } finally {
      MDC.clear();
    }
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
    // Generate or retrieve trace ID for this request
    String traceId = LoggingUtils.getOrCreateTraceId();

    LOG.info("Agent events streaming started - trace_id={} agent_id={} session_id={}", traceId, request.getAgentId(),
        request.getSessionId());
    LOG.debug("Agent events streaming request details - trace_id={} agent_config_path=\"{}\" message_length={}",
        traceId, request.getAgentConfigPath(), request.getMessage() != null ? request.getMessage().length() : 0);

    try {
      final AgentRequest effectiveRequest = request.withSessionId(getOrCreateSession(request.getSessionId()));
      MDC.put("session_id", effectiveRequest.getSessionId());
      Multi<BaseEvent> response = (Multi<BaseEvent>) handlerFor(RequestType.STREAMING_INVOKE_AGENT)
          .handle(effectiveRequest);

      LOG.info("Agent events streaming initiated - trace_id={} agent_id={} session_id={}", traceId,
          request.getAgentId(), request.getSessionId());
      LOG.debug("Agent events streaming response details - trace_id={} response_type=\"{}\"", traceId,
          response != null ? response.getClass().getSimpleName() : "null");

      return response;
    } catch (Exception e) {
      LOG.error("Agent events streaming failed - trace_id={} agent_id={} session_id={} outcome=failure error=\"{}\"",
          traceId, request.getAgentId(), request.getSessionId(), e.getMessage(), e);
      throw e;
    } finally {
      MDC.clear();
    }
  }

  @POST
  @Path("v1/responses")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Blocking
  @Operation(summary = "Stream agent responses in Responses API format", description = "Invoke the agent and stream events in Responses API format compatible with Codex CLI.")
  @APIResponse(responseCode = "200", description = "SSE event stream in Responses API format", content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
  public Multi<ResponsesApiEvent> responses(final ResponsesApiRequest request) {
    String traceId = LoggingUtils.getOrCreateTraceId();

    // Convert the ResponsesApiRequest to an AgentRequest for internal processing
    AgentRequest agentRequest = convertResponsesApiRequestToAgentRequest(request);

    LOG.info("Agent responses streaming started - trace_id={} agent_id={} session_id={}", traceId,
        agentRequest.getAgentId(), agentRequest.getSessionId());
    LOG.debug("Agent responses streaming request details - trace_id={} agent_config_path=\"{}\" message_length={}",
        traceId, agentRequest.getAgentConfigPath(),
        agentRequest.getMessage() != null ? agentRequest.getMessage().length() : 0);

    if (!responsesApiConfig.enabled()) {
      LOG.warn("Responses API is disabled via configuration");
      throw new WebApplicationException("Responses API is disabled", 503);
    }

    try {
      final AgentRequest effectiveRequest = agentRequest.withSessionId(getOrCreateSession(agentRequest.getSessionId()));
      MDC.put("session_id", effectiveRequest.getSessionId());

      // noinspection unchecked
      Multi<BaseEvent> baseEventStream = (Multi<BaseEvent>) handlerFor(RequestType.STREAMING_INVOKE_AGENT)
          .handle(effectiveRequest);

      LOG.info("Agent responses streaming initiated - trace_id={} agent_id={} session_id={}", traceId,
          agentRequest.getAgentId(), agentRequest.getSessionId());

      // Configure the mapper with the API configuration
      responsesApiMapper.setConfig(responsesApiConfig);

      return baseEventStream.onItem().transform(responsesApiMapper::mapEvent);

    } catch (Exception e) {
      LOG.error("Agent responses streaming failed - trace_id={} agent_id={} session_id={} outcome=failure error=\"{}\"",
          traceId, agentRequest.getAgentId(), agentRequest.getSessionId(), e.getMessage(), e);
      throw e;
    } finally {
      MDC.clear();
    }
  }

  private AgentRequestHandler<?> handlerFor(final RequestType requestType) {
    final AgentRequestHandler<?> handler = handlers.get(requestType);
    if (handler == null) {
      String errorMsg = STR."No handler registered for request type: \{requestType}";
      LOG.error("Handler lookup failed - request_type={} error=\"{}\"", requestType, errorMsg);
      throw new IllegalArgumentException(errorMsg);
    }
    return handler;
  }

  private static String getOrCreateSession(final String sessionId) {
    if (StringUtils.isNotBlank(sessionId)) {
      return sessionId;
    }
    String newSessionId = UUID.randomUUID().toString();
    LOG.debug("New session created - session_id={}", newSessionId);
    return newSessionId;
  }

  private AgentRequest convertResponsesApiRequestToAgentRequest(ResponsesApiRequest responsesRequest) {
    final StringBuilder messageBuilder = new StringBuilder();
    for (final ResponsesApiRequest.Message message : responsesRequest.getMessages()) {
      if (message != null && message.getContent() != null) {
        messageBuilder.append(message.getContent()).append("\n");
      }
    }
    String message = messageBuilder.toString();

    AgentRequest agentRequest = new AgentRequest();
    agentRequest.setType("STREAMING_INVOKE_AGENT");
    agentRequest.setAgentId(responsesRequest.getModel());
    agentRequest.setSessionId(responsesRequest.getThreadId());
    agentRequest.setMessage(message);

    return agentRequest;
  }
}
