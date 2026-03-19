package com.agentengine.engine.services;

import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_USER_ID;
import static com.agentengine.engine.utils.SessionUtils.buildInitialState;
import static com.google.adk.agents.RunConfig.StreamingMode.SSE;
import static com.google.adk.flows.llmflows.Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME;
import static com.google.genai.types.Part.fromText;

import com.agentengine.engine.agents.AgentSessionRuntime;
import com.agentengine.engine.agents.SessionTitleGenerator;
import com.agentengine.engine.agui.AGUIEventMapper;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.events.SessionDeletedEvent;
import com.agentengine.engine.factories.agent.AgentProvider;
import com.agentengine.engine.factories.context.ContextManagerProvider;
import com.agentengine.engine.guardrails.GuardrailPlugin;
import com.agentengine.engine.guardrails.GuardrailPolicyFactory;
import com.agentengine.engine.hitl.SessionPause;
import com.agentengine.engine.plugin.Agent;
import com.agentengine.engine.plugin.ContextManager;
import com.agentengine.engine.plugins.ContextManagementPlugin;
import com.agentengine.engine.plugins.PluginGroup;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.engine.utils.ResponseUtils;
import com.agentengine.engine.utils.SessionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.RefCountedCache;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Utils;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.core.event.BaseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.apps.App;
import com.google.adk.apps.ResumabilityConfig;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.quarkus.arc.Unremovable;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class AgentExecutionServiceImpl implements AgentExecutionService {
  private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionServiceImpl.class);
  private static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 30L;

  private final AgentService agentService;
  private final AgentProvider agentProvider;
  private final AgentSessionRepository sessionRepository;
  private final SessionService sessionService;
  private final GuardrailPolicyFactory guardrailPolicyFactory;
  private final SessionTitleGenerator sessionTitleGenerator;
  private final ContextManagerProvider contextManagerProvider;
  private final RefCountedCache<String, AgentSessionRuntime> runtimeCache;

  @Inject
  public AgentExecutionServiceImpl(final AgentService agentService, final AgentProvider agentProvider,
      final AgentSessionRepository sessionRepository, final SessionService sessionService,
      final GuardrailPolicyFactory guardrailPolicyFactory, final SessionTitleGenerator sessionTitleGenerator,
      final ContextManagerProvider contextManagerProvider) {
    this.agentService = agentService;
    this.agentProvider = agentProvider;
    this.sessionRepository = sessionRepository;
    this.sessionService = sessionService;
    this.guardrailPolicyFactory = guardrailPolicyFactory;
    this.sessionTitleGenerator = sessionTitleGenerator;
    this.contextManagerProvider = contextManagerProvider;
    this.runtimeCache = RefCountedCache.<String, AgentSessionRuntime>builder().name("agent-runtime")
        .idleTimeout(DEFAULT_IDLE_TIMEOUT_MINUTES, TimeUnit.MINUTES).cleanupInterval(30, TimeUnit.SECONDS).build();
  }

  @Override
  public Flowable<BaseEvent> run(final String agentId, final String sessionId, final String text) {
    return runContent(agentId, sessionId, buildFromText(text));
  }

  @Override
  public Flowable<BaseEvent> resumeSession(final String agentId, final String sessionId, final Boolean confirmed, final String answer) {
    if (StringUtils.isBlank(sessionId)) {
      throw new IllegalArgumentException("sessionId is required");
    }
    final AgentSession session = sessionService.getSession(sessionId).orElseThrow(() -> new AssetNotFoundException("session", sessionId));
    final SessionPause pauseView = SessionUtils.pauseView(Utils.toType(session.getSessionInfo().getEvents(), new TypeReference<>() {
    }));
    if (!pauseView.isPaused() || !pauseView.hasConfirmationId()) {
      throw new IllegalArgumentException("Session is not waiting for user input");
    }
    final ToolConfirmation toolConfirmation = ResponseUtils.buildToolConfirmation(pauseView.kind(), confirmed, answer);

    final FunctionResponse functionResponse = FunctionResponse.builder().id(pauseView.confirmationId())
        .name(REQUEST_CONFIRMATION_FUNCTION_CALL_NAME).response(JsonUtils.toMap(toolConfirmation)).build();
    final Content content = Content.builder().role("user").parts(List.of(Part.builder().functionResponse(functionResponse).build()))
        .build();
    return runContent(agentId, sessionId, content);
  }

  private Flowable<BaseEvent> runContent(final String agentId, final String sessionId, final Content userContent) {
    final AgentSessionRuntime runtime = getOrStartRuntime(agentId, sessionId);
    final String resolvedSessionId = runtime.sessionId();
    try {
      LOG.debug("run - session_id={} agent_id={} content_parts={}", resolvedSessionId, agentId,
          userContent == null ? 0 : userContent.parts().orElse(List.of()).size());
      final RunConfig runConfig = buildRunConfig(runtime.agentConfig());
      final Runner runner = runtime.runner();
      final AGUIEventMapper mapper = new AGUIEventMapper(sessionId, agentId, AGUIEventMapper.Mode.LIVE);
      return mapper.map(runner.runAsync(DEFAULT_USER_ID, resolvedSessionId, userContent, runConfig)
          .doOnComplete(() -> updateTitle(runner, resolvedSessionId)).doFinally(() -> markRunInactive(resolvedSessionId)));
    } catch (Exception ex) {
      markRunInactive(resolvedSessionId);
      return Flowable.error(ex);
    }
  }

  private static RunConfig buildRunConfig(final BaseAgentConfig agentConfig) {
    return RunConfig.builder().setStreamingMode(SSE)
        .setToolExecutionMode(RunConfig.ToolExecutionMode.valueOf(agentConfig.getToolExecutionMode())).build();
  }

  public void onSessionDeleted(@Observes final SessionDeletedEvent event) {
    runtimeCache.invalidate(event.sessionId());
  }

  private void updateTitle(final Runner runner, final String sessionId) {
    final Session session = Objects
        .requireNonNull(runner.sessionService().getSession(runner.appName(), DEFAULT_USER_ID, sessionId, Optional.empty()).blockingGet());
    sessionTitleGenerator.generateTitle(session.events()).ifPresent(title -> sessionService.updateTitle(sessionId, title));
  }

  private static Content buildFromText(final String text) {
    return Content.fromParts(fromText(text));
  }

  private AgentSessionRuntime getOrStartRuntime(final String agentId, final String sessionId) {
    final AgentSession session = StringUtils.isNotBlank(sessionId) ? sessionService.getSession(sessionId).orElse(null) : null;
    final BaseAgentConfig agentConfig = getAgentConfig(agentId, session);
    final String resolvedSessionId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
    return runtimeCache.getAndAcquire(resolvedSessionId, ignored -> createRuntime(resolvedSessionId, agentConfig, session == null));
  }

  private void markRunInactive(final String sessionId) {
    runtimeCache.release(sessionId);
  }

  private AgentSessionRuntime createRuntime(final String sessionId, final BaseAgentConfig agentConfig, final boolean createSession) {
    if (createSession) {
      LOG.debug("Creating new session for agent_id={} session_id={}", agentConfig.getId(), sessionId);
      sessionRepository.createSession(agentConfig.getId(), DEFAULT_USER_ID, buildInitialState(), sessionId).blockingGet();
    }

    final Agent agent = agentProvider.create(agentConfig);
    final PluginGroup pluginGroup = buildPluginGroup(agent);
    final App.Builder appBuilder = App.builder().name(runtimeAppName(agentConfig.getId())).rootAgent(agent).plugins(List.of(pluginGroup))
        .resumabilityConfig(new ResumabilityConfig(isResumable(agentConfig)));

    final Runner runner = Runner.builder().app(appBuilder.build()).sessionService(sessionRepository).build();
    return new AgentSessionRuntime(sessionId, runner, agentConfig);
  }

  static String runtimeAppName(final String agentId) {
    return agentId.replaceAll("[^A-Za-z0-9_]", "_");
  }

  private static boolean isResumable(final BaseAgentConfig config) {
    if (config == null || config.getRuntime() == null) {
      return true;
    }
    return config.getRuntime().isResumable();
  }

  private BaseAgentConfig getAgentConfig(final String agentId, final AgentSession session) {
    return getConfig(session == null ? agentId : session.getAgentId());
  }

  private BaseAgentConfig getConfig(final String agentId) {
    if (StringUtils.isBlank(agentId)) {
      throw new IllegalArgumentException("agentId cannot be blank");
    }
    return agentService.getAgent(agentId).orElseThrow(() -> new AssetNotFoundException("agent", agentId));
  }

  private PluginGroup buildPluginGroup(final Agent rootAgent) {
    final Queue<Agent> queue = new LinkedList<>();
    queue.add(rootAgent);

    final Set<String> visited = new HashSet<>();
    final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policies = new LinkedHashMap<>();
    final Map<String, ContextManager> contextManagers = new LinkedHashMap<>();
    while (!queue.isEmpty()) {
      final Agent agent = queue.poll();
      if (!visited.add(agent.name())) {
        continue;
      }
      contextManagers.put(agent.name(), contextManagerProvider.create(agent.getAgentConfig()));
      final GuardrailPolicyFactory.GuardrailPolicy policy = guardrailPolicyFactory.build(agent.getAgentConfig().getGuardrails());
      if (policy.enabled()) {
        policies.put(agent.name(), policy);
      }
      for (final BaseAgent subAgent : agent.subAgents()) {
        if (subAgent instanceof Agent llmSubAgent) {
          queue.add(llmSubAgent);
        }
      }
    }
    return new PluginGroup("engine", List.of(new GuardrailPlugin(policies), new ContextManagementPlugin(contextManagers)));
  }
}
