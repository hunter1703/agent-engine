package com.agentengine.engine.agents;

import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_USER_ID;
import static com.agentengine.engine.utils.SessionUtils.buildInitialState;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.events.SessionDeletedEvent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages {@link AgentSessionRuntime} instances, one per session.
 *
 * <p>Runtimes for sessions with active (in-progress) runs are kept in {@code activeRuntimes} and
 * are never evicted. Once a run completes, the runtime moves to {@code idleRuntimes}, a Guava
 * cache that evicts entries that have been idle for longer than
 * {@code agentengine.session.runtime.idle.timeout.minutes} (default 30 min).
 */
@Singleton
public class AgentSessionRuntimeManager {
  private static final Logger LOG = LoggerFactory.getLogger(AgentSessionRuntimeManager.class);
  private static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 30L;

  @ConfigProperty(
      name = "agentengine.session.runtime.idle.timeout.minutes",
      defaultValue = "30")
  long idleTimeoutMinutes;

  private final AgentService agentService;
  private final AgentProvider agentProvider;
  private final SessionServiceProvider sessionServiceProvider;
  private final SessionService sessionService;

  /** Sessions whose run is currently in progress — never evicted. */
  private final ConcurrentMap<String, AgentSessionRuntime> activeRuntimes =
      new ConcurrentHashMap<>();

  /** Sessions between runs — evicted after {@code idleTimeoutMinutes} of inactivity. */
  private Cache<String, AgentSessionRuntime> idleRuntimes;

  public AgentSessionRuntimeManager(
      final AgentService agentService,
      final AgentProvider agentProvider,
      final SessionServiceProvider sessionServiceProvider,
      final SessionService sessionService) {
    this.agentService = agentService;
    this.agentProvider = agentProvider;
    this.sessionServiceProvider = sessionServiceProvider;
    this.sessionService = sessionService;
    // Initialize with the default; @PostConstruct will reinitialize with the configured value
    this.idleRuntimes = buildIdleCache(DEFAULT_IDLE_TIMEOUT_MINUTES);
  }

  @PostConstruct
  void init() {
    idleRuntimes = buildIdleCache(idleTimeoutMinutes);
  }

  private static Cache<String, AgentSessionRuntime> buildIdleCache(long timeoutMinutes) {
    return CacheBuilder.newBuilder()
        .expireAfterAccess(timeoutMinutes, TimeUnit.MINUTES)
        .removalListener(
            notification ->
                LOG.debug(
                    "Idle session runtime evicted: session_id={} cause={}",
                    notification.getKey(),
                    notification.getCause()))
        .build();
  }

  /**
   * Returns the runtime for {@code sessionId} if one exists (active or idle), or creates a new one
   * bound to {@code agentId}. The caller must call {@link #markRunActive(String)} before starting a
   * run and {@link #markRunInactive(String)} when it completes.
   */
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

    // Active first: a concurrent run is already holding this runtime
    AgentSessionRuntime existing = activeRuntimes.get(resolvedSessionId);
    if (existing != null) {
      return existing;
    }

    // Idle cache: runtime exists but no run is currently active
    existing = idleRuntimes.getIfPresent(resolvedSessionId);
    if (existing != null) {
      return existing;
    }

    // Create and register in idle cache
    AgentSessionRuntime newRuntime =
        createRuntime(resolvedSessionId, agentConfig, session == null);
    idleRuntimes.put(resolvedSessionId, newRuntime);
    return newRuntime;
  }

  /**
   * Moves the runtime for {@code sessionId} from the idle cache into the active map so it is not
   * evicted while a run is in progress. Must be paired with {@link #markRunInactive(String)}.
   */
  public void markRunActive(String sessionId) {
    AgentSessionRuntime runtime = idleRuntimes.getIfPresent(sessionId);
    if (runtime != null) {
      idleRuntimes.invalidate(sessionId);
      activeRuntimes.put(sessionId, runtime);
    }
    // If already in activeRuntimes (concurrent run), nothing to do
  }

  /**
   * Moves the runtime for {@code sessionId} back to the idle cache once a run has finished.
   * Must be called from a terminal operator ({@code doOnTerminate} / {@code doFinally}).
   */
  public void markRunInactive(String sessionId) {
    AgentSessionRuntime runtime = activeRuntimes.remove(sessionId);
    if (runtime != null) {
      idleRuntimes.put(sessionId, runtime);
    }
  }

  /** Removes the cached runtime when its session has been deleted. */
  void onSessionDeleted(@Observes SessionDeletedEvent event) {
    activeRuntimes.remove(event.sessionId());
    idleRuntimes.invalidate(event.sessionId());
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
