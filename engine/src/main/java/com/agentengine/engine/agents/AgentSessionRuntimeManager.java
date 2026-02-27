package com.agentengine.engine.agents;

import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_USER_ID;
import static com.agentengine.engine.agents.SessionTitleGenerator.buildInitialState;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.google.adk.agents.LlmAgent;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import jakarta.inject.Singleton;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AgentSessionRuntimeManager {
  private static final Logger LOG = LoggerFactory.getLogger(AgentSessionRuntimeManager.class);

  private final AgentService agentService;
  private final AgentProvider agentProvider;
  private final SessionServiceProvider sessionServiceProvider;
  private final SessionService sessionService;
  private final ConcurrentMap<String, AgentSessionRuntime> runtimes = new ConcurrentHashMap<>();

  public AgentSessionRuntimeManager(final AgentService agentService, final AgentProvider agentProvider, final SessionServiceProvider sessionServiceProvider, SessionService sessionService) {
    this.agentService = agentService;
    this.agentProvider = agentProvider;
    this.sessionServiceProvider = sessionServiceProvider;
    this.sessionService = sessionService;
  }

  public AgentSessionRuntime getOrStartRuntime(String agentId, String sessionId) {
    final AgentSession session =
        StringUtils.isNotBlank(sessionId)
            ? sessionService.getSession(sessionId).orElse(null)
            : null;
    final AgentConfig agentConfig = getAgentConfig(agentId, session);
    if (agentConfig == null) {
      String errorMsg = "agentId \"" + agentId + "\" has no resolved config";
      LOG.error(
          "Agent configuration resolution failed - agent_id={} error=\"{}\"", agentId, errorMsg);
      throw new IllegalArgumentException(errorMsg);
    }
    final String resolvedSessionId =
        StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
    return runtimes.computeIfAbsent(
        resolvedSessionId, _ -> createRuntime(resolvedSessionId, agentConfig, session == null));
  }

  private AgentSessionRuntime createRuntime(
      final String sessionId, final AgentConfig agentConfig, final boolean createSession) {
    final BaseSessionService sessionService =
        sessionServiceProvider.get(agentConfig.getSessionStore());

    if (createSession) {
      LOG.debug(
          "Creating new session for agent_id={} session_id={}", agentConfig.getId(), sessionId);
      sessionService
          .createSession(agentConfig.getId(), DEFAULT_USER_ID, buildInitialState(), sessionId)
          .blockingGet();
    }

    final AgentContext agentContext = new AgentContext(agentConfig, sessionService);
    final LlmAgent agent = agentProvider.get(agentConfig, agentContext);
    final Runner runner =
        Runner.builder()
            .agent(agent)
            .appName(agentConfig.getId())
            .sessionService(sessionService)
            .build();

    return new AgentSessionRuntime(sessionId, runner);
  }

  private AgentConfig getAgentConfig(final String agentId, final AgentSession session) {
    return _getConfig(session == null ? agentId : session.getAgentId());
  }

  private AgentConfig _getConfig(final String agentId) {
    if (StringUtils.isBlank(agentId)) {
      throw new IllegalArgumentException("agentId cannot be blank");
    } else {
      return agentService.getAgent(agentId).orElse(null);
    }
  }
}
