package com.agentengine.interfaces.rest.handlers;

import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.interfaces.rest.services.AgentManager;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InvokeAgentRequestHandler extends AbstractAgentRequestHandler<AgentResponse> {
  private static final Logger LOG = LoggerFactory.getLogger(InvokeAgentRequestHandler.class);

  public InvokeAgentRequestHandler(final AgentManager agentManager) {
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
      final Agent engine = getOrCreateEngine(request);
      final String sessionId = request.getSessionId();
      Message response = engine.invoke(sessionId, Message.user(request.getMessage()), Agent.NO_OP_LISTENER);

      LOG.info(
          "Agent invocation handler completed - agent_id={} session_id={} operation=agent.invoke.complete outcome=success",
          request.getAgentId(), request.getSessionId());
      LOG.debug("Agent invocation handler response - agent_id={} session_id={} response_length={} thoughts_length={}",
          request.getAgentId(), request.getSessionId(),
          response != null && response.getContent() != null ? response.getContent().length() : 0,
          response != null && response.getThoughts() != null ? response.getThoughts().length() : 0);

      return new InvokeResponse(sessionId, response == null ? null : response.getContent(),
          response == null ? null : response.getThoughts());
    } catch (Exception e) {
      LOG.error(
          "Agent invocation handler failed - agent_id={} session_id={} operation=agent.invoke.error outcome=failure error=\"{}\"",
          request.getAgentId(), request.getSessionId(), e.getMessage(), e);
      throw e;
    }
  }
}
