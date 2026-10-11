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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    final Content userContent =
        ContentUtils.buildUserContent(
            ContentUtils.textParts(userMessage.parts()), userMessage.attachments(), null);

    // ADK appends its own user event to the session without emitting it, and the stream's leading
    // event is what is stored. The delta puts when it was sent on ADK's event, which is what
    // UserContextSessionService stamps the model's view from.
    final Map<String, Object> stateDelta = EventUtils.sentAtDelta(sentAt);

    publish(
        leading, runner.runAsync(createdBy, sessionId, userContent, RUN_CONFIG, stateDelta));
  }

  /**
   * Continues the run of {@code runId} with {@code resumeRequests}. As with a message, ADK does not
   * emit what it appends, so the stream opens with one event per resume, in the order they
   * arrived, each attributed to who gave it: a child's result to the child, a user's answer to the
   * user, and {@code defaultAuthor} when none is named.
   */
  public synchronized void resume(
      final String runId,
      final String defaultAuthor,
      final Collection<ResumeRequest> resumeRequests) {
    final List<Event> resumeEvents =
        resumeRequests.stream()
            .sorted(Comparator.comparingLong(ResumeRequest::getTimestamp))
            .map(
                resumeRequest ->
                    EventUtils.buildResumeEvent(
                        List.of(resumeRequest),
                        runId,
                        Objects.requireNonNullElse(resumeRequest.getAuthor(), defaultAuthor)))
            .toList();
    publish(
        Flowable.fromIterable(resumeEvents),
        runner.runAsync(
            createdBy, sessionId, ContentUtils.buildResumeContent(resumeRequests), RUN_CONFIG));
  }

  private void publish(final Flowable<Event> leading, final Flowable<Event> runnerEvents) {
    LOG.debug("[USER_MESSAGE_TRACE][{}] SessionRunner starting a stream", sessionId);
    stopRequested = false;

    leading
        .concatWith(
            runnerEvents.takeUntil(event -> stopRequested && event.turnComplete().orElse(false)))
        .doOnNext(
            event ->
                LOG.debug(
                    "[USER_MESSAGE_TRACE][{}] emitted event: author={} turnComplete={} finalResponse={} content={}",
                    sessionId,
                    event.author(),
                    event.turnComplete().orElse(false),
                    event.finalResponse(),
                    event.content().map(Content::text).orElse("<no-content>")))
        .doOnError(error -> LOG.error("[{}] stream error", sessionId, error))
        .subscribe(
            event -> sessionActor.tell(new PublishEventCommand(event)),
            error ->
                sessionActor.tell(new CompleteRunCommand(ExceptionUtils.getErrorSummary(error))),
            () -> {
              LOG.debug("[{}] stream completed", sessionId);
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
