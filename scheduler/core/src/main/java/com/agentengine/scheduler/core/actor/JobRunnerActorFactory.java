package com.agentengine.scheduler.core.actor;

import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.SchedulerConfigs;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.actor.AutoPassivableShardedEntityFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;

/** Registers {@link JobRunnerActor} for sharding and hands out refs to individual runners. */
@Singleton
@Unremovable
public class JobRunnerActorFactory
    extends AutoPassivableShardedEntityFactory<JobRunnerActor.Command> {

  /**
   * A safety net only: an entity stops itself once its run finishes, so one still alive after this
   * long is one whose work never completed.
   */
  private static final Duration PASSIVATION_TIMEOUT = Duration.ofMinutes(5);

  /** Only scheduler nodes carry the job classes these entities load. */
  private static final String SCHEDULER_ROLE = "scheduler";

  private static final String MAX_CONCURRENT_JOBS_KEY =
      "agent-engine.scheduler.max-concurrent-jobs";
  private static final int DEFAULT_MAX_CONCURRENT_JOBS = 20;

  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final ConcurrencyLimiter nodeLimiter;
  private final ExecutorService jobExecutor;
  private final Duration heartbeatInterval;

  @Inject
  public JobRunnerActorFactory(
      final ActorSystemProvider actorSystemProvider,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final ApplicationConfig applicationConfig) {
    super(actorSystemProvider, JobRunnerActor.TYPE_KEY, PASSIVATION_TIMEOUT, SCHEDULER_ROLE);
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    // One limiter for the factory, and the factory is a singleton, so every runner on this node
    // draws from the same pool. It caps total concurrent jobs on the pod — what protects its memory
    // and connections — rather than dividing capacity per job class, which is the cluster-wide
    // quota's job. Permits are returned when an entity stops, so none of them expire.
    final int maxConcurrent =
        applicationConfig.getInt(MAX_CONCURRENT_JOBS_KEY, DEFAULT_MAX_CONCURRENT_JOBS);
    this.nodeLimiter = new ConcurrencyLimiter(_ -> maxConcurrent, Long.MAX_VALUE);
    this.jobExecutor = Executors.newFixedThreadPool(maxConcurrent);
    this.heartbeatInterval = SchedulerConfigs.from(applicationConfig).heartbeatInterval();
  }

  @Override
  protected Behavior<JobRunnerActor.Command> behavior(
      final EntityContext<JobRunnerActor.Command> context) {
    return Behaviors.setup(
        actorContext ->
            Behaviors.withTimers(
                timers ->
                    new JobRunnerActor(
                        actorContext,
                        timers,
                        context.getEntityId(),
                        triggerDefinitionRepository,
                        nodeLimiter,
                        jobExecutor,
                        heartbeatInterval)));
  }
}
