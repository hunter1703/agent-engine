package com.agentengine.agent.core.session;

import com.agentengine.agent.core.session.state.RunState;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.time.ZonedDateTime;
import java.util.Map;
import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.ContentUtils;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.commands.SelfCommand.CompleteRunCommand;
import com.agentengine.agent.core.session.commands.SelfCommand.PublishEventCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.utils.ExceptionUtils;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import java.util.Collection;
import org.apache.pekko.actor.typed.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionRunner {

  private static final RunConfig RUN_CONFIG =
      RunConfig.builder()
          .toolExecutionMode(RunConfig.ToolExecutionMode.PARALLEL)
          .streamingMode(RunConfig.StreamingMode.SSE)
          .build();
  private static final Logger LOG = LoggerFactory.getLogger(SessionRunner.class);

  private final String agentId;
  private final String sessionId;
  private final ActorRef<SessionCommand> sessionActor;
  private final Runner runner;
  private final String createdBy;

  public SessionRunner(
      final String agentId,
      final String sessionId,
      final ActorRef<SessionCommand> sessionActor,
      final Runner runner,
      final String createdBy) {
    this.agentId = agentId;
    this.sessionId = sessionId;
    this.sessionActor = sessionActor;
    this.runner = runner;
    this.createdBy = createdBy;
  }

  private volatile boolean stopRequested = false;

  /**
   * Starts {@code run} from its message. ADK does not emit the message it appends to the session,
   * so the stream opens with it, as {@code author}'s event kept with when it was sent.
   */
  public synchronized void start(final RunState run, final String author) {
    final UserMessage userMessage = run.message().getRecord().message();
    final Event userEvent =
        EventUtils.buildUserEvent(
            userMessage, run.runId(), run.messagePickedTimestamp(), author);
    EventUtils.addSentAt(userEvent, run.sentAt());
    startStream(userMessage, run.sentAt(), Flowable.just(userEvent));
  }

  /**
   * Continues a run whose history already holds its message; {@code continuedAt} is when it
   * continued. The model is told to continue, which is not kept.
   */
  public synchronized void continueRun(final ZonedDateTime continuedAt) {
    startStream(UserMessage.ofText("continue"), continuedAt, Flowable.empty());
  }

  private void startStream(
      final UserMessage userMessage, final ZonedDateTime sentAt, final Flowable<Event> leading) {
    LOG.debug("[USER_MESSAGE_TRACE][{}] SessionRunner.start() called", sessionId);
    stopRequested = false;

    final Content userContent =
        ContentUtils.buildUserContent(
            ContentUtils.textParts(userMessage.parts()), userMessage.attachments());

    // ADK appends its own user event to the session without emitting it, and the stream's leading
    // event is what is stored. The delta puts when it was sent on ADK's event, which is what
    // UserContextSessionService stamps the model's view from.
    final Map<String, Object> stateDelta = EventUtils.sentAtDelta(sentAt);

    leading
        .concatWith(
            runner
                .runAsync(createdBy, sessionId, userContent, RUN_CONFIG, stateDelta)
                .takeUntil(event -> stopRequested && event.turnComplete().orElse(false)))
        .doOnNext(
            event -> {
              LOG.debug(
                  "[USER_MESSAGE_TRACE][{}] ADK runAsync emitted event: author={} turnComplete={} finalResponse={} content={}",
                  sessionId,
                  event.author(),
                  event.turnComplete().orElse(false),
                  event.finalResponse(),
                  event.content().map(Content::text).orElse("<no-content>"));
            })
        .doOnError(
            error ->
                LOG.error("[USER_MESSAGE_TRACE][{}] ADK runAsync stream error", sessionId, error))
        .subscribe(
            event -> {
              LOG.debug(
                  "[USER_MESSAGE_TRACE][{}] Sending PublishEventCommand to SessionActor",
                  sessionId);
              sessionActor.tell(new PublishEventCommand(event));
            },
            error ->
                sessionActor.tell(new CompleteRunCommand(ExceptionUtils.getErrorSummary(error))),
            () -> {
              LOG.debug("[USER_MESSAGE_TRACE][{}] ADK runAsync stream completed", sessionId);
              sessionActor.tell(new CompleteRunCommand());
            });
  }

  public synchronized void resume(final Collection<ResumeRequest> resumeRequests) {
    stopRequested = false;
    runner
        .runAsync(createdBy, sessionId, ContentUtils.buildResumeContent(resumeRequests), RUN_CONFIG)
        .takeUntil(event -> stopRequested && event.turnComplete().orElse(false))
        .doOnNext(
            event ->
                LOG.debug(
                    "[{}] resume onNext: author={} turnComplete={} finalResponse={}",
                    sessionId,
                    event.author(),
                    event.turnComplete().orElse(false),
                    event.finalResponse()))
        .doOnError(error -> LOG.error("[{}] resume onError", sessionId, error))
        .subscribe(
            event -> sessionActor.tell(new PublishEventCommand(event)),
            error ->
                sessionActor.tell(new CompleteRunCommand(ExceptionUtils.getErrorSummary(error))),
            () -> {
              LOG.debug("[{}] resume onComplete", sessionId);
              sessionActor.tell(new CompleteRunCommand());
            });
  }

  public synchronized void stop() {
    stopRequested = true;
  }

  public String getAgentId() {
    return agentId;
  }

  public String getSessionId() {
    return sessionId;
  }
}
