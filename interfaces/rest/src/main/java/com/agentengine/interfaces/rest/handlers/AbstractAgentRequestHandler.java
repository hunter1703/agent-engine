package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.google.adk.sessions.Session;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAgentRequestHandler<T> implements AgentRequestHandler<T> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractAgentRequestHandler.class);

  private final AgentRuntimeManager agentManager;

  public AbstractAgentRequestHandler(AgentRuntimeManager agentManager) {
    this.agentManager = agentManager;
  }

  protected AgentRuntime getOrCreateRuntime(final AgentRequest request) {
    LOG.debug("Getting or creating agent engine - agent_id={} config_path=\"{}\"", request.getAgentId(),
        request.getAgentConfigPath());
    LOG.trace("Agent engine request details - agent_id={} type={} config_path=\"{}\" session_id={}",
        request.getAgentId(), request.getType(), request.getAgentConfigPath(), request.getSessionId());
    return agentManager.getOrStartRuntime(request.getAgentId(), request.getAgentConfigPath());
  }

  protected String ensureSession(final AgentRuntime runtime, final String sessionId) {
    final String resolvedSessionId = StringUtils.isNotBlank(sessionId) ? sessionId : UUID.randomUUID().toString();
    final Session existing = runtime.sessionService()
        .getSession(runtime.appName(), AgentRuntimeManager.DEFAULT_USER_ID, resolvedSessionId, Optional.empty())
        .blockingGet();
    if (existing == null) {
      runtime.sessionService()
          .createSession(runtime.appName(), AgentRuntimeManager.DEFAULT_USER_ID, null, resolvedSessionId).blockingGet();
    }
    return resolvedSessionId;
  }
}
