package com.agentengine.agent.core.factories;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.agent.core.memory.MemoryService;
import com.agentengine.agent.core.session.SessionRunner;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.context.ContextManager;
import com.agentengine.agent.infra.factories.agent.AgentProvider;
import com.agentengine.agent.infra.factories.context.ContextManagerProvider;
import com.agentengine.agent.infra.guardrails.GuardrailPolicyFactory;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.plugins.*;
import com.agentengine.agent.infra.session.SessionEventsRepository;
import com.agentengine.agent.infra.utils.AgentUtils;
import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.adk.agents.BaseAgent;
import com.google.adk.apps.App;
import com.google.adk.events.Event;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pekko.actor.typed.ActorRef;

@Singleton
public class RunnerFactory {

  private final AgentService agentService;
  private final AgentProvider agentProvider;
  private final ContextManagerProvider contextManagerProvider;
  private final GuardrailPolicyFactory guardrailPolicyFactory;
  private final SessionService sessionService;
  private final SessionEventsRepository sessionEventsRepository;

  private final KnowledgeService knowledgeService;
  private final MemoryService memoryService;
  private final NotebookService notebookService;
  private final DistributedCache<SessionRunner> cache;

  public RunnerFactory(
      AgentService agentService,
      AgentProvider agentProvider,
      ContextManagerProvider contextManagerProvider,
      GuardrailPolicyFactory guardrailPolicyFactory,
      SessionService sessionService,
      final SessionEventsRepository sessionEventsRepository,
      final KnowledgeService knowledgeService,
      final MemoryService memoryService,
      final NotebookService notebookService,
      final DistributedCacheManager cacheManager) {
    this.agentService = agentService;
    this.agentProvider = agentProvider;
    this.contextManagerProvider = contextManagerProvider;
    this.guardrailPolicyFactory = guardrailPolicyFactory;
    this.sessionService = sessionService;
    this.sessionEventsRepository = sessionEventsRepository;
    this.knowledgeService = knowledgeService;
    this.memoryService = memoryService;
    this.notebookService = notebookService;
    this.cache =
        new DistributedCache.Builder<SessionRunner>(RuntimeService.RUNNER_CACHE, cacheManager)
            .removalListener(SessionRunner::stop)
            .build();
  }

  /**
   * The session's runner, built as the customer's system: building only loads what the session's
   * agent is configured with, and whether it may be used is checked where it is used — starting the
   * session, and transferring to or spawning an agent.
   */
  public SessionRunner getOrBuild(
      final String agentId, final String sessionId, final ActorRef<SessionCommand> actor) {
    return cache.get(
        cacheKey(agentId, sessionId),
        key -> Context.require().asSystemCaller().get(() -> build(agentId, sessionId, actor)));
  }

  public void stopped(final SessionRunner runner) {
    cache.invalidateLocally(cacheKey(runner.getAgentId(), runner.getSessionId()));
  }

  private static String cacheKey(final String agentId, final String sessionId) {
    return agentId + ID_SEPARATOR + sessionId;
  }

  private SessionRunner build(
      final String agentId, final String sessionId, final ActorRef<SessionCommand> actor) {
    final BaseAgentConfig config = agentService.getAgent(agentId);
    final Agent agent = agentProvider.create(config);
    final App app =
        App.builder()
            .plugins(buildPlugins(agent))
            .rootAgent(agent)
            .name(AgentUtils.appName(agentId))
            .build();
    final AgentSession agentSession = sessionService.getSession(sessionId);
    final InMemorySessionService inMemorySessionService =
        buildInMemorySessionService(app.name(), sessionId, agentSession);
    final Runner runner =
        Runner.builder()
            .app(app)
            .sessionService(inMemorySessionService)
            .memoryService(memoryService)
            .build();
    return new SessionRunner(agentId, sessionId, actor, runner, agentSession.getCreatedBy());
  }

  private InMemorySessionService buildInMemorySessionService(
      final String appId, final String sessionId, final AgentSession agentSession) {
    final InMemorySessionService inMemorySessionService = new InMemorySessionService();
    final Session persistedSession = SessionUtils.toSession(agentSession, getEvents(sessionId));

    final ConcurrentHashMap<String, Object> initialState =
        persistedSession == null
            ? new ConcurrentHashMap<>()
            : new ConcurrentHashMap<>(
                persistedSession.state() == null ? Map.of() : persistedSession.state());
    final Session session =
        inMemorySessionService
            .createSession(appId, agentSession.getCreatedBy(), initialState, sessionId)
            .blockingGet();

    if (persistedSession != null) {

      for (final Event event : CollectionUtils.nullSafeList(persistedSession.events())) {
        inMemorySessionService
            .appendEvent(session, EventUtils.enrichWithAttachments(event))
            .blockingGet();
      }
    }
    return inMemorySessionService;
  }

  private List<Event> getEvents(final String sessionId) {
    return sessionEventsRepository.getCommittedEvents(sessionId, false).stream()
        .filter(sessionEvent -> sessionEvent.getType() == SessionEvent.Type.NORMAL)
        .map(SessionEvent::getRawEvent)
        .toList();
  }

  private List<BasePlugin> buildPlugins(final Agent rootAgent) {
    final Queue<Agent> queue = new ArrayDeque<>();
    queue.add(rootAgent);
    final Set<String> visited = new HashSet<>();
    final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policies = new LinkedHashMap<>();
    final Map<String, ContextManager> contextManagers = new LinkedHashMap<>();
    final Set<String> agentsWithNotebook = new HashSet<>();

    while (!queue.isEmpty()) {
      final Agent agent = queue.poll();
      if (!visited.add(agent.name())) {
        continue;
      }
      contextManagers.put(agent.name(), contextManagerProvider.create(agent.getAgentConfig()));
      final GuardrailPolicyFactory.GuardrailPolicy policy =
          guardrailPolicyFactory.build(agent.getAgentConfig().getGuardrails());
      if (policy.enabled()) {
        policies.put(agent.name(), policy);
      }
      if (CollectionUtils.nullSafeList(agent.getAgentConfig().getTools()).stream()
          .anyMatch(tool -> Constants.Toolsets.NOTEBOOK.equals(tool.getToolName()))) {
        agentsWithNotebook.add(agent.name());
      }
      for (final BaseAgent subAgent : CollectionUtils.nullSafeList(agent.subAgents())) {
        if (subAgent instanceof Agent nestedAgent) {
          queue.add(nestedAgent);
        }
      }
    }

    final List<BasePlugin> plugins =
        List.of(
            new InitPlugin(),
            new KnowledgePlugin(knowledgeService),
            new GuardrailPlugin(policies),
            new ContextManagementPlugin(contextManagers),
            new ReminderPlugin(),
            new PlanningPlugin(),
            new ResponseValidationPlugin(),
            new NotebookPlugin(notebookService, agentsWithNotebook));
    return List.of(new PluginGroup("engine", plugins), AddEventMetadataPlugin.INSTANCE);
  }
}
