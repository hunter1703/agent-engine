package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InvokeAgentRequestHandler extends AbstractAgentRequestHandler<AgentResponse> {
  private static final Logger LOG = LoggerFactory.getLogger(InvokeAgentRequestHandler.class);

  @Inject
  public InvokeAgentRequestHandler(AgentExecutionService agentExecutionService) {
    super(agentExecutionService);
  }

  @Override
  public RequestType requestType() {
    return RequestType.INVOKE_AGENT;
  }

  @Override
  public AgentResponse handle(final AgentRequest request) {
    if (request.getSessionId() == null) {
      request.setSessionId(java.util.UUID.randomUUID().toString());
    }
    LOG.info("Agent invocation handler started - agent_id={} session_id={} operation=agent.invoke.start",
        request.getAgentId(), request.getSessionId());
    LOG.debug("Agent invocation handler details - agent_id={} session_id={} message_length={}", request.getAgentId(),
        request.getSessionId(), request.getMessage() != null ? request.getMessage().length() : 0);

    try {
      final AgentRunResult result = AgentRunResult
          .fromEvents(agentExecutionService.run(request).toList().blockingGet());
      LOG.info(
          "Agent invocation handler completed - agent_id={} session_id={} operation=agent.invoke.complete outcome=success",
          request.getAgentId(), request.getSessionId());
      LOG.debug("Agent invocation handler response - agent_id={} session_id={} response_length={} thoughts_length={}",
          request.getAgentId(), request.getSessionId(),
          result.finalAnswer() != null ? result.finalAnswer().length() : 0,
          result.thoughts() != null ? result.thoughts().length() : 0);

      return new InvokeResponse(request.getSessionId(), result.finalAnswer(), result.thoughts());
    } catch (Exception e) {
      LOG.error(
          "Agent invocation handler failed - agent_id={} session_id={} operation=agent.invoke.error outcome=failure error=\"{}\"",
          request.getAgentId(), request.getSessionId(), e.getMessage(), e);
      throw e;
    }
  }
}
