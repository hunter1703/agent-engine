package com.agentengine.scheduler.core.actor;

import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.SchedulerConfigs;
import com.agentengine.util.common.ThreadUtils;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.context.ContextualExecutor;
import com.agentengine.util.pekko.ActorSystemProvider;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.japi.function.Function;

/** Starts the pod's single {@link WorkerActor}. */
@Singleton
@Unremovable
public class WorkerActorFactory {

  private static final Duration SPAWN_TIMEOUT = Duration.ofSeconds(10);
  private static final String WORKER_NAME = "job-worker";

  private final ActorSystemProvider actorSystemProvider;
  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final SchedulerConfigs schedulerConfigs;
  private final ExecutorService jobExecutor;

  @Inject
  public WorkerActorFactory(
      final ActorSystemProvider actorSystemProvider,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final ApplicationConfig applicationConfig) {
    this.actorSystemProvider = actorSystemProvider;
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.schedulerConfigs = new SchedulerConfigs(applicationConfig);
    this.jobExecutor =
        new ContextualExecutor(
            ThreadUtils.newFixedThreadExecutor(
                "job-runner-", schedulerConfigs.maxConcurrentJobs()));
    ;
  }

  public void start(final ActorRef<SchedulerActor.Command> scheduler) {
    AskPattern.ask(
            actorSystemProvider.system(),
            (Function<ActorRef<ActorRef<WorkerActor.Command>>, SpawnProtocol.Command>)
                replyTo ->
                    new SpawnProtocol.Spawn<>(
                        Behaviors.supervise(
                                WorkerActor.create(
                                    scheduler,
                                    triggerDefinitionRepository,
                                    jobExecutor,
                                    schedulerConfigs))
                            .onFailure(SupervisorStrategy.restart()),
                        WORKER_NAME,
                        Props.empty(),
                        replyTo),
            SPAWN_TIMEOUT,
            actorSystemProvider.system().scheduler())
        .toCompletableFuture()
        .join();
  }
}
