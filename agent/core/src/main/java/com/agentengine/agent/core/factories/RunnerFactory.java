package com.agentengine.agent.core.factories;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.agent.core.memory.MemoryService;
import com.agentengine.agent.core.session.SessionRunner;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.infra.agents.ConfiguredAgent;
import com.agentengine.agent.infra.factories.agent.AgentProvider;
import com.agentengine.agent.infra.guardrails.GuardrailPolicyFactory;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.plugins.*;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.session.SessionEventsRepository;
import com.agentengine.agent.infra.tools.planning.PlanningValidator;
import com.agentengine.agent.infra.utils.AgentUtils;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.knowledge.api.services.MediaService;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.adk.agents.BaseAgent;
import com.google.adk.apps.App;
import com.google.adk.events.Event;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pekko.actor.typed.ActorRef;

@Singleton
public class RunnerFactory {
  private final AgentService agentService;
  private final AgentProvider agentProvider;
  private final GuardrailPolicyFactory guardrailPolicyFactory;
  private final SessionService sessionService;
  private final SessionEventsRepository sessionEventsRepository;

  private final KnowledgeService knowledgeService;
  private final MemoryService memoryService;
  private final NotebookService notebookService;
  private final ReminderSyncService reminderSyncService;
  private final PlanningValidator planningValidator;
  private final MediaService mediaService;
  private final ModelProvider modelProvider;
  private final DistributedCache<SessionRunner> runnersCache;
  private final DistributedCache<Runner> tempRunnersCache;
  private final FileService fileService;

  @Inject
  public RunnerFactory(
      AgentService agentService,
      AgentProvider agentProvider,
      GuardrailPolicyFactory guardrailPolicyFactory,
      SessionService sessionService,
      final SessionEventsRepository sessionEventsRepository,
      final KnowledgeService knowledgeService,
      final MemoryService memoryService,
      final NotebookService notebookService,
      final ReminderSyncService reminderSyncService,
      final PlanningValidator planningValidator,
      final MediaService mediaService,
      final ModelProvider modelProvider,
      final DistributedCacheManager cacheManager,
      FileService fileService) {
    this.agentService = agentService;
    this.agentProvider = agentProvider;
    this.guardrailPolicyFactory = guardrailPolicyFactory;
    this.sessionService = sessionService;
    this.sessionEventsRepository = sessionEventsRepository;
    this.knowledgeService = knowledgeService;
    this.memoryService = memoryService;
    this.notebookService = notebookService;
    this.reminderSyncService = reminderSyncService;
    this.planningValidator = planningValidator;
    this.mediaService = mediaService;
    this.modelProvider = modelProvider;
    this.runnersCache =
        new DistributedCache.Builder<SessionRunner>(RuntimeService.RUNNER_CACHE, cacheManager)
            .removalListener(SessionRunner::stop)
            .build();
    this.tempRunnersCache =
        new DistributedCache.Builder<Runner>(RuntimeService.TEMP_RUNNER_CACHE, cacheManager)
            .build();
    this.fileService = fileService;
  }

  /**
   * The session's runner, built as the customer's system: building only loads what the session's
   * agent is configured with, and whether it may be used is checked where it is used — starting the
   * session, and transferring to or spawning an agent.
   */
  public SessionRunner getOrBuild(
      final String agentId, final String sessionId, final ActorRef<SessionCommand> actor) {
    return runnersCache.get(
        cacheKey(agentId, sessionId),
        key -> Context.require().asSystemCaller().get(() -> build(agentId, sessionId, actor)));
  }

  public Runner getOrBuildTemp(final String agentId) {
    return tempRunnersCache.get(
        agentId,
        _ ->
            Context.require()
                .asSystemCaller()
                .get(
                    () -> {
                      final BaseAgentConfig config = agentService.getAgent(agentId);
                      config.getRuntime().setResumable(false);
                      final BaseAgent agent = agentProvider.create(config);
                      final App app =
                          App.builder()
                              .plugins(buildPlugins(agent))
                              .rootAgent(agent)
                              .name(AgentUtils.appName(agentId))
                              .build();
                      return Runner.builder()
                          .app(app)
                          .sessionService(new InMemorySessionService())
                          .memoryService(memoryService)
                          .build();
                    }));
  }

  public void stopped(final SessionRunner runner) {
    runnersCache.invalidateLocally(cacheKey(runner.getAgentId(), runner.getSessionId()));
  }

  private static String cacheKey(final String agentId, final String sessionId) {
    return agentId + ID_SEPARATOR + sessionId;
  }

  private SessionRunner build(
      final String agentId, final String sessionId, final ActorRef<SessionCommand> actor) {
    final BaseAgentConfig config = agentService.getAgent(agentId);
    final BaseAgent agent = agentProvider.create(config);
    final App app =
        App.builder()
            .plugins(buildPlugins(agent))
            .rootAgent(agent)
            .name(AgentUtils.appName(agentId))
            .build();
    final AgentSession agentSession = sessionService.getSession(sessionId);
    final BaseSessionService adkSessionService =
        buildAdkSessionService(app.name(), sessionId, agentSession);
    final Runner runner =
        Runner.builder()
            .app(app)
            .sessionService(adkSessionService)
            .memoryService(memoryService)
            .build();
    return new SessionRunner(
        agentId,
        sessionId,
        actor,
        runner,
        agentSession.getCreatedBy(),
        knowledgeService,
        fileService);
  }

  private BaseSessionService buildAdkSessionService(
      final String appId, final String sessionId, final AgentSession agentSession) {
    final BaseSessionService sessionService = new InMemorySessionService();
    final Map<String, Object> state =
        new ConcurrentHashMap<>(CollectionUtils.nullSafeMap(agentSession.getState()));
    final Session session =
        sessionService
            .createSession(appId, agentSession.getCreatedBy(), state, sessionId)
            .blockingGet();
    for (final Event event : getEvents(sessionId)) {
      sessionService.appendEvent(session, event).blockingGet();
    }
    return sessionService;
  }

  private List<Event> getEvents(final String sessionId) {
    return sessionEventsRepository.getCommittedEvents(sessionId, false).stream()
        .filter(sessionEvent -> sessionEvent.getType() == SessionEvent.Type.NORMAL)
        .map(SessionEvent::getRawEvent)
        .toList();
  }

  private List<BasePlugin> buildPlugins(final BaseAgent rootAgent) {
    final Queue<BaseAgent> queue = new ArrayDeque<>();
    queue.add(rootAgent);
    final Set<String> visited = new HashSet<>();
    final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policies = new LinkedHashMap<>();

    while (!queue.isEmpty()) {
      final BaseAgent agent = queue.poll();
      if (!(agent instanceof ConfiguredAgent configured) || !visited.add(agent.name())) {
        continue;
      }
      final GuardrailPolicyFactory.GuardrailPolicy policy =
          guardrailPolicyFactory.build(configured.getAgentConfig().getGuardrails());
      if (policy.enabled()) {
        policies.put(agent.name(), policy);
      }
      for (final BaseAgent subAgent : CollectionUtils.nullSafeList(agent.subAgents())) {
        queue.add(subAgent);
      }
    }

    final List<BasePlugin> plugins =
        List.of(
            new InitPlugin(notebookService, planningValidator, reminderSyncService),
            new GuardrailPlugin(policies),
            new ReminderPlugin(),
            new ToolResultPlugin(knowledgeService, fileService, mediaService, modelProvider),
            new PlanningPlugin(planningValidator),
            new ResponseValidationPlugin());
    return List.of(new PluginGroup("engine", plugins), AddEventMetadataPlugin.INSTANCE);
  }
}
