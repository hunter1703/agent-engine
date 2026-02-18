package com.agentengine.engine.agents;

import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_APP;
import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_USER_ID;
import static com.agentengine.engine.utils.SessionUtils.buildInitialState;
import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.google.adk.agents.LlmAgent;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AgentSessionRuntimeManager {
  private static final Logger LOG = LoggerFactory.getLogger(AgentSessionRuntimeManager.class);

  private final AgentRepository agentRepository;
  private final AgentProvider agentProvider;
  private final SessionServiceProvider sessionServiceProvider;
  private final AgentSessionRepository agentSessionRepository;
  private final ConcurrentMap<String, AgentSessionRuntime> runtimes = new ConcurrentHashMap<>();

  public AgentSessionRuntimeManager(AgentRepository agentRepository, final AgentProvider agentProvider,
      final SessionServiceProvider sessionServiceProvider, AgentSessionRepository agentSessionRepository) {
    this.agentRepository = agentRepository;
    this.agentProvider = agentProvider;
    this.sessionServiceProvider = sessionServiceProvider;
    this.agentSessionRepository = agentSessionRepository;
  }

  public AgentSessionRuntime getOrStartRuntime(String agentId, String sessionId) {
        final AgentSession session = StringUtils.isNotBlank(sessionId) ? agentSessionRepository.findById(sessionId).orElse(null) : null;
        final AgentConfig agentConfig = getAgentConfig(agentId, session);
        if (agentConfig == null) {
            String errorMsg = STR."agentId \"\{agentId}\" has no resolved config";
            LOG.error("Agent configuration resolution failed - agent_id={} error=\"{}\"",
                    agentId, errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        agentConfig.validate();
        final String resolvedSessionId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
        return runtimes.computeIfAbsent(resolvedSessionId, _ -> createRuntime(resolvedSessionId, agentConfig, session == null));
    }

  private AgentSessionRuntime createRuntime(final String sessionId, final AgentConfig agentConfig,
      final boolean createSession) {
    final BaseSessionService sessionService = sessionServiceProvider.get(agentConfig.getSessionStore());

    if (createSession) {
      LOG.debug("Creating new session for agent_id={} session_id={}", agentConfig.getId(), sessionId);
      sessionService.createSession(DEFAULT_APP, DEFAULT_USER_ID, buildInitialState(), sessionId).blockingGet();
    }

    final AgentContext agentContext = new AgentContext(agentConfig, sessionService);
    final LlmAgent agent = agentProvider.get(agentConfig, agentContext);
    final Runner runner = Runner.builder().agent(agent).appName(DEFAULT_APP).sessionService(sessionService).build();

    return new AgentSessionRuntime(sessionId, runner);
  }

  private AgentConfig getAgentConfig(final String agentId, final AgentSession session) {
    if (session != null) {
      return agentRepository.findById(session.getAgentId()).orElse(null);
    }
    return _getConfig(agentId);
  }

  private AgentConfig _getConfig(final String agentId) {
    if (StringUtils.isBlank(agentId)) {
      throw new IllegalArgumentException("agentId cannot be blank");
    } else {
      return agentRepository.findById(agentId).orElse(null);
    }
  }
}
