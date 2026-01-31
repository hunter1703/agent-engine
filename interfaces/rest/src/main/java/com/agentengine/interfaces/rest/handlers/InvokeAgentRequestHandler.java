package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.google.adk.agents.RunConfig;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Singleton;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InvokeAgentRequestHandler extends AbstractAgentRequestHandler<AgentResponse> {
  private static final Logger LOG = LoggerFactory.getLogger(InvokeAgentRequestHandler.class);

  public InvokeAgentRequestHandler(final AgentRuntimeManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.INVOKE_AGENT;
  }

  @Override
  public AgentResponse handle(final AgentRequest request) {
    LOG.info("Agent invocation handler started - agent_id={} session_id={} operation=agent.invoke.start",
        request.getAgentId(), request.getSessionId());
    LOG.debug("Agent invocation handler details - agent_id={} session_id={} message_length={}", request.getAgentId(),
        request.getSessionId(), request.getMessage() != null ? request.getMessage().length() : 0);

    try {
      final AgentRuntime runtime = getOrCreateRuntime(request);
      final String sessionId = ensureSession(runtime, request.getSessionId());
      final Content message = Content.fromParts(Part.builder().text(request.getMessage()).build());
      final RunConfig runConfig = RunConfig.builder().build();
      final Flowable<Event> events = runtime.runner().runAsync(AgentRuntimeManager.DEFAULT_USER_ID, sessionId, message,
          runConfig);
      final List<Event> eventList = events.toList().blockingGet();
      final AgentRunResult result = AgentRunResult.fromEvents(eventList);

      LOG.info(
          "Agent invocation handler completed - agent_id={} session_id={} operation=agent.invoke.complete outcome=success",
          request.getAgentId(), request.getSessionId());
      LOG.debug("Agent invocation handler response - agent_id={} session_id={} response_length={} thoughts_length={}",
          request.getAgentId(), request.getSessionId(),
          result.finalAnswer() != null ? result.finalAnswer().length() : 0,
          result.thoughts() != null ? result.thoughts().length() : 0);

      return new InvokeResponse(sessionId, result.finalAnswer(), result.thoughts());
    } catch (Exception e) {
      LOG.error(
          "Agent invocation handler failed - agent_id={} session_id={} operation=agent.invoke.error outcome=failure error=\"{}\"",
          request.getAgentId(), request.getSessionId(), e.getMessage(), e);
      throw e;
    }
  }
}
