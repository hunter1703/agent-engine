package com.agentengine.agent.core.session;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.commands.SelfCommand.CompleteRunCommand;
import com.agentengine.agent.core.session.commands.SelfCommand.PublishEventCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.artifacts.CloudStorageArtifactService;
import com.agentengine.agent.infra.utils.ContentUtils;
import com.agentengine.knowledge.api.beans.IndexRequest;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.FileUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.pekko.actor.typed.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Session-scoped adapter around ADK {@link Runner}. */
public final class SessionRunner {
  private static final Logger LOG = LoggerFactory.getLogger(SessionRunner.class);
  private static final ExecutorService RUN_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  private static final Scheduler SCHEDULER = Schedulers.from(RUN_EXECUTOR);

  static {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    RUN_EXECUTOR.shutdownNow();
                  } catch (final Exception exception) {
                    // best-effort shutdown
                  }
                }));
  }

  private final String sessionId;
  private final ActorRef<SessionCommand> sessionActor;
  private final Agent agent;
  private final Runner runner;
  private Disposable disposable;
  private final CloudStorageService cloudStorageService;
  private final KnowledgeService knowledgeService;

  public SessionRunner(
      final String sessionId,
      final ActorRef<SessionCommand> sessionActor,
      final Agent agent,
      final Runner runner,
      final CloudStorageService cloudStorageService,
      final KnowledgeService knowledgeService) {
    this.sessionId = sessionId;
    this.sessionActor = sessionActor;
    this.agent = agent;
    this.runner = runner;
    this.cloudStorageService = cloudStorageService;
    this.knowledgeService = knowledgeService;
  }

  public synchronized void start(final UserMessage userMessage) {
    LOG.info("[USER_MESSAGE_TRACE][{}] SessionRunner.start() called", sessionId);
    cancel();

    final Map<String, String> indexedFiles = new LinkedHashMap<>();
    userMessage.parts().stream()
        .filter(part -> part instanceof MessagePart.FilePart)
        .forEach(
            part -> {
              final MessagePart.FilePart filePart = (MessagePart.FilePart) part;
              final FileDetails fileDetails = filePart.fileDetails();
              final String source = fileDetails.source();
              final int slashIndex = source.indexOf("/");

              if (FileUtils.isTextFile(fileDetails)) {
                try {
                  final IndexRequest request = new IndexRequest();
                  request.setAgentId(runner.appName());
                  request.setFileDetails(fileDetails);
                  request.setTitle(fileDetails.name());
                  request.setGrants(List.of("S/" + sessionId));
                  request.setWaitForCompletion(true);
                  final Knowledge knowledge = knowledgeService.create(request);
                  indexedFiles.put(fileDetails.name(), knowledge.getId());
                  LOG.info(
                      "[{}] Indexed file '{}' as knowledge {}",
                      sessionId,
                      fileDetails.name(),
                      knowledge.getId());
                } catch (Exception e) {
                  LOG.warn(
                      "[{}] Failed to index file '{}' as knowledge: {}",
                      sessionId,
                      fileDetails.name(),
                      e.getMessage());
                }
              } else {
                cloudStorageService.copy(
                    source.substring(slashIndex + 1),
                    CloudStorageArtifactService.objectKey(
                        runner.appName(),
                        AgentSession.DEFAULT_USER_ID,
                        sessionId,
                        fileDetails.name(),
                        0));
              }
            });

    final List<MessagePart> updatedParts =
        userMessage.parts().stream()
            .filter(
                part ->
                    !(part instanceof MessagePart.FilePart(FileDetails fileDetails))
                        || !FileUtils.isTextFile(fileDetails))
            .toList();
    final Content userContent = ContentUtils.buildUserContent(new UserMessage(updatedParts));
    final Content contentToRun =
        indexedFiles.isEmpty() ? userContent : appendKnowledgeHint(userContent, indexedFiles);

    disposable =
        runner
            .runAsync(AgentSession.DEFAULT_USER_ID, sessionId, contentToRun, runConfig())
            .subscribeOn(SCHEDULER)
            .doOnNext(
                event -> {
                  LOG.info(
                      "[USER_MESSAGE_TRACE][{}] ADK runAsync emitted event: author={} turnComplete={} finalResponse={} content={}",
                      sessionId,
                      event.author(),
                      event.turnComplete().orElse(false),
                      event.finalResponse(),
                      event.content().map(Content::text).orElse("<no-content>"));
                  LOG.debug(
                      "[{}] runAsync onNext: author={} turnComplete={} finalResponse={}",
                      sessionId,
                      event.author(),
                      event.turnComplete().orElse(false),
                      event.finalResponse());
                })
            .doOnError(
                error -> {
                  LOG.error("[USER_MESSAGE_TRACE][{}] ADK runAsync stream error", sessionId, error);
                  LOG.error("[{}] runAsync onError", sessionId, error);
                })
            .subscribe(
                event -> {
                  LOG.info(
                      "[USER_MESSAGE_TRACE][{}] Sending PublishEventCommand to SessionActor",
                      sessionId);
                  sessionActor.tell(new PublishEventCommand(event));
                },
                error ->
                    sessionActor.tell(
                        new CompleteRunCommand(ExceptionUtils.getFullStackTrace(error))),
                () -> {
                  LOG.info("[USER_MESSAGE_TRACE][{}] ADK runAsync stream completed", sessionId);
                  LOG.debug("[{}] runAsync onComplete", sessionId);
                  sessionActor.tell(new CompleteRunCommand());
                });
  }

  public synchronized void resume(final Collection<ResumeRequest> resumeRequests) {
    cancel();
    disposable =
        runner
            .runAsync(
                AgentSession.DEFAULT_USER_ID,
                sessionId,
                ContentUtils.buildResumeContent(resumeRequests),
                runConfig())
            .subscribeOn(SCHEDULER)
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
                    sessionActor.tell(
                        new CompleteRunCommand(ExceptionUtils.getFullStackTrace(error))),
                () -> {
                  LOG.debug("[{}] resume onComplete", sessionId);
                  sessionActor.tell(new CompleteRunCommand());
                });
  }

  public synchronized void cancel() {
    if (disposable != null && !disposable.isDisposed()) {
      disposable.dispose();
    }
    disposable = null;
  }

  public synchronized void close() {
    cancel();
    agent.close().blockingAwait();
  }

  private static RunConfig runConfig() {
    return RunConfig.builder()
        .setToolExecutionMode(RunConfig.ToolExecutionMode.PARALLEL)
        .setStreamingMode(RunConfig.StreamingMode.SSE)
        .build();
  }

  private static Content appendKnowledgeHint(
      final Content base, final Map<String, String> indexedFiles) {
    final StringBuilder hint =
        new StringBuilder(
            """


                The following uploaded files have been indexed as knowledge. \
                Use the search_knowledge tool to search through their contents.
                """);
    indexedFiles.forEach(
        (name, knowledgeId) ->
            hint.append("- ")
                .append(name)
                .append(" → knowledgeId: ")
                .append(knowledgeId)
                .append('\n'));

    final List<Part> parts = base.parts().orElse(List.of());
    final List<Part> newParts = new ArrayList<>(parts);
    if (newParts.isEmpty()) {
      newParts.add(Part.fromText(hint.toString()));
    } else {
      final Part first = newParts.getFirst();
      newParts.set(0, Part.fromText(first.text().orElse("") + hint));
    }
    return base.toBuilder().parts(newParts).build();
  }
}
