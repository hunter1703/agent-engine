package com.agentengine.runtime.services;

import com.agentengine.core.api.services.AgentService;
import com.agentengine.runtime.actor.AgentRunner;
import com.agentengine.runtime.actor.SessionActor;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.SessionEvent;
import com.agentengine.runtime.factories.agent.AgentProvider;
import com.agentengine.runtime.hitl.SessionPause;
import com.google.adk.sessions.Session;
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
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class DefaultAgentRunner implements AgentRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentRunner.class);

    private final AgentService agentService;
    private final AgentProvider agentProvider;
    private final MongoSessionService mongoSessionService;
    private final Instance<SessionActorFactory> actorFactory;

    @Inject
    public DefaultAgentRunner(final AgentService agentService,
                              final AgentProvider agentProvider,
                              final MongoSessionService mongoSessionService,
                              final Instance<SessionActorFactory> actorFactory) {
        this.agentService = agentService;
        this.agentProvider = agentProvider;
        this.mongoSessionService = mongoSessionService;
        this.actorFactory = actorFactory;
    }

    @Override
    public void startRun(final String agentId,
                         final String sessionId,
                         final String rootSessionId,
                         final String parentSessionId,
                         final String parentAgentId,
                         final String runId,
                         final String message,
                         final ActorRef<SessionActor.Command> replyTo) {
        // Run setup and execution on a virtual thread to avoid blocking the actor dispatcher.
        Thread.ofVirtual().start(() -> {
            mongoSessionService.createSession(agentId, sessionId, rootSessionId, parentSessionId, parentAgentId).blockingGet();
            mongoSessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.RUNNING);
            execute(agentId, sessionId, rootSessionId, parentSessionId, runId, buildUserContent(message), replyTo);
        });
    }

    @Override
    public void resumeRun(final String agentId,
                          final String sessionId,
                          final String runId,
                          final String confirmationId,
                          final boolean confirmed,
                          final String answer,
                          final ActorRef<SessionActor.Command> replyTo) {
        Thread.ofVirtual().start(() -> {
            final Event confirmationEvent = buildConfirmationResponseEvent(agentId, sessionId, confirmationId, confirmed, answer);
            mongoSessionService.appendEvent(
                    mongoSessionService.getSession(agentId, AgentSession.DEFAULT_USER_ID, sessionId, Optional.empty()).blockingGet(),
                    confirmationEvent
            ).blockingGet();
            mongoSessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.RUNNING);
            execute(agentId, sessionId, null, null, runId, null, replyTo);
        });
    }

    @Override
    public void retryRun(final String agentId,
                         final String sessionId,
                         final String runId,
                         final ActorRef<SessionActor.Command> replyTo) {
        Thread.ofVirtual().start(() -> execute(agentId, sessionId, null, null, runId, null, replyTo));
    }

    private void execute(final String agentId,
                         final String sessionId,
                         final String rootSessionId,
                         final String parentSessionId,
                         final String runId,
                         final Content userContent,
                         final ActorRef<SessionActor.Command> replyTo) {
        final Runner runner = buildRunner(agentId, sessionId);
        final AtomicReference<FinishReason> finishReasonRef = new AtomicReference<>();
        final AtomicReference<String> finalAnswerRef = new AtomicReference<>();
        final AtomicBoolean paused = new AtomicBoolean(false);

        final Disposable disposable = runner.runAsync(AgentSession.DEFAULT_USER_ID, sessionId, userContent, runConfig())
                .subscribe(event -> {
                    finishReasonRef.set(event.finishReason().orElse(null));
                    final SessionEvent sessionEvent = toSessionEvent(sessionId, rootSessionId, parentSessionId, runId, event);
                    replyTo.tell(new SessionActor.Command.ExecutionEvent(sessionEvent));
                    if (event.turnComplete().orElse(false) && !event.partial().orElse(false)) {
                        event.content().map(Content::text).ifPresent(finalAnswerRef::set);
                    }
                    if (isPauseEvent(event)) {
                        paused.set(true);
                        final SessionPause pause = SessionUtils.pauseView(List.of(event));
                        replyTo.tell(new SessionActor.Command.PauseRequested(runId, pause.confirmationId(), pause.kind()));
                        mongoSessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.INTERRUPTED);
                    }
                }, error -> {
                    mongoSessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.ERRORED);
                    replyTo.tell(new SessionActor.Command.ExecutionFailed(runId, error.getMessage(), false));
                    closeQuietly(runner);
                }, () -> {
                    if (!paused.get()) {
                        mongoSessionService.updateSessionStatus(sessionId, AgentSession.AgentSessionStatus.COMPLETED);
                        replyTo.tell(new SessionActor.Command.ExecutionCompleted(runId, finishReasonRef.get(), finalAnswerRef.get()));
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
        return new Runner(agent, agentId, new InMemoryArtifactService(), mongoSessionService, new InMemoryMemoryService());
    }

    private static RunConfig runConfig() {
        return RunConfig.builder()
                .setToolExecutionMode(RunConfig.ToolExecutionMode.PARALLEL)
                .build();
    }

    private static Content buildUserContent(final String message) {
        return Content.builder().role("user").parts(List.of(Part.fromText(message))).build();
    }

    private Event buildConfirmationResponseEvent(final String agentId,
                                                 final String sessionId,
                                                 final String confirmationId,
                                                 final boolean confirmed,
                                                 final String answer) {
        final Session session = mongoSessionService.getSession(agentId, AgentSession.DEFAULT_USER_ID,
                sessionId, Optional.empty()).blockingGet();
        final SessionPause pause = session == null
                ? new SessionPause(SessionPauseKind.UNKNOWN, confirmationId)
                : SessionUtils.pauseView(session.events());
        final var toolConfirmation = ResponseUtils.buildToolConfirmation(pause.kind(), confirmed, answer);
        final FunctionResponse functionResponse = FunctionResponse.builder()
                .id(confirmationId)
                .name(Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
                .response(JsonUtils.toMap(toolConfirmation))
                .build();
        return Event.builder()
                .author("user")
                .content(Content.builder().role("user")
                        .parts(List.of(Part.builder().functionResponse(functionResponse).build()))
                        .build())
                .build();
    }

    private static boolean isPauseEvent(final Event event) {
        return event != null
                && event.actions() != null
                && event.actions().endInvocation().orElse(false)
                && !event.actions().requestedToolConfirmations().isEmpty();
    }

    private static SessionEvent toSessionEvent(final String sessionId,
                                               final String rootSessionId,
                                               final String parentSessionId,
                                               final String runId,
                                               final Event event) {
        return new SessionEvent(
                event.id(),
                rootSessionId,
                parentSessionId,
                sessionId,
                runId,
                event.author(),
                event.content().orElse(null),
                event.partial().orElse(false),
                event.turnComplete().orElse(false),
                event.finishReason().orElse(null),
                event.timestamp(),
                0L,
                extractMetadata(event)
        );
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

    private static void closeQuietly(final Runner runner) {
        try {
            runner.close().blockingAwait();
        } catch (final Exception ex) {
            LOG.debug("Ignoring runner close failure", ex);
        }
    }
}
