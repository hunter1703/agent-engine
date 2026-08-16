package com.agentengine.scheduler.core;

import com.agentengine.scheduler.api.store.JobDefinitionRepository;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.actor.ConcurrencyLimiter;
import com.agentengine.scheduler.core.actor.JobRunnerActorFactory;
import com.agentengine.scheduler.core.actor.SchedulerActor;
import com.agentengine.scheduler.core.actor.TriggerReconcilerActor;
import com.agentengine.scheduler.core.config.JobTagConfig;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import com.agentengine.util.pekko.ActorSystemProvider;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.typed.ClusterSingletonSettings;
import org.apache.pekko.cluster.typed.SingletonActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts the scheduler as a cluster singleton, so exactly one node scans for due triggers. */
@Singleton
@Unremovable
public class SchedulerActorFactory {

  private static final Logger LOG = LoggerFactory.getLogger(SchedulerActorFactory.class);
  private static final String SINGLETON_NAME = "SchedulerActor";
  private static final String RECONCILER_SINGLETON_NAME = "TriggerReconcilerActor";
  private static final String RESERVATION_TTL_KEY =
      "agent-engine.scheduler.dispatch-reservation-ttl-millis";
  private static final long DEFAULT_RESERVATION_TTL_MILLIS = Duration.ofMinutes(30).toMillis();

  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final JobDefinitionRepository jobDefinitionRepository;
  private final ActorSystemProvider actorSystemProvider;
  private final JobRunnerActorFactory jobRunnerActorFactory;
  private final InfraConfigService infraConfigService;
  private final ApplicationConfig applicationConfig;

  @Inject
  public SchedulerActorFactory(
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final JobDefinitionRepository jobDefinitionRepository,
      final ActorSystemProvider actorSystemProvider,
      final JobRunnerActorFactory jobRunnerActorFactory,
      final InfraConfigService infraConfigService,
      final ApplicationConfig applicationConfig) {
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.actorSystemProvider = actorSystemProvider;
    this.jobRunnerActorFactory = jobRunnerActorFactory;
    this.infraConfigService = infraConfigService;
    this.applicationConfig = applicationConfig;
  }

  public void onStart(
      @Observes @Priority(ActorSystemProvider.ACTOR_SYSTEM_STARTUP_PRIORITY + 1)
          final StartupEvent event) {
    if (!actorSystemProvider.isEnabled()) {
      LOG.info("Pekko is disabled; scheduler singleton will not start");
      return;
    }
    final ConcurrencyLimiter concurrencyLimiter =
        new ConcurrencyLimiter(
            tag -> {
              final JobTagConfig config =
                  infraConfigService.findById(JobTagConfig.CATEGORY, JobTagConfig.TYPE, tag);
              return (config == null || config.getMaxConcurrent() <= 0)
                  ? Integer.MAX_VALUE
                  : config.getMaxConcurrent();
            },
            applicationConfig.getLong(RESERVATION_TTL_KEY, DEFAULT_RESERVATION_TTL_MILLIS));

    final SchedulerConfigs schedulerConfigs = SchedulerConfigs.from(applicationConfig);
    final SingletonActor<SchedulerActor.Command> singleton =
        SingletonActor.of(
                Behaviors.<SchedulerActor.Command>setup(
                    context ->
                        Behaviors.withTimers(
                            timers ->
                                new SchedulerActor(
                                    context,
                                    timers,
                                    triggerDefinitionRepository,
                                    jobDefinitionRepository,
                                    jobRunnerActorFactory,
                                    concurrencyLimiter,
                                    schedulerConfigs))),
                SINGLETON_NAME)
            .withSettings(ClusterSingletonSettings.create(actorSystemProvider.system()));

    final long heartbeatTimeoutMs =
        schedulerConfigs.heartbeatInterval().toMillis() * schedulerConfigs.allowedHeartbeatMisses();
    final SingletonActor<TriggerReconcilerActor.Command> reconcilerSingleton =
        SingletonActor.of(
                TriggerReconcilerActor.create(triggerDefinitionRepository, heartbeatTimeoutMs),
                RECONCILER_SINGLETON_NAME)
            .withSettings(ClusterSingletonSettings.create(actorSystemProvider.system()));

    actorSystemProvider.singleton().init(singleton);
    actorSystemProvider.singleton().init(reconcilerSingleton);
    LOG.info("Scheduler singleton {} initialized", SINGLETON_NAME);
  }
}
