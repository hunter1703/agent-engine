package com.agentengine.agent.core.factories;

import com.agentengine.agent.core.memory.MemoryService;
import com.agentengine.agent.core.session.SessionActorJournal;
import com.agentengine.agent.core.session.SessionRunner;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.events.RollbackFact;
import com.agentengine.agent.core.session.events.SessionFact;
import com.agentengine.agent.core.session.events.TurnCommittedFact;
import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.context.ContextManager;
import com.agentengine.agent.infra.factories.agent.AgentProvider;
import com.agentengine.agent.infra.factories.context.ContextManagerProvider;
import com.agentengine.agent.infra.guardrails.GuardrailPolicyFactory;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.plugins.*;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.repository.SessionEventsRepository;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.apps.App;
import com.google.adk.events.Event;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.plugins.LoggingPlugin;
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
  private final SessionActorJournal sessionActorJournal;
  private final SessionEventsRepository sessionEventsRepository;

  private final KnowledgeService knowledgeService;
  private final MemoryService memoryService;
  private final NotebookRepository notebookRepository;
  private final NotesRepository notesRepository;

  public RunnerFactory(
      AgentService agentService,
      AgentProvider agentProvider,
      ContextManagerProvider contextManagerProvider,
      GuardrailPolicyFactory guardrailPolicyFactory,
      SessionService sessionService,
      final SessionActorJournal sessionActorJournal,
      final SessionEventsRepository sessionEventsRepository,
      final KnowledgeService knowledgeService,
      final MemoryService memoryService,
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository) {
    this.agentService = agentService;
    this.agentProvider = agentProvider;
    this.contextManagerProvider = contextManagerProvider;
    this.guardrailPolicyFactory = guardrailPolicyFactory;
    this.sessionService = sessionService;
    this.sessionActorJournal = sessionActorJournal;
    this.sessionEventsRepository = sessionEventsRepository;
    this.knowledgeService = knowledgeService;
    this.memoryService = memoryService;
    this.notebookRepository = notebookRepository;
    this.notesRepository = notesRepository;
  }

  public SessionRunner buildRunner(
      final String agentId, final String sessionId, final ActorRef<SessionCommand> actor) {
    final BaseAgentConfig config = agentService.getAgent(agentId);
    final Agent agent = agentProvider.create(config);
    final App app =
        App.builder().plugins(buildPlugins(agent)).rootAgent(agent).name(agentId).build();
    final InMemorySessionService inMemorySessionService =
        buildInMemorySessionService(agentId, sessionId);
    final Runner runner =
        Runner.builder()
            .app(app)
            .sessionService(inMemorySessionService)
            .memoryService(memoryService)
            .build();
    return new SessionRunner(sessionId, actor, agent, runner);
  }

  private InMemorySessionService buildInMemorySessionService(
      final String agentId, final String sessionId) {
    final InMemorySessionService inMemorySessionService = new InMemorySessionService();
    final AgentSession agentSession = sessionService.getSession(sessionId, false);
    final Session persistedSession = SessionUtils.toSession(agentSession, getEvents(sessionId));

    final ConcurrentHashMap<String, Object> initialState =
        persistedSession == null
            ? new ConcurrentHashMap<>()
            : new ConcurrentHashMap<>(
                persistedSession.state() == null ? Map.of() : persistedSession.state());
    final Session session =
        inMemorySessionService
            .createSession(agentId, AgentSession.DEFAULT_USER_ID, initialState, sessionId)
            .blockingGet();

    if (persistedSession != null) {

      for (final var event : CollectionUtils.nullSafeList(persistedSession.events())) {
        inMemorySessionService.appendEvent(session, event).blockingGet();
      }
    }
    return inMemorySessionService;
  }

  private List<Event> getEvents(final String sessionId) {
    final List<SessionFact> facts =
        sessionActorJournal.readSessionEvents(
            sessionId, fact -> fact instanceof TurnCommittedFact || fact instanceof RollbackFact);

    // Applies each RollbackFact against the turns committed so far, using only the lightweight
    // journal facts, before any event content is fetched: a rolled-back turn's events never need
    // to leave Mongo in the first place.
    final List<TurnCommittedFact> survivingTurns = new LinkedList<>();
    for (final SessionFact fact : facts) {
      if (fact instanceof TurnCommittedFact committed) {
        survivingTurns.add(committed);
      } else if (fact instanceof RollbackFact rollback) {
        final String runId = rollback.getRunId();
        boolean foundRun = false;
        while (true) {
          if (!foundRun) {
            foundRun = Objects.equals(survivingTurns.getLast().getRunId(), runId);
          } else if (!Objects.equals(survivingTurns.getLast().getRunId(), runId)) {
            break;
          }
          survivingTurns.removeLast();
        }
      }
    }

    final List<String> turnIds = survivingTurns.stream().map(TurnCommittedFact::getTurnId).toList();
    return sessionEventsRepository.getCommittedSessionEvents(sessionId, turnIds, false).stream()
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
            new InitPlugin(knowledgeService, notebookRepository),
            new GuardrailPlugin(policies),
            new ContextManagementPlugin(contextManagers),
            new NotebookPlugin(notesRepository, agentsWithNotebook),
            new ReminderPlugin(),
            new PlanningPlugin(),
            new ResponseValidationPlugin(),
            new LoggingPlugin());
    return List.of(new PluginGroup("engine", plugins), AddEventMetadataPlugin.INSTANCE);
  }
}
