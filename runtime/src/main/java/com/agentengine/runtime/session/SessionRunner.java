package com.agentengine.runtime.session;

import com.agentengine.runtime.session.commands.SelfCommand.CompleteRunCommand;
import com.agentengine.runtime.session.commands.SelfCommand.PublishEventCommand;
import com.agentengine.runtime.session.commands.SelfCommand.RunFailedCommand;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.utils.ContentUtils;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.ExceptionUtils;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.Runner;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Collection;
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                RUN_EXECUTOR.shutdownNow();
            } catch (final Exception exception) {
                // best-effort shutdown
            }
        }));
    }

    private final String sessionId;
    private final ActorRef<SessionCommand> sessionActor;
    private final Runner runner;
    private Disposable disposable;

    public SessionRunner(final String sessionId, final ActorRef<SessionCommand> sessionActor, final Runner runner) {
        this.sessionId = sessionId;
        this.sessionActor = sessionActor;
        this.runner = runner;
    }

    public synchronized void start(final String message) {
        cancel();
        disposable = runner.runAsync(
                        AgentSession.DEFAULT_USER_ID, sessionId, ContentUtils.buildUserContent(message), runConfig())
                .subscribeOn(SCHEDULER)
                .doOnNext(event -> LOG.debug(
                        "[{}] runAsync onNext: author={} turnComplete={} finalResponse={}",
                        sessionId,
                        event.author(),
                        event.turnComplete().orElse(false),
                        event.finalResponse()))
                .doOnComplete(() -> {
                    LOG.debug("[{}] runAsync onComplete", sessionId);
                    sessionActor.tell(new CompleteRunCommand());
                })
                .doOnError(error -> LOG.error("[{}] runAsync onError", sessionId, error))
                .subscribe(
                        event -> sessionActor.tell(new PublishEventCommand(event)),
                        error -> sessionActor.tell(new RunFailedCommand(ExceptionUtils.getErrorMessage(error))));
    }

    public synchronized void resume(final Collection<Confirmation> confirmations) {
        cancel();
        disposable = runner.runAsync(
                        AgentSession.DEFAULT_USER_ID,
                        sessionId,
                        ContentUtils.buildConfirmationsContent(confirmations),
                        runConfig())
                .subscribeOn(SCHEDULER)
                .doOnNext(event -> LOG.debug(
                        "[{}] resume onNext: author={} turnComplete={} finalResponse={}",
                        sessionId,
                        event.author(),
                        event.turnComplete().orElse(false),
                        event.finalResponse()))
                .doOnComplete(() -> {
                    LOG.debug("[{}] resume onComplete", sessionId);
                    sessionActor.tell(new CompleteRunCommand());
                })
                .doOnError(error -> LOG.error("[{}] resume onError", sessionId, error))
                .subscribe(
                        event -> sessionActor.tell(new PublishEventCommand(event)),
                        error -> sessionActor.tell(new RunFailedCommand(ExceptionUtils.getErrorMessage(error))));
    }

    public synchronized void cancel() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        disposable = null;
    }

    private static RunConfig runConfig() {
        return RunConfig.builder()
                .setToolExecutionMode(RunConfig.ToolExecutionMode.PARALLEL)
                .setStreamingMode(RunConfig.StreamingMode.SSE)
                .build();
    }
}
