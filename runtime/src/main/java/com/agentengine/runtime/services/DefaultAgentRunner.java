package com.agentengine.runtime.services;

import com.agentengine.core.api.services.AgentService;
import com.agentengine.runtime.actor.ActorUtils;
import com.agentengine.runtime.actor.AgentRunner;
import com.agentengine.runtime.actor.ChildRegistry;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.SessionCommand;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.runtime.actor.SessionTopology;
import com.agentengine.runtime.actor.SessionTopologyFactory;
import com.agentengine.runtime.factories.agent.AgentProvider;
import com.agentengine.runtime.hitl.SessionPause;
import com.agentengine.runtime.hitl.SessionPauseKind;
import com.agentengine.runtime.utils.ResponseUtils;
import com.agentengine.runtime.utils.SessionUtils;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.JsonUtils;
import com.google.adk.agents.RunConfig;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.memory.InMemoryMemoryService;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class DefaultAgentRunner implements AgentRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentRunner.class);

  private final AgentService agentService;
  private final AgentProvider agentProvider;
  private final ProjectionBackedSessionService sessionService;
  private final Instance<SessionActorFactory> actorFactory;

  @Inject
  public DefaultAgentRunner(final AgentService agentService, final AgentProvider agentProvider,
      final ProjectionBackedSessionService sessionService, final Instance<SessionActorFactory> actorFactory) {
    this.agentService = agentService;
    this.agentProvider = agentProvider;
    this.sessionService = sessionService;
    this.actorFactory = actorFactory;
  }

  @Override
  public void startRun(final SessionTopology topology, final String runId, final String message, final ActorRef<SessionCommand> replyTo) {
    Thread.ofVirtual().start(() -> {
      final String agentId = topology.agentId();
      final String sessionId = topology.sessionId();
      sessionService.createSession(agentId, sessionId, AgentSession.DEFAULT_USER_ID, topology.rootSessionId(), parentSessionId(topology),
          parentAgentId(topology), null).blockingGet();
      sessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.RUNNING);
      execute(topology, runId, buildUserContent(message), replyTo);
    });
  }

  @Override
  public void resumeRun(final SessionTopology topology, final String runId, final String confirmationId, final Object confirmationResponse,
      final ActorRef<SessionCommand> replyTo) {
    Thread.ofVirtual().start(() -> {
      final String agentId = topology.agentId();
      final String sessionId = topology.sessionId();
      final Event confirmationEvent = buildConfirmationEvent(agentId, sessionId, confirmationId, confirmationResponse);
      sessionService
          .appendEvent(sessionService.getSession(agentId, AgentSession.DEFAULT_USER_ID, sessionId, Optional.empty()).blockingGet(),
              confirmationEvent)
          .blockingGet();
      sessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.RUNNING);
      execute(topology, runId, null, replyTo);
    });
  }

  @Override
  public void retryRun(final SessionTopology topology, final String runId, final ActorRef<SessionCommand> replyTo) {
    Thread.ofVirtual().start(() -> execute(topology, runId, null, replyTo));
  }

  @Override
  public void spawnChild(final SessionTopology parentTopology, final String childAgentId, final String childSessionId,
      final String childRunId, final String message, final ActorRef<SessionCommand> parentRef) {
    final var childTopology = SessionTopologyFactory.childTopology(childAgentId, childSessionId, parentTopology.rootSessionId(),
        parentTopology.sessionId(), parentTopology.agentId());
    final var childRef = actorFactory.get().entityRef(childAgentId, childSessionId);
    childRef.<com.agentengine.runtime.actor.SessionReply.InitializeResult>ask(
        replyTo -> new SessionCommand.ExternalCommand.InitializeSession(childTopology, replyTo), ActorUtils.DEFAULT_ASK_TIMEOUT)
        .thenAccept(_ -> childRef.<com.agentengine.runtime.actor.SessionReply.StartRunResult>ask(
            replyTo -> new SessionCommand.ExternalCommand.StartRun(message, replyTo), ActorUtils.DEFAULT_ASK_TIMEOUT));
  }

  @Override
  public void sendChildTask(final SessionTopology parentTopology, final String childAgentId, final String childSessionId,
      final String childRunId, final String message, final ActorRef<SessionCommand> parentRef) {
    actorFactory.get().entityRef(childAgentId, childSessionId).<com.agentengine.runtime.actor.SessionReply.StartRunResult>ask(
        replyTo -> new SessionCommand.ExternalCommand.StartRun(message, replyTo), ActorUtils.DEFAULT_ASK_TIMEOUT);
  }

  @Override
  public void notifyParentOfCompletion(final SessionTopology childTopology, final String runId, final ChildRegistry.ChildRunResult result) {
    if (childTopology.isRoot())
      return;
    final var role = (SessionTopology.SessionRole.Child) childTopology.role();
    actorFactory.get().entityRef(role.parentAgentId(), role.parentSessionId())
        .tell(new SessionCommand.InternalCommand.NotifyChildRunCompleted(childTopology.sessionId(), runId, result));
  }

  private void execute(final SessionTopology topology, final String runId, final Content userContent,
      final ActorRef<SessionCommand> replyTo) {
    final String agentId = topology.agentId();
    final String sessionId = topology.sessionId();
    final Runner runner = buildRunner(agentId, sessionId);
    final AtomicBoolean paused = new AtomicBoolean(false);

    final Disposable disposable = runner.runAsync(AgentSession.DEFAULT_USER_ID, sessionId, userContent, runConfig()).subscribe(event -> {
      final SessionEvent sessionEvent = toSessionEvent(topology, runId, event);
      replyTo.tell(new SessionCommand.InternalCommand.ExecutionEvent(runId, sessionEvent));
      if (isPauseEvent(event)) {
        paused.set(true);
        final SessionPause pause = SessionUtils.pauseView(List.of(event));
        replyTo.tell(new SessionCommand.InternalCommand.PauseRequested(runId, Set.of(pause.confirmationId())));
        sessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.INTERRUPTED);
      }
    }, error -> {
      sessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.ERRORED);
      replyTo.tell(new SessionCommand.InternalCommand.ExecutionFailed(runId, error.getMessage(), false));
      closeQuietly(runner);
    }, () -> {
      if (!paused.get()) {
        sessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.COMPLETED);
        replyTo.tell(new SessionCommand.InternalCommand.ExecutionCompleted(runId));
      }
      closeQuietly(runner);
    });

    if (disposable.isDisposed()) {
      closeQuietly(runner);
    }
  }

  private Runner buildRunner(final String agentId, final String sessionId) {
    final var config = agentService.getAgent(agentId);
    final var agent = agentProvider.create(config);
    return new Runner(agent, agentId, new InMemoryArtifactService(), sessionService, new InMemoryMemoryService());
  }

  private Event buildConfirmationEvent(final String agentId, final String sessionId, final String confirmationId,
      final Object confirmationResponse) {
    final Session session = sessionService.getSession(agentId, AgentSession.DEFAULT_USER_ID, sessionId, Optional.empty()).blockingGet();
    final SessionPause pause = session == null
        ? new SessionPause(SessionPauseKind.UNKNOWN, confirmationId)
        : SessionUtils.pauseView(session.events());
    @SuppressWarnings("unchecked")
    final Map<String, Object> responseData = confirmationResponse instanceof Map<?, ?>
        ? (Map<String, Object>) confirmationResponse
        : Map.of();
    final boolean confirmed = Boolean.TRUE.equals(responseData.get("confirmed"));
    final String answer = responseData.get("answer") instanceof String s ? s : null;
    final var toolConfirmation = ResponseUtils.buildToolConfirmation(pause.kind(), confirmed, answer);
    final FunctionResponse functionResponse = FunctionResponse.builder().id(confirmationId)
        .name(Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME).response(JsonUtils.toMap(toolConfirmation)).build();
    return Event.builder().author("user")
        .content(Content.builder().role("user").parts(List.of(Part.builder().functionResponse(functionResponse).build())).build()).build();
  }

  private static boolean isPauseEvent(final Event event) {
    return event != null && event.actions() != null && event.actions().endInvocation().orElse(false)
        && !event.actions().requestedToolConfirmations().isEmpty();
  }

  private static SessionEvent toSessionEvent(final SessionTopology topology, final String runId, final Event event) {
    return new SessionEvent(event.id(), topology.rootSessionId(), parentSessionId(topology), topology.sessionId(), runId, event.author(),
        event.content().orElse(null), event.partial().orElse(false), event.turnComplete().orElse(false), event.finishReason().orElse(null),
        event.timestamp(), 0L, extractMetadata(event));
  }

  private static Map<String, Object> extractMetadata(final Event event) {
    final Map<String, Object> metadata = new HashMap<>();
    if (event.actions() != null && event.actions().stateDelta() != null) {
      metadata.putAll(event.actions().stateDelta());
    }
    if (event.actions() != null && !event.actions().requestedToolConfirmations().isEmpty()) {
      metadata.put("requestedToolConfirmations", JsonUtils.toMap(event.actions().requestedToolConfirmations()));
    }
    if (event.errorMessage().isPresent()) {
      metadata.put("errorMessage", event.errorMessage().get());
    }
    return metadata;
  }

  private static String parentSessionId(final SessionTopology topology) {
    return topology.role() instanceof SessionTopology.SessionRole.Child c ? c.parentSessionId() : null;
  }

  private static String parentAgentId(final SessionTopology topology) {
    return topology.role() instanceof SessionTopology.SessionRole.Child c ? c.parentAgentId() : null;
  }

  private static Content buildUserContent(final String message) {
    return Content.builder().role("user").parts(List.of(Part.fromText(message))).build();
  }

  private static RunConfig runConfig() {
    return RunConfig.builder().setToolExecutionMode(RunConfig.ToolExecutionMode.PARALLEL).build();
  }

  private static void closeQuietly(final Runner runner) {
    try {
      runner.close().blockingAwait();
    } catch (final Exception ex) {
      LOG.debug("Ignoring runner close failure", ex);
    }
  }
}
