package com.agentengine.agent.core.session;

import com.agentengine.agent.api.model.ResourceGrants;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.commands.SelfCommand.CompleteRunCommand;
import com.agentengine.agent.core.session.commands.SelfCommand.PublishEventCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.utils.ContentUtils;
import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.ExceptionUtils;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.Collection;
import org.apache.pekko.actor.typed.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionRunner {
  private static final Logger LOG = LoggerFactory.getLogger(SessionRunner.class);

  private final String sessionId;
  private final ActorRef<SessionCommand> sessionActor;
  private final Agent agent;
  private final Runner runner;
  private Disposable disposable;

  public SessionRunner(
      final String sessionId,
      final ActorRef<SessionCommand> sessionActor,
      final Agent agent,
      final Runner runner) {
    this.sessionId = sessionId;
    this.sessionActor = sessionActor;
    this.agent = agent;
    this.runner = runner;
  }

  public synchronized void start(final UserMessage userMessage, final ResourceGrants grants) {
    LOG.debug("[USER_MESSAGE_TRACE][{}] SessionRunner.start() called", sessionId);

    final Content userContent =
        ContentUtils.buildUserContent(ContentUtils.textParts(userMessage.parts()));

    disposable =
        runner
            .runAsync(AgentSession.DEFAULT_USER_ID, sessionId, userContent, runConfig(grants, true))
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
                    LOG.error(
                        "[USER_MESSAGE_TRACE][{}] ADK runAsync stream error", sessionId, error))
            .subscribe(
                event -> {
                  LOG.debug(
                      "[USER_MESSAGE_TRACE][{}] Sending PublishEventCommand to SessionActor",
                      sessionId);
                  sessionActor.tell(new PublishEventCommand(event));
                },
                error ->
                    sessionActor.tell(
                        new CompleteRunCommand(ExceptionUtils.getFullStackTrace(error))),
                () -> {
                  LOG.debug("[USER_MESSAGE_TRACE][{}] ADK runAsync stream completed", sessionId);
                  sessionActor.tell(new CompleteRunCommand());
                });
  }

  public synchronized void resume(
      final Collection<ResumeRequest> resumeRequests, final ResourceGrants grants) {
    disposable =
        runner
            .runAsync(
                AgentSession.DEFAULT_USER_ID,
                sessionId,
                ContentUtils.buildResumeContent(resumeRequests),
                runConfig(grants, false))
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

  private static RunConfig runConfig(final ResourceGrants grants, final boolean newRun) {
    final RunConfig base =
        RunConfig.builder()
            .toolExecutionMode(RunConfig.ToolExecutionMode.PARALLEL)
            .streamingMode(RunConfig.StreamingMode.SSE)
            .build();
    return grants.isEmpty() ? base : new ExtendedRunConfig(base, grants, newRun);
  }
}
