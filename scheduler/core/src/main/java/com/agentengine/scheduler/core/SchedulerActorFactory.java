package com.agentengine.scheduler.core;

import com.agentengine.scheduler.api.store.JobDefinitionRepository;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.actor.WorkerActorFactory;
import com.agentengine.scheduler.core.actor.SchedulerActor;
import com.agentengine.scheduler.core.actor.TriggerReconcilerActor;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.pekko.ActorSystemProvider;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
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
  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final JobDefinitionRepository jobDefinitionRepository;
  private final ActorSystemProvider actorSystemProvider;
  private final WorkerActorFactory workerActorFactory;
  private final ApplicationConfig applicationConfig;
  private ActorRef<SchedulerActor.Command> schedulerRef;

  @Inject
  public SchedulerActorFactory(
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final JobDefinitionRepository jobDefinitionRepository,
      final ActorSystemProvider actorSystemProvider,
      final WorkerActorFactory workerActorFactory,
      final ApplicationConfig applicationConfig) {
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.actorSystemProvider = actorSystemProvider;
    this.workerActorFactory = workerActorFactory;
    this.applicationConfig = applicationConfig;
  }

  public void onStart(
      @Observes @Priority(ActorSystemProvider.ACTOR_SYSTEM_STARTUP_PRIORITY + 1)
          final StartupEvent event) {
    if (!actorSystemProvider.isEnabled()) {
      LOG.info("Pekko is disabled; scheduler singleton will not start");
      return;
    }
    final SchedulerConfigs schedulerConfigs = new SchedulerConfigs(applicationConfig);
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

    schedulerRef = actorSystemProvider.singleton().init(singleton);
    workerActorFactory.start(schedulerRef);
    actorSystemProvider.singleton().init(reconcilerSingleton);
    LOG.info("Scheduler singleton {} initialized", SINGLETON_NAME);
  }

  public ActorRef<SchedulerActor.Command> getSchedulerRef() {
    return schedulerRef;
  }
}
