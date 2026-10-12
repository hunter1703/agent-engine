package com.agentengine.agent.core.session;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.commands.SelfCommand.CompleteRunCommand;
import com.agentengine.agent.core.session.commands.SelfCommand.PublishEventCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.state.RunState;
import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.RequestContentUtils;
import com.agentengine.knowledge.api.beans.IndexRequest;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.ExceptionUtils;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.utils.StructuredConcurrencyUtils;
import com.agentengine.util.common.utils.StructuredConcurrencyUtils.TaskOutcome;
import com.agentengine.util.context.Context;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope.Subtask;
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

  /**
   * Attachments at or below this size are granted as a raw knowledge file (read whole, on demand).
   * Larger text attachments are indexed instead, since a large file is both wasteful to hand the
   * model in full and a better fit for semantic search.
   */
  private static final long INDEXING_THRESHOLD_BYTES = 5 * 1024;

  private final String agentId;
  private final String sessionId;
  private final ActorRef<SessionCommand> sessionActor;
  private final Runner runner;
  private final String createdBy;
  private final KnowledgeService knowledgeService;
  private final FileService fileService;

  public SessionRunner(
      final String agentId,
      final String sessionId,
      final ActorRef<SessionCommand> sessionActor,
      final Runner runner,
      final String createdBy,
      final KnowledgeService knowledgeService,
      final FileService fileService) {
    this.agentId = agentId;
    this.sessionId = sessionId;
    this.sessionActor = sessionActor;
    this.runner = runner;
    this.createdBy = createdBy;
    this.knowledgeService = knowledgeService;
    this.fileService = fileService;
  }

  private volatile boolean stopRequested = false;

  /**
   * Starts {@code run} from its message. ADK does not emit the message it appends to the session,
   * so the stream opens with it, as {@code author}'s event kept with when it was sent.
   */
  public synchronized void start(final RunState run, final String author) {
    final UserMessage rawMessage = run.message().getRecord().message();
    final ResolvedMessage resolved = resolveAttachments(rawMessage);
    final Map<String, Object> stateDelta =
        EventUtils.userStateDelta(run.sentAt(), resolved.userMessage().attachments());
    final Event userEvent =
        EventUtils.buildUserEvent(
            resolved.userMessage(), run.runId(), run.messagePickedTimestamp(), author, stateDelta);
    startStream(resolved.userContent(), Flowable.just(userEvent), stateDelta);
  }

  /**
   * Continues a run whose history already holds its message; {@code continuedAt} is when it
   * continued. The model is told to continue, which is not kept.
   */
  public synchronized void continueRun(final ZonedDateTime continuedAt) {
    startStream(
        RequestContentUtils.buildUserContent(
            RequestContentUtils.textParts(UserMessage.ofText("continue").parts()), null, null),
        Flowable.empty(),
        EventUtils.sentAtDelta(continuedAt));
  }

  private void startStream(
      final Content userContent,
      final Flowable<Event> leading,
      final Map<String, Object> stateDelta) {
    // ADK appends its own user event to the session without emitting it, and the stream's leading
    // event is what is stored. The delta puts when it was sent on ADK's event, which is what
    // UserContextSessionService stamps the model's view from.
    publish(leading, runner.runAsync(createdBy, sessionId, userContent, RUN_CONFIG, stateDelta));
  }

  /**
   * Continues the run of {@code runId} with {@code resumeRequests}. As with a message, ADK does not
   * emit what it appends, so the stream opens with one event per resume, in the order they arrived,
   * each attributed to who gave it: a child's result to the child, a user's answer to the user, and
   * {@code defaultAuthor} when none is named.
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
            createdBy,
            sessionId,
            RequestContentUtils.buildResumeContent(resumeRequests),
            RUN_CONFIG));
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
                    event.content().orElse(null)))
        .subscribe(
            event -> sessionActor.tell(new PublishEventCommand(event)),
            error -> {
              LOG.error("[USER_MESSAGE_TRACE][{}] SessionRunner stream failed", sessionId, error);
              sessionActor.tell(new CompleteRunCommand(ExceptionUtils.getErrorSummary(error)));
            },
            () -> {
              LOG.debug("[USER_MESSAGE_TRACE][{}] SessionRunner stream completed", sessionId);
              sessionActor.tell(new CompleteRunCommand());
            });
  }

  public void stop() {
    stopRequested = true;
  }

  public String getAgentId() {
    return agentId;
  }

  public String getSessionId() {
    return sessionId;
  }

  private ResolvedMessage resolveAttachments(final UserMessage message) {
    final List<AgentFileDetails> attachments = message.attachments();
    if (CollectionUtils.isEmpty(attachments)) {
      final Content userContent =
          RequestContentUtils.buildUserContent(
              RequestContentUtils.textParts(message.parts()), null, null);
      return new ResolvedMessage(message, userContent);
    }

    return Context.require()
        .actingAs(AgentSession.principal(agentId, sessionId))
        .get(
            () -> {
              final List<AgentFileDetails> toIndex = new ArrayList<>();
              final List<AgentFileDetails> resolved = new ArrayList<>();
              for (final AgentFileDetails fileDetails : attachments) {
                if (StringUtils.isNotBlank(fileDetails.knowledgeId())) {
                  resolved.add(fileDetails);
                } else if (needsIndexing(fileDetails)) {
                  toIndex.add(fileDetails);
                } else {
                  resolved.add(createUnindexedKnowledge(fileDetails));
                }
              }

              if (!toIndex.isEmpty()) {
                final List<Callable<String>> indexing =
                    toIndex.stream()
                        .<Callable<String>>map(
                            fileDetails -> () -> indexAsKnowledge(fileDetails).getId())
                        .toList();
                final List<TaskOutcome<String>> outcomes =
                    StructuredConcurrencyUtils.callConcurrentlyUntil(
                        "knowledge-indexing", indexing, _ -> false);

                for (final TaskOutcome<String> outcome : outcomes) {
                  if (outcome.state() == Subtask.State.SUCCESS) {
                    resolved.add(toIndex.get(outcome.index()).withKnowledgeId(outcome.value()));
                  } else {
                    LOG.warn(
                        "Indexing failed for {}; dropping it from the message's attachments",
                        toIndex.get(outcome.index()).source(),
                        outcome.error());
                  }
                }
              }

              final UserMessage resolvedMessage = new UserMessage(message.parts(), resolved);
              final Content userContent =
                  RequestContentUtils.buildUserContent(
                      RequestContentUtils.textParts(resolvedMessage.parts()),
                      resolvedMessage.attachments(),
                      fileService);
              return new ResolvedMessage(resolvedMessage, userContent);
            });
  }

  private AgentFileDetails createUnindexedKnowledge(final AgentFileDetails fileDetails) {
    final Knowledge knowledge = createKnowledge(fileDetails, true);
    return fileDetails.withKnowledgeId(knowledge.getId());
  }

  private boolean needsIndexing(final AgentFileDetails agentFileDetails) {
    final FileDetails fileDetails = agentFileDetails.toFileDetails();
    if (!FileUtils.isTextFile(fileDetails)
        && !FileUtils.isOfficeFile(fileDetails)
        && !FileUtils.isPdfFile(fileDetails)
        && !FileUtils.isImageFile(fileDetails)) {
      return false;
    }
    final long size = fileService.getSize(fileDetails);
    return size > INDEXING_THRESHOLD_BYTES;
  }

  private Knowledge indexAsKnowledge(final AgentFileDetails fileDetails) {
    return createKnowledge(fileDetails, false);
  }

  private Knowledge createKnowledge(
      final AgentFileDetails fileDetails, final boolean skipIndexing) {
    final IndexRequest request = new IndexRequest();
    request.setFileDetails(fileDetails.toFileDetails());
    request.setTitle(fileDetails.name());
    request.setSkipIndexing(skipIndexing);
    request.setWaitForCompletion(true);
    return knowledgeService.index(request);
  }

  private record ResolvedMessage(UserMessage userMessage, Content userContent) {}
}
